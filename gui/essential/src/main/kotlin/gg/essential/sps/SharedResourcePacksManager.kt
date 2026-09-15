/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.sps

import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.util.Client
import gg.essential.util.Sha256
import gg.essential.util.httpCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.lang.AutoCloseable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.moveTo
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.milliseconds

typealias PackWithPath = Pair<Sha256, Deferred<Path>>
typealias PacksWithPaths = List<PackWithPath>

abstract class SharedResourcePacksManager(val worldsManager: WorldsManager) : AutoCloseable {
    private val refHolder = ReferenceHolderImpl()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Client)

    val resourcePackServer = ResourcePackServer()

    val serverPacks = mutableStateOf<List<Sha256>>(emptyList())

    protected val acceptedPacks = mutableStateOf<PacksWithPaths>(emptyList())
    protected val currentPrompt = mutableStateOf<Pair<PacksWithPaths, Boolean?>?>(null)

    init {
        val suggestedPacks = memo {
            val hostingWorld = worldsManager.integratedServerWorld()
            when {
                hostingWorld != null -> hostingWorld.resourcePackFiles()?.map { it.first to it.third }
                else -> serverPacks().map { sha256 ->
                    val cachePath = resourcePackCachePath(sha256)
                    sha256 to scope.async(Dispatchers.IO, CoroutineStart.LAZY) {
                        if (!cachePath.exists()) {
                            downloadPackP2P(cachePath, sha256, mutableStateOf(0f))
                        }
                        cachePath
                    }
                }
            }
        }
        val requirePrompt = memo {
            val hostingWorld = worldsManager.integratedServerWorld()
            when {
                hostingWorld != null -> false
                else -> true
            }
        }

        effect(refHolder) {
            val packs = suggestedPacks() ?: return@effect // null means it's still loading, we'll try again later
            if (!requirePrompt()) {
                currentPrompt.set(null)
                acceptedPacks.set(packs)
            } else {
                val (promptPacks, promptStatus) = currentPrompt() ?: Pair(emptyList(), null)
                if (packs.map { it.first } == acceptedPacks().map { it.first }) {
                    currentPrompt.set(Pair(packs, true))
                } else if (packs != promptPacks) {
                    currentPrompt.set(Pair(packs, null))
                } else if (promptStatus == true) {
                    acceptedPacks.set(packs)
                }
            }
        }

        var downloadJob: Job? = null // may be cancelled
        var applyJob: Job? = null // may not be cancelled
        var appliedPacks = emptyList<Sha256>()
        effect(refHolder) {
            val packFiles = acceptedPacks()

            downloadJob?.cancel()
            downloadJob = scope.launch {
                // TODO show progress?
                val files = packFiles.map { it.second.await() }

                val prevApplyJob = applyJob
                applyJob = scope.launch applyJob@{
                    prevApplyJob?.join()

                    // Check if we're already in the correct State.
                    // This is mostly to skip an unnecessary initial resource reload and cases where the Deferred
                    // changes but the sha256 does not.
                    val packs = packFiles.map { it.first }
                    if (packs == appliedPacks) {
                        return@applyJob
                    }
                    appliedPacks = packs

                    applySharedResourcePacks(files)
                }
            }
        }

        // Provide shared resource packs via http server
        effect(refHolder) {
            val world = worldsManager.integratedServerWorld()
            val packs = if (world?.shareResourcePacks() == true) world.resourcePackFiles() else null
            resourcePackServer.packs = (packs ?: emptyList()).associate { it.first to it.third }
        }
    }

    override fun close() {
        scope.cancel()
        resourcePackServer.close()
    }

    private suspend fun downloadPackP2P(path: Path, sha256: Sha256, progress: MutableState<Float>): Unit = coroutineScope {
        val progressChannel = Channel<Float>(Channel.CONFLATED)
        launch {
            for (value in progressChannel) {
                progress.set(value)
                delay(100.milliseconds)
            }
        }
        withContext(Dispatchers.IO) {
            httpCall(Request.Builder().url(getP2PUrl(sha256)).build()).use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response $response")
                val contentLength: FileSize = response.header("Content-Length")?.toLongOrNull()
                    ?: throw IOException("Missing or invalid Content-Length header")

                path.createParentDirectories()
                val tmpFile = Files.createTempFile(path.parent, "dl", ".zip")
                try {
                    tmpFile.outputStream().use { sink ->
                        val source = response.body.byteStream()
                        var totalBytesCopied: FileSize = 0
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val chunkSize = source.read(buffer)
                            if (chunkSize < 0) {
                                break
                            }
                            sink.write(buffer, 0, chunkSize)
                            totalBytesCopied += chunkSize
                            progressChannel.send(totalBytesCopied.toFloat() / contentLength)
                        }
                        progressChannel.send(1f)
                    }
                    val actualSha256 = tmpFile.inputStream().use { Sha256.compute(it) }
                    if (actualSha256 != sha256) {
                        throw IOException("Resource pack had unexpected checksum, expected $sha256, got $actualSha256")
                    }
                    tmpFile.moveTo(path, overwrite = true)
                    progressChannel.close()
                } finally {
                    tmpFile.deleteIfExists()
                }
            }
        }
    }


    protected abstract fun getP2PUrl(sha256: Sha256): String
    protected abstract suspend fun applySharedResourcePacks(packs: List<Path>)
}
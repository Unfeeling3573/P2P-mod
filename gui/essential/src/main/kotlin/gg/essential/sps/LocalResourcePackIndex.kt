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

import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.util.Client
import gg.essential.util.FilesIndexTracker
import gg.essential.util.Sha256
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.walk

abstract class LocalResourcePackIndex(essentialBaseDirectory: Path) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Client)
    private val mutableIsUpdating = mutableStateOf(false)
    private val mutex = Mutex()
    private val fileTrackerDirectory = essentialBaseDirectory.resolve("local-resource-pack-index-cache")
    private val resourcePackCacheDirectory = essentialBaseDirectory.resolve("local-resource-pack-cache")
    private val resourcePackTrackers = mutableMapOf<Path, Lazy<FilesIndexTracker>>()
    private val mutableResourcePacks = mutableStateOf(mapOf<Sha256, Pair<Path, ResourcePackName>>())

    protected val loadedResourcePacks = mutableStateOf(mapOf<Path, ResourcePackName>())

    val resourcePacks: State<Map<Sha256, Pair<Path, ResourcePackName>>> = mutableResourcePacks
    val isUpdating: State<Boolean> = mutableIsUpdating

    init {
        fileTrackerDirectory.createDirectories()
        resourcePackCacheDirectory.createDirectories()
    }

    protected abstract fun updatePaths(updateMinecraftPackRepo: Boolean)

    fun update(updateMinecraftPackRepo: Boolean = true) {
        coroutineScope.launch {
            mutex.withLock {
                try {
                    mutableIsUpdating.set(true)
                    updatePaths(updateMinecraftPackRepo)
                    mutableResourcePacks.set(emptyMap())
                    updateFilePacks()
                    updateFolderPacks()
                    deleteOldCacheFiles()
                    saveFileTrackers()
                } catch (e: Exception) {
                    LOGGER.error("Error while indexing resource packs", e)
                } finally {
                    mutableIsUpdating.set(false)
                }
            }
        }
    }

    private suspend fun deleteOldCacheFiles() {
        val loadedPacks = loadedResourcePacks.getUntracked()
        val resourcePacks = resourcePacks.getUntracked()
        withContext(Dispatchers.IO) {
            // Clean up indexes
            fileTrackerDirectory.forEachDirectoryEntry { entry ->
                if (entry.isRegularFile() && loadedPacks.none { "${it.key.name}.index" == entry.name }) {
                    entry.deleteExisting()
                }
            }
            // Clean up zipped resource packs
            resourcePackCacheDirectory.forEachDirectoryEntry { entry ->
                if (entry.isRegularFile() && resourcePacks.none { "${it.key.hexStr}.zip" == entry.name }) {
                    entry.deleteExisting()
                }
            }
        }
    }

    private fun getFileTracker(resourcePackPath: Path): Lazy<FilesIndexTracker> {
        return lazy {
            val trackerPath = fileTrackerDirectory.resolve("${resourcePackPath.name}.index")
            FilesIndexTracker.read(trackerPath, resourcePackPath)
        }
    }

    private suspend fun saveFileTrackers() {
        for ((resourcePackPath, tracker) in resourcePackTrackers) {
            withContext(Dispatchers.IO) {
                val trackerPath = fileTrackerDirectory.resolve("${resourcePackPath.name}.index")
                FilesIndexTracker.write(trackerPath, tracker.value)
            }
        }
    }

    private suspend fun updateFilePacks() {
        val packs = loadedResourcePacks.getUntracked()
        val filePacks = withContext(Dispatchers.IO) {
            packs.entries.filter { it.key.isRegularFile() }
        }
        for (entry in filePacks) {
            val resourcePackPath = entry.key
            val tracker = resourcePackTrackers.computeIfAbsent(resourcePackPath) { getFileTracker(resourcePackPath) }

            val sha256 = withContext(Dispatchers.IO) {
                getShaViaTracker(resourcePackPath, tracker.value)
            } ?: continue

            mutableResourcePacks.set { it + (sha256 to (resourcePackPath to entry.value)) }
        }
    }

    private suspend fun updateFolderPacks() {
        val packs = loadedResourcePacks.getUntracked()
        val directoryPacks = withContext(Dispatchers.IO) {
            packs.entries.filter { it.key.isDirectory() }
        }

        // Search through all folder packs and zip if they aren't registered to a zip file
        for (entry in directoryPacks) {
            val resourcePackPath = entry.key
            val tracker = resourcePackTrackers.computeIfAbsent(resourcePackPath) { getFileTracker(resourcePackPath) }
            val folderPackSha = withContext(Dispatchers.IO) {
                getShaViaTracker(entry.key, tracker.value)
            } ?: continue

            val cachedZip = withContext(Dispatchers.IO) {
                val path = resourcePackCachePath(folderPackSha)
                if (!path.exists()) {
                    zipFolderResourcePack(folderPackSha, resourcePackPath)
                }
                path
            }

            mutableResourcePacks.set { it + (folderPackSha to (cachedZip to entry.value)) }
        }

    }

    private fun getShaViaTracker(path: Path, tracker: FilesIndexTracker): Sha256? {
        tracker.update()
        return if (path.isDirectory()) {
            val digest = DigestUtils.getSha256Digest()
            for (shaEntry in tracker.getSha256Entries().entries.sortedBy { it.key }) {
                digest.update(shaEntry.value.first)
            }
            Sha256(digest.digest())
        } else {
            tracker.getSha256(path)?.let { Sha256(it) }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun zipFolderResourcePack(folderSha256: Sha256, pathToZip: Path) {
        val finalPath = resourcePackCachePath(folderSha256)
        val tmpPath = Files.createTempFile(finalPath.parent, "${folderSha256.hexStr}-", ".tmp")
        try {
            tmpPath.outputStream().use { pathOut ->
                ZipOutputStream(pathOut).use { zipOut ->
                    pathToZip.walk().forEach { path ->
                        val entryName = pathToZip.relativize(path).joinToString("/") + if (path.isDirectory()) "/" else ""
                        if (entryName.isEmpty() || entryName == "/") {
                            return@forEach
                        }

                        zipOut.putNextEntry(ZipEntry(entryName))

                        if (path.isRegularFile()) {
                            path.inputStream().use { input ->
                                input.copyTo(zipOut)
                            }
                        }
                        zipOut.closeEntry()
                    }
                }
            }

            tmpPath.moveTo(finalPath, overwrite = true)
        } finally {
            tmpPath.deleteIfExists()
        }
    }

    private fun resourcePackCachePath(sha256: Sha256): Path =
        resourcePackCacheDirectory / (sha256.hexStr + ".zip")

    companion object {
        protected val LOGGER: Logger = LoggerFactory.getLogger(LocalResourcePackIndex::class.java)
    }

}
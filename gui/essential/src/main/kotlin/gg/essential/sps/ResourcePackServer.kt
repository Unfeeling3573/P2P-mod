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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import gg.essential.quic.QuicUtil.LOCALHOST
import gg.essential.util.HttpStatus
import gg.essential.util.Sha256
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream

class ResourcePackServer : AutoCloseable {
    var packs: Map<Sha256, Deferred<Path>> = emptyMap()
        set(value) {
            field = value
            if (value.isNotEmpty() && !started && !closed) {
                started = true
                server.start()
            }
        }

    private var started = false
    private var closed = false

    private val server: HttpServer = HttpServer.create(InetSocketAddress(LOCALHOST, 0), 0).apply {
        createContext("/") { handle(it) }
        executor = Dispatchers.IO.asExecutor()
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val sha256 = Sha256.fromOrNull(exchange.requestURI.path.removePrefix("/"))
            if (sha256 == null) {
                exchange.sendResponseHeaders(HttpStatus.BAD_REQUEST, 0)
                return
            }
            val pack = packs[sha256]
            if (pack == null) {
                exchange.sendResponseHeaders(HttpStatus.NOT_FOUND, 0)
                return
            }
            val file = runBlocking { pack.await() }
            exchange.sendResponseHeaders(HttpStatus.OK, file.fileSize())
            file.inputStream().use { it.copyTo(exchange.responseBody) }
        } catch (e: Throwable) {
            e.printStackTrace() // HttpServer by default does not log anything
        } finally {
            exchange.responseBody.close()
        }
    }

    val port: Int
        get() = server.address.port

    override fun close() {
        if (closed) return
        closed = true

        if (started) {
            server.stop(0)
        }
    }
}

/*
 * Нютаг — локальный HTTP CONNECT прокси для WebView звонка поверх SOCKS кильватера.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import android.content.Context
import android.util.Base64
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.nyutag.NyutagWebProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultNyutagWebProxy(
    @ApplicationContext private val context: Context,
) : NyutagWebProxy {
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "nyutag-web-proxy").apply { isDaemon = true } }
    private var server: ServerSocket? = null
    private var endpoint: NyutagWebProxy.Endpoint? = null

    override suspend fun start(): NyutagWebProxy.Endpoint = withContext(Dispatchers.IO) {
        synchronized(this@DefaultNyutagWebProxy) {
            endpoint?.takeIf { server?.isClosed == false }?.let { return@withContext it }
            NyutagTunnel.proxyUrl(context) ?: throw IOException("Nyutag tunnel is not running")
            val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            val ep = NyutagWebProxy.Endpoint(ss.localPort, randomToken(), randomToken())
            server = ss
            endpoint = ep
            NyutagWebProxy.current = ep
            pool.execute { acceptLoop(ss, ep) }
            Timber.i("Nyutag web proxy listening on 127.0.0.1:${ss.localPort}")
            ep
        }
    }

    private fun acceptLoop(ss: ServerSocket, ep: NyutagWebProxy.Endpoint) {
        val expected = "Basic " + Base64.encodeToString("${ep.user}:${ep.password}".toByteArray(), Base64.NO_WRAP)
        while (!ss.isClosed) {
            val client = try {
                ss.accept()
            } catch (e: IOException) {
                if (!ss.isClosed) Timber.w(e, "Nyutag web proxy accept failed")
                break
            }
            pool.execute { handle(client, expected) }
        }
    }

    private fun handle(client: Socket, expectedAuth: String) {
        var upstream: Socket? = null
        try {
            client.soTimeout = HEADER_TIMEOUT_MS
            client.tcpNoDelay = true
            val head = readHead(client.getInputStream()) ?: return client.close()
            val lines = head.split("\r\n")
            val parts = lines.first().split(" ")
            if (parts.size < 2 || parts[0] != "CONNECT") return reply(client, "405 Method Not Allowed")
            val auth = lines.drop(1).firstOrNull { it.startsWith("proxy-authorization:", ignoreCase = true) }
                ?.substringAfter(':')?.trim()
            if (auth == null || !MessageDigest.isEqual(auth.toByteArray(), expectedAuth.toByteArray())) {
                return reply(client, "407 Proxy Authentication Required", "Proxy-Authenticate: Basic realm=\"${NyutagWebProxy.REALM}\"\r\n")
            }
            val target = parts[1]
            val host = target.substringBeforeLast(':').removePrefix("[").removeSuffix("]").lowercase()
            val port = target.substringAfterLast(':').toIntOrNull() ?: return reply(client, "400 Bad Request")
            if (!NyutagTunnel.webProxyAllows(host, port)) {
                Timber.w("Nyutag web proxy: refused CONNECT $host:$port")
                return reply(client, "403 Forbidden")
            }
            val started = System.currentTimeMillis()
            val proxy = URI(NyutagTunnel.proxyUrl(context) ?: return reply(client, "502 Bad Gateway"))
            val (user, pass) = (proxy.rawUserInfo ?: "").split(":", limit = 2).map { URLDecoder.decode(it, "UTF-8") } + listOf("", "")
            val up = Socket().also { upstream = it }
            up.connect(InetSocketAddress(proxy.host, proxy.port), CONNECT_TIMEOUT_MS)
            up.soTimeout = CONNECT_TIMEOUT_MS
            up.tcpNoDelay = true
            try {
                NyutagHttp.socks5Connect(up, user, pass, host, port)
            } catch (e: IOException) {
                Timber.w("Nyutag web proxy: tunnel CONNECT $host:$port failed: ${e.message}")
                return reply(client, "502 Bad Gateway")
            }
            up.soTimeout = 0
            client.soTimeout = 0
            client.getOutputStream().apply { write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray()); flush() }
            Timber.i("Nyutag web proxy: CONNECT $host:$port ok in ${System.currentTimeMillis() - started} ms")
            val upBytes = LongArray(1)
            val downward = pool.submit(Callable { pipe(up.getInputStream(), client.getOutputStream(), client) })
            pipe(client.getInputStream(), up.getOutputStream(), up, upBytes)
            val down = runCatching { downward.get() }.getOrDefault(0L)
            Timber.i("Nyutag web proxy: closed $host:$port up=${upBytes[0]} down=$down in ${(System.currentTimeMillis() - started) / 1000} s")
        } catch (e: IOException) {
            Timber.d("Nyutag web proxy: connection error: ${e.message}")
        } finally {
            runCatching { client.close() }
            runCatching { upstream?.close() }
        }
    }

    /** Копирует поток до EOF; по концу закрывает запись на другой стороне. Возвращает число байт. */
    private fun pipe(input: InputStream, output: OutputStream, sink: Socket, counter: LongArray? = null): Long {
        val buf = ByteArray(16 * 1024)
        var total = 0L
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
                output.flush()
                total += n
                counter?.set(0, total)
            }
        } catch (_: IOException) {
        } finally {
            runCatching { sink.shutdownOutput() }
        }
        return total
    }

    private fun readHead(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var matched = 0
        while (out.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            matched = when {
                b == '\r'.code && (matched == 0 || matched == 2) -> matched + 1
                b == '\n'.code && (matched == 1 || matched == 3) -> matched + 1
                b == '\r'.code -> 1
                else -> 0
            }
            if (matched == 4) return out.toString(Charsets.ISO_8859_1.name()).trimEnd()
        }
        return null
    }

    private fun reply(client: Socket, status: String, extraHeaders: String = "") {
        runCatching {
            client.getOutputStream().apply {
                write("HTTP/1.1 $status\r\n${extraHeaders}Content-Length: 0\r\nConnection: close\r\nProxy-Connection: close\r\n\r\n".toByteArray())
                flush()
            }
        }
        runCatching { client.close() }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(18).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private companion object {
        const val HEADER_TIMEOUT_MS = 15_000
        const val CONNECT_TIMEOUT_MS = 15_000
        const val MAX_HEAD = 8 * 1024
    }
}

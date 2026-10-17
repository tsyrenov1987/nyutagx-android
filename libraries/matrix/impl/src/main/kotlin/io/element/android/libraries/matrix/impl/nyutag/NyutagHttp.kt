/*
 * Нютаг — маленький HTTPS-клиент к сервисам ядра (приглашения) через тот же SOCKS «Кильватера».
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

/**
 * HTTP/1.1 поверх SOCKS5 (логин/пароль farvater-core, имя ядра разрешает вход) и TLS с доверием только нашему CA.
 * Блокирующий: звать с Dispatchers.IO. Тела запросов и ответов не логируются (там пароли и токены).
 */
internal object NyutagHttp {
    class Response(val status: Int, val body: String) {
        fun json(): JSONObject = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
    }

    private const val CONNECT_TIMEOUT_MS = 15_000

    /** Ответ ядра через туннель приходит за секунды; 20 с без ответа — «нет связи, попробуйте ещё раз», а не минутное ожидание. */
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_BODY = 256 * 1024

    private val sslContext: SSLContext by lazy {
        val ca = CertificateFactory.getInstance("X.509").generateCertificate(NyutagTunnel.caDer.inputStream())
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("nyutag-ca", ca)
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
        SSLContext.getInstance("TLS").apply { init(null, tmf.trustManagers, null) }
    }

    fun request(context: Context, method: String, path: String, jsonBody: String? = null, bearer: String? = null): Response {
        val proxy = URI(NyutagTunnel.proxyUrl(context) ?: throw IOException("Nyutag tunnel is not running"))
        val (user, pass) = (proxy.rawUserInfo ?: "").split(":", limit = 2).map { URLDecoder.decode(it, "UTF-8") } + listOf("", "")
        val target = URI(NyutagTunnel.HOMESERVER_URL)
        val host = target.host
        val port = target.port

        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(proxy.host, proxy.port), CONNECT_TIMEOUT_MS)
            raw.soTimeout = READ_TIMEOUT_MS
            socks5Connect(raw, user, pass, host, port)
            val tls = sslContext.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            tls.sslParameters = tls.sslParameters.apply { serverNames = listOf(SNIHostName(host)) }
            tls.startHandshake()
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, tls.session)) {
                throw IOException("Nyutag core certificate does not match $host")
            }
            tls.use { return exchange(it, method, "$host:$port", path, jsonBody, bearer) }
        } finally {
            runCatching { raw.close() }
        }
    }

    private fun exchange(socket: SSLSocket, method: String, hostHeader: String, path: String, jsonBody: String?, bearer: String?): Response {
        val body = jsonBody?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val head = buildString {
            append("$method $path HTTP/1.1\r\n")
            append("Host: $hostHeader\r\n")
            append("User-Agent: Nyutag-Android\r\n")
            append("Accept: application/json\r\n")
            append("Connection: close\r\n")
            if (bearer != null) append("Authorization: Bearer $bearer\r\n")
            if (jsonBody != null || method == "POST") {
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${body.size}\r\n")
            }
            append("\r\n")
        }
        socket.outputStream.apply {
            write(head.toByteArray(Charsets.UTF_8))
            write(body)
            flush()
        }
        val input = socket.inputStream.buffered()
        val statusLine = readLine(input) ?: throw IOException("Empty response")
        val status = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: throw IOException("Bad status line")
        var contentLength = -1
        var chunked = false
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val name = line.substringBefore(":").trim().lowercase()
            val value = line.substringAfter(":", "").trim()
            if (name == "content-length") contentLength = value.toIntOrNull() ?: -1
            if (name == "transfer-encoding" && value.lowercase().contains("chunked")) chunked = true
        }
        val bytes = when {
            chunked -> readChunked(input)
            contentLength >= 0 -> ByteArray(minOf(contentLength, MAX_BODY)).also { DataInputStream(input).readFully(it) }
            else -> readAll(input)
        }
        return Response(status, String(bytes, Charsets.UTF_8))
    }

    internal fun socks5Connect(socket: Socket, user: String, pass: String, host: String, port: Int) {
        val out = socket.outputStream
        val input = DataInputStream(socket.inputStream)
        out.write(byteArrayOf(5, 1, 2))
        out.flush()
        if (input.readUnsignedByte() != 5 || input.readUnsignedByte() != 2) throw IOException("SOCKS: auth method rejected")
        val u = user.toByteArray(Charsets.UTF_8)
        val p = pass.toByteArray(Charsets.UTF_8)
        out.write(byteArrayOf(1, u.size.toByte()) + u + byteArrayOf(p.size.toByte()) + p)
        out.flush()
        input.readUnsignedByte()
        if (input.readUnsignedByte() != 0) throw IOException("SOCKS: auth failed")
        val h = host.toByteArray(Charsets.US_ASCII)
        out.write(byteArrayOf(5, 1, 0, 3, h.size.toByte()) + h + byteArrayOf((port shr 8).toByte(), port.toByte()))
        out.flush()
        if (input.readUnsignedByte() != 5) throw IOException("SOCKS: bad reply")
        val rep = input.readUnsignedByte()
        input.readUnsignedByte()
        val addrLen = when (input.readUnsignedByte()) {
            1 -> 4
            4 -> 16
            3 -> input.readUnsignedByte()
            else -> throw IOException("SOCKS: bad address type")
        }
        input.skipBytes(addrLen + 2)
        if (rep != 0) throw IOException("SOCKS: connect failed ($rep)")
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
            buf.write(b)
            if (buf.size() > 8192) throw IOException("Header line too long")
        }
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataInputStream(input)
        while (true) {
            val size = (readLine(input) ?: break).substringBefore(";").trim().toInt(16)
            if (size == 0) break
            if (out.size() + size > MAX_BODY) throw IOException("Response too large")
            val chunk = ByteArray(size)
            data.readFully(chunk)
            out.write(chunk)
            readLine(input)
        }
        return out.toByteArray()
    }

    private fun readAll(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n == -1) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BODY) throw IOException("Response too large")
        }
        return out.toByteArray()
    }
}

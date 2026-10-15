/*
 * Нютаг — ICE-политика звонка: если UDP до SFU не ходит (зона белых списков), WebView сразу идёт через TURN,
 * а не ждёт ~16 с провала прямых кандидатов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.impl.utils

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom

object NyutagIcePolicy {
    /** coturn на nyutag-rtc: отвечает на STUN Binding по UDP. Нет ответа дважды = UDP до SFU мёртв. */
    private const val STUN_HOST = "2.31.27.66"
    private const val STUN_PORT = 3478
    private const val ATTEMPTS = 2
    private const val TIMEOUT_MS = 1500
    private const val WAIT_MS = 4000L
    private const val TTL_MS = 60_000L

    /** Имена TURN на российской стороне (k2/k3.cdn-tvzvezda-ru.ru): WebView ходит к ним напрямую, мимо туннеля. */
    const val RU_TURN_BYPASS = "*.cdn-tvzvezda-ru.ru"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var probe: Deferred<Boolean>? = null
    @Volatile private var probedAt = 0L

    /** Запускает замер UDP в фоне; свежий результат (моложе TTL) переиспользуется. */
    @Synchronized
    fun refresh() {
        val current = probe
        if (current != null && (current.isActive || System.currentTimeMillis() - probedAt < TTL_MS)) return
        probedAt = System.currentTimeMillis()
        probe = scope.async {
            val started = System.currentTimeMillis()
            val ok = udpReachable()
            Timber.i("Nyutag ICE: UDP to SFU ${if (ok) "alive" else "DEAD"} in ${System.currentTimeMillis() - started} ms")
            ok
        }
    }

    /** `relay` — только TURN (UDP мёртв), `all` — как обычно. Ждёт замер не дольше [WAIT_MS]. */
    fun policy(): String {
        val udpOk = runBlocking { withTimeoutOrNull(WAIT_MS) { probe?.await() } }
        return if (udpOk == false) "relay" else "all"
    }

    internal fun udpReachable(): Boolean {
        val request = ByteArray(20)
        request[1] = 0x01 // Binding Request, длина 0
        // magic cookie
        request[4] = 0x21
        request[5] = 0x12
        request[6] = 0xA4.toByte()
        request[7] = 0x42
        val transactionId = ByteArray(12).also { SecureRandom().nextBytes(it) }
        System.arraycopy(transactionId, 0, request, 8, 12)
        repeat(ATTEMPTS) {
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = TIMEOUT_MS
                    socket.send(DatagramPacket(request, request.size, InetSocketAddress(STUN_HOST, STUN_PORT)))
                    val buf = ByteArray(256)
                    val reply = DatagramPacket(buf, buf.size)
                    socket.receive(reply)
                    if (reply.length >= 20 && buf.copyOfRange(8, 20).contentEquals(transactionId)) return true
                }
            } catch (_: SocketTimeoutException) {
            } catch (e: Exception) {
                Timber.w("Nyutag ICE: STUN probe failed: ${e.message}")
            }
        }
        return false
    }

    class Bridge {
        @JavascriptInterface
        fun iceTransportPolicy(): String = policy()
    }

    /** Ставит мост и скрипт-обёртку RTCPeerConnection; вызывать до loadUrl. */
    fun install(webView: WebView) {
        webView.addJavascriptInterface(Bridge(), "nyutagCall")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, DOCUMENT_START_SCRIPT, setOf("*"))
        } else {
            Timber.w("Nyutag ICE: WebView without DOCUMENT_START_SCRIPT, policy hook not installed")
        }
    }

    private val DOCUMENT_START_SCRIPT = """
        (function () {
          if (window.__nyutagIce) return;
          window.__nyutagIce = true;
          var Orig = window.RTCPeerConnection;
          if (!Orig) return;
          function policy() {
            try { return (window.nyutagCall && window.nyutagCall.iceTransportPolicy()) || 'all'; } catch (e) { return 'all'; }
          }
          function patch(cfg) {
            if (policy() !== 'relay') return cfg;
            var c = Object.assign({}, cfg || {});
            c.iceTransportPolicy = 'relay';
            console.log('nyutag: ICE policy relay (UDP to SFU is dead)');
            return c;
          }
          class Patched extends Orig {
            constructor(cfg, constraints) { super(patch(cfg), constraints); }
            setConfiguration(cfg) { return super.setConfiguration(patch(cfg)); }
          }
          window.RTCPeerConnection = Patched;
          window.webkitRTCPeerConnection = Patched;
        })();
    """.trimIndent()
}

/*
 * Нютаг (форк Element X) — WebView звонка только через туннель.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.impl.utils

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.matrix.api.nyutag.NyutagWebProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume

/** Направляет весь сетевой трафик WebView процесса в локальный прокси туннеля до загрузки Element Call. */
fun interface CallWebViewRouter {
    suspend fun routeThroughTunnel()
}

@ContributesBinding(AppScope::class)
class DefaultCallWebViewRouter(
    private val nyutagWebProxy: NyutagWebProxy,
) : CallWebViewRouter {
    override suspend fun routeThroughTunnel() {
        val endpoint = nyutagWebProxy.start()
        check(WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) { "WebView without PROXY_OVERRIDE: call would bypass the tunnel" }
        NyutagIcePolicy.refresh() // замер UDP до SFU идёт, пока грузится Element Call
        val config = ProxyConfig.Builder()
            .addProxyRule("127.0.0.1:${endpoint.port}")
            .addBypassRule(NyutagIcePolicy.RU_TURN_BYPASS) // TURN на российской стороне — напрямую, не через туннель
            .build()
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                ProxyController.getInstance().setProxyOverride(config, { it.run() }) { cont.resume(Unit) }
            }
        }
        Timber.i("Nyutag: call WebView routed through local proxy")
    }
}

/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.element.android.features.call.impl.BuildConfig
import io.element.android.libraries.matrix.api.nyutag.NyutagWebProxy
import kotlinx.coroutines.flow.MutableSharedFlow
import timber.log.Timber

class WebViewWidgetMessageInterceptor(
    private val webView: WebView,
    private val onUrlLoaded: (String) -> Unit,
    private val onError: (String?) -> Unit,
    private val useBuryatStrings: Boolean = false,
) : WidgetMessageInterceptor {
    companion object {
        // We call both the WebMessageListener and the JavascriptInterface objects in JS with this
        // 'listenerName' so they can both receive the data from the WebView when
        // `${LISTENER_NAME}.postMessage(...)` is called
        const val LISTENER_NAME = "elementX"
    }

    // It's important to have extra capacity here to make sure we don't drop any messages
    override val interceptedMessages = MutableSharedFlow<String>(extraBufferCapacity = 10)

    init {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(webView.context))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                // Due to https://github.com/element-hq/element-x-android/issues/4097
                // we need to supply a logging implementation that correctly includes
                // objects in log lines.
                view.evaluateJavascript(
                    """
                        // Removing any parts that result in a circular structure. Circular structures crash JSON.stringify.
                        function safeStringify(object) {
                          const simpleObject = {};
                          for (const prop in object) {
                              if (!object.hasOwnProperty(prop)) {
                                  continue;
                              }
                              if (typeof(object[prop]) == 'object') {
                                  continue;
                              }
                              if (typeof(object[prop]) == 'function') {
                                  continue;
                              }
                              simpleObject[prop] = object[prop];
                          }
                          try {
                            return JSON.stringify(simpleObject);
                          } catch {
                            return "{Failed to stringify object}";
                          }
                        }

                        function logFn(consoleLogFn, ...args) {
                            consoleLogFn(
                                args.map(
                                    a => typeof a === "string" ? a : safeStringify(a)
                                ).join(' ')
                            );
                        };
                        globalThis.console.debug = logFn.bind(null, console.debug);
                        globalThis.console.log = logFn.bind(null, console.log);
                        globalThis.console.info = logFn.bind(null, console.info);
                        globalThis.console.warn = logFn.bind(null, console.warn);
                        globalThis.console.error = logFn.bind(null, console.error);
                    """.trimIndent(),
                    null
                )

                // We inject this JS code when the page starts loading to attach a message listener to the window.
                // This listener will receive both messages:
                // - EC widget API -> Element X (message.data.api == "fromWidget")
                // - Element X -> EC widget API (message.data.api == "toWidget"), we should ignore these
                view.evaluateJavascript(
                    """
                        window.addEventListener('message', function(event) {
                            let message = {data: event.data, origin: event.origin}
                            if (message.data.response && message.data.api == "toWidget"
                                || !message.data.response && message.data.api == "fromWidget") {
                                let json = JSON.stringify(event.data) 
                                ${"console.log('message sent: ' + json);".takeIf { BuildConfig.DEBUG }}
                                $LISTENER_NAME.postMessage(json);
                            } else {
                                ${"console.log('message received (ignored): ' + JSON.stringify(event.data));".takeIf { BuildConfig.DEBUG }}
                            }
                        });
                    """.trimIndent(),
                    null
                )

                // Нютаг: скрытую страницу без звука (собеседник молчит или выключил микрофон) Chromium через полминуты
                // останавливает — молчат кнопки плашки, и через 10 минут звонок рвётся: участие не продлевается.
                // Пока звонок свёрнут, страница играет неслышный сигнал (30 Гц, −66 дБ) — для Chromium она «звучит».
                view.evaluateJavascript(
                    """
                        (function() {
                            let keepAwake = null;
                            document.addEventListener('visibilitychange', function() {
                                if (document.visibilityState === 'hidden' && !keepAwake) {
                                    try {
                                        keepAwake = new AudioContext();
                                        const oscillator = keepAwake.createOscillator();
                                        oscillator.frequency.value = 30;
                                        const gain = keepAwake.createGain();
                                        gain.gain.value = 0.0005;
                                        oscillator.connect(gain).connect(keepAwake.destination);
                                        oscillator.start();
                                        ${"console.log('Nyutag keep awake: ' + keepAwake.state);".takeIf { BuildConfig.DEBUG }}
                                    } catch (e) {
                                        keepAwake = null;
                                    }
                                } else if (document.visibilityState === 'visible' && keepAwake) {
                                    keepAwake.close();
                                    keepAwake = null;
                                }
                            });
                        })();
                    """.trimIndent(),
                    null
                )
            }

            override fun onPageFinished(view: WebView, url: String) {
                onUrlLoaded(url)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                // No network for instance, transmit the error
                Timber.e("onReceivedError error: ${error?.errorCode} ${error?.description}")

                // Only propagate the error if it happens while loading the current page
                if (view?.url == request?.url.toString()) {
                    onError(error?.description.toString())
                }

                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                Timber.e("onReceivedHttpError error: ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase}")

                // Only propagate the error if it happens while loading the current page
                if (view?.url == request?.url.toString()) {
                    onError(errorResponse?.statusCode.toString())
                }

                super.onReceivedHttpError(view, request, errorResponse)
            }

            // Нютаг: 407 от нашего локального прокси (туннель) — отвечаем его одноразовой учёткой, больше никому.
            override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler, host: String?, realm: String?) {
                val proxy = NyutagWebProxy.current
                val isLocalProxyHost = host == "127.0.0.1" || host?.startsWith("127.0.0.1:") == true
                if (proxy != null && realm == NyutagWebProxy.REALM && isLocalProxyHost) {
                    handler.proceed(proxy.user, proxy.password)
                } else {
                    Timber.w("Nyutag: unexpected HTTP auth request, realm=$realm")
                    handler.cancel()
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                Timber.e("onReceivedSslError error: ${error?.primaryError}")

                // Only propagate the error if it happens while loading the current page
                if (view?.url == error?.url.toString()) {
                    onError(error?.toString())
                }

                super.onReceivedSslError(view, handler, error)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                return buryatStrings(request.url) ?: assetLoader.shouldInterceptRequest(request.url)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String): WebResourceResponse? {
                return buryatStrings(url.toUri()) ?: assetLoader.shouldInterceptRequest(url.toUri())
            }

            // Нютаг: при бурятском интерфейсе русские строки звонка — с бурятским переводом поверх.
            private fun buryatStrings(url: Uri): WebResourceResponse? {
                if (!useBuryatStrings || !NyutagCallStrings.isRussianStrings(url.path)) return null
                val russian = assetLoader.shouldInterceptRequest(url)?.data ?: return null
                return runCatching {
                    val overrides = webView.context.assets.open(NyutagCallStrings.BURYAT_ASSET).use { it.reader().readText() }
                    val merged = NyutagCallStrings.merge(russian.use { it.reader().readText() }, overrides)
                    WebResourceResponse("application/json", "utf-8", merged.byteInputStream())
                }.onFailure { Timber.w(it, "Nyutag: Buryat call strings not applied") }.getOrNull()
            }
        }

        // Always register JavascriptInterface as the baseline message channel.
        // This works on all WebView implementations including Huawei.
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun postMessage(json: String?) {
                onMessageReceived(json)
            }
        }, LISTENER_NAME)

        // Additionally register WebMessageListener on WebViews that reliably support it.
        // Huawei WebView (Chromium < 119) reports WEB_MESSAGE_LISTENER as supported
        // but silently drops messages, so we only trust it on Chromium 119+.
        // See: https://github.com/element-hq/element-x-android/issues/6632
        val webViewVersionName = WebViewCompat.getCurrentWebViewPackage(webView.context)?.versionName.orEmpty()
        Timber.d("Using WebView version: $webViewVersionName")
        val webViewVersionCode = webViewVersionName.split(".").firstOrNull()?.toIntOrNull() ?: 0

        if (webViewVersionCode >= 119 &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                LISTENER_NAME,
                setOf("*"),
                WebViewCompat.WebMessageListener { _, message, _, _, _ ->
                    onMessageReceived(message.data)
                }
            )
        }
    }

    override fun sendMessage(message: String) {
        webView.evaluateJavascript("postMessage($message, '*')", null)
    }

    private fun onMessageReceived(json: String?) {
        // Here is where we would handle the messages from the WebView, passing them to the Rust SDK
        json?.let { interceptedMessages.tryEmit(it) }
    }
}

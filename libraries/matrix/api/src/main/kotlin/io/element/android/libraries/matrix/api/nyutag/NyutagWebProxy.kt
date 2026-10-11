/*
 * Нютаг (форк Element X) — звонки через туннель.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

/**
 * Нютаг: локальный HTTP CONNECT прокси для WebView звонка (Element Call). Каждый поток уходит в SOCKS кильватера,
 * поэтому сигналинг LiveKit, выдача JWT и TCP-запасной путь медиа (ICE-TCP, TURN/TLS) идут через вход, а не напрямую.
 * UDP-медиа Chromium через прокси не пускает — оно идёт напрямую на SFU.
 */
interface NyutagWebProxy {
    /** Поднимает прокси (один раз на процесс) и возвращает адрес и учётку для `Proxy-Authorization: Basic`. */
    suspend fun start(): Endpoint

    data class Endpoint(val port: Int, val user: String, val password: String)

    companion object {
        /** Realm в `Proxy-Authenticate`; по нему WebView понимает, что спрашивает именно наш прокси. */
        const val REALM = "nyutag"

        /** Учётка поднятого прокси для ответа на 407 в `WebViewClient.onReceivedHttpAuthRequest`; null — прокси не поднят. */
        @Volatile
        var current: Endpoint? = null
    }
}

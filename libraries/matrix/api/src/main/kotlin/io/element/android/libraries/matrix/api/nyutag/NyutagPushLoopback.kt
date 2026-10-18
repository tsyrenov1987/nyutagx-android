/*
 * Нютаг — тестовый пуш через ядро.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

/**
 * Шлюз пушей ядра (Sygnal) слушает только 127.0.0.1, поэтому «Тест обратной отправки» в проверке уведомлений
 * не достучится до него с телефона. Тестовый пуш на это устройство отправляет само ядро.
 */
interface NyutagPushLoopback {
    /** Отправляет тестовый пуш; `true` — шлюз отклонил pushkey (токен устройства устарел). */
    suspend fun notify(appId: String, pushKey: String): Result<Boolean>

    class RateLimitedException : Exception("Too many test pushes")
}

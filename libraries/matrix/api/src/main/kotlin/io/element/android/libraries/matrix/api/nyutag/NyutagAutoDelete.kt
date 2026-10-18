/*
 * Нютаг (форк Element X) — автоудаление сообщений в чате, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId

/**
 * Нютаг: таймер автоудаления хранится в state-событии комнаты [NyutagAutoDeleteRule.EVENT_TYPE] (формат общий с iOS):
 * `{"ttl": <секунды>, "since": <мс>}`. Сервер (E2EE) содержимое не видит, поэтому удаляют клиенты:
 * все прячут истёкшие сообщения, отправитель удаляет (redact) свои.
 */
interface NyutagAutoDelete {
    suspend fun get(sessionId: SessionId, roomId: RoomId): Result<NyutagAutoDeleteRule>

    /** [ttlSeconds] = 0 выключает таймер. Действует на сообщения, отправленные с [nowMs]. */
    suspend fun set(sessionId: SessionId, roomId: RoomId, ttlSeconds: Long, nowMs: Long): Result<NyutagAutoDeleteRule>
}

data class NyutagAutoDeleteRule(
    val ttlSeconds: Long = 0,
    val sinceMs: Long = 0,
) {
    val isEnabled: Boolean get() = ttlSeconds > 0

    /** Сообщения, отправленные до включения таймера, не трогаем — как в Telegram. */
    fun isExpired(timestampMs: Long, nowMs: Long): Boolean =
        isEnabled && timestampMs >= sinceMs && timestampMs + ttlSeconds * 1000 <= nowMs

    companion object {
        const val EVENT_TYPE = "chat.nyutag.autodelete"
        const val DAY = 86_400L
        const val WEEK = 7 * DAY
        const val MONTH = 31 * DAY

        /** Варианты как в Telegram: выкл, 1 день, 1 неделя, 1 месяц. */
        val OPTIONS = listOf(0L, DAY, WEEK, MONTH)
    }
}

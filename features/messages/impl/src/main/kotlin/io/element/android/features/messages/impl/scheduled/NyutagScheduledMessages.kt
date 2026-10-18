/*
 * Нютаг (форк Element X) — очередь отложенных сообщений.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.scheduled

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow

/**
 * Сервер Matrix отложенную отправку не умеет, поэтому очередь хранится на телефоне,
 * а в нужное время сообщение отправляет WorkManager (нужна сеть; при выключенном телефоне — после включения).
 */
interface NyutagScheduledMessages {
    /** Очередь чата, ближайшие первыми. */
    fun observe(sessionId: SessionId, roomId: RoomId): Flow<List<NyutagScheduledMessage>>

    suspend fun get(id: String): NyutagScheduledMessage?

    suspend fun schedule(message: NyutagScheduledMessage)

    suspend fun sendNow(id: String)

    suspend fun delete(id: String)
}

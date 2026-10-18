/*
 * Нютаг (форк Element X) — фейк очереди отложенных сообщений для тестов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.scheduled

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeNyutagScheduledMessages : NyutagScheduledMessages {
    val items = MutableStateFlow<List<NyutagScheduledMessage>>(emptyList())
    val sentNow = mutableListOf<String>()

    override fun observe(sessionId: SessionId, roomId: RoomId): Flow<List<NyutagScheduledMessage>> =
        items.map { list -> list.filter { it.isIn(sessionId, roomId) }.sortedBy { it.sendAtMs } }

    override suspend fun get(id: String) = items.value.firstOrNull { it.id == id }

    override suspend fun schedule(message: NyutagScheduledMessage) {
        items.value = items.value.filterNot { it.id == message.id } + message
    }

    override suspend fun sendNow(id: String) {
        sentNow += id
    }

    override suspend fun delete(id: String) {
        items.value = items.value.filterNot { it.id == id }
    }
}

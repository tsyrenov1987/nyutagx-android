/*
 * Нютаг — личные чаты без нашего ответа, в памяти для тестов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.test.nyutag

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagUnansweredChats

class FakeNyutagUnansweredChats : NyutagUnansweredChats {
    private val chats = mutableMapOf<SessionId, Set<RoomId>>()
    override fun all(sessionId: SessionId): Set<RoomId> = chats[sessionId].orEmpty()
    override fun add(sessionId: SessionId, roomId: RoomId) { chats[sessionId] = all(sessionId) + roomId }
    override fun remove(sessionId: SessionId, roomId: RoomId) { chats[sessionId] = all(sessionId) - roomId }
}

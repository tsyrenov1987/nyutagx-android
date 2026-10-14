/*
 * Нютаг — личные чаты, принятые сами (написал незнакомый), где мы ещё не ответили.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId

/** Пока чат здесь, в нём видна надпись «Вне контактов»; хранится только на этом устройстве. */
interface NyutagUnansweredChats {
    fun all(sessionId: SessionId): Set<RoomId>
    fun add(sessionId: SessionId, roomId: RoomId)
    fun remove(sessionId: SessionId, roomId: RoomId)
}

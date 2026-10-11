/*
 * Нютаг — заявки в закрытые сообщества, поданные с этого устройства.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId

/** Приглашение после одобрения такой заявки принимается само, как во ВКонтакте. */
interface NyutagKnockedRooms {
    fun all(sessionId: SessionId): Set<RoomId>
    fun add(sessionId: SessionId, roomId: RoomId)
    fun remove(sessionId: SessionId, roomId: RoomId)
}

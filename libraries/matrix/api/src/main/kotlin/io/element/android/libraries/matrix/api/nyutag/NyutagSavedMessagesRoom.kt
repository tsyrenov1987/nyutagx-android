/*
 * Нютаг — «Избранное» (чат с самим собой): id комнаты лежит в account data, общий для Android и iOS.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId

const val NYUTAG_SAVED_MESSAGES_TYPE = "chat.nyutag.saved_messages"

private val ROOM_ID_REGEX = Regex("\"room_id\"\\s*:\\s*\"([^\"]+)\"")

/** Комната «Избранного» из account data; null, если её ещё не создавали. */
suspend fun MatrixClient.nyutagSavedMessagesRoomId(): RoomId? {
    return getAccountData(NYUTAG_SAVED_MESSAGES_TYPE).getOrNull()
        ?.let { ROOM_ID_REGEX.find(it)?.groupValues?.get(1) }
        ?.let { tryOrNull { RoomId(it) } }
}

/*
 * Нютаг — автоприём приглашений: новая переписка или звонок не должны выглядеть как «приглашение в чат».
 * Сервер закрытый (без федерации), чужих нет — приглашение от участника принимаем сразу, как в Telegram/WhatsApp.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

object NyutagInviteAutoJoin {
    /** Следит за списком комнат и вступает во все, куда нас пригласили. Одна попытка на комнату за сессию. */
    suspend fun run(client: MatrixClient) {
        val attempted = mutableSetOf<RoomId>()
        client.roomListService.allRooms.summaries
            .map { summaries ->
                summaries.filter { it.info.currentUserMembership == CurrentUserMembership.INVITED }.map { it.roomId }
            }
            .distinctUntilChanged()
            .collect { invited ->
                for (roomId in invited) {
                    if (!attempted.add(roomId)) continue
                    Timber.i("Nyutag: auto-joining invited room $roomId")
                    client.joinRoom(roomId).onFailure { Timber.w(it, "Nyutag: auto-join failed for $roomId") }
                }
            }
    }
}

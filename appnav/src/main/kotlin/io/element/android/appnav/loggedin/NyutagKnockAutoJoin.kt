/*
 * Нютаг — заявку в закрытое сообщество одобрили (пришло приглашение): вступаем сами, как во ВКонтакте.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.appnav.loggedin

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.nyutag.NyutagKnockedRooms
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@SingleIn(SessionScope::class)
@Inject
class NyutagKnockAutoJoin(
    private val matrixClient: MatrixClient,
    private val knockedRooms: NyutagKnockedRooms,
) {
    /** Одобренные заявки, по которым уже пробовали вступить (до следующего статуса «заявка подана»). */
    private val attempts = mutableSetOf<RoomId>()

    fun launchIn(coroutineScope: CoroutineScope) {
        val sessionId = matrixClient.sessionId
        matrixClient.roomListService.allRooms.summaries
            .onEach { summaries ->
                val knocked = knockedRooms.all(sessionId)
                if (knocked.isEmpty()) return@onEach
                summaries.filter { it.roomId in knocked }.forEach { summary ->
                    when (summary.info.currentUserMembership) {
                        CurrentUserMembership.KNOCKED -> attempts.remove(summary.roomId)
                        // Покинутая комната остаётся в списке и до заявки — забываем только после вступления или бана.
                        CurrentUserMembership.LEFT -> Unit
                        // Кэш может ещё держать старое приглашение: одна попытка на каждое приглашение, отказ заявку не стирает.
                        CurrentUserMembership.INVITED -> if (attempts.add(summary.roomId)) {
                            matrixClient.joinRoom(summary.roomId)
                                .onSuccess { knockedRooms.remove(sessionId, summary.roomId) }
                                .onFailure { Timber.e(it, "Failed joining approved knock ${summary.roomId}") }
                        }
                        else -> knockedRooms.remove(sessionId, summary.roomId)
                    }
                }
            }
            .launchIn(coroutineScope)
    }
}

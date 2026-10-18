/*
 * Нютаг — отдельный маленький список SDK только для закреплённых чатов:
 * основной список постраничный, и закреп из глубины истории в нём мог бы ещё не загрузиться.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.nyutag

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.home.impl.datasource.RoomListRoomSummaryFactory
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Inject
@SingleIn(SessionScope::class)
class NyutagPinnedRoomsDataSource(
    roomListService: RoomListService,
    private val roomListRoomSummaryFactory: RoomListRoomSummaryFactory,
    coroutineDispatchers: CoroutineDispatchers,
    @SessionCoroutineScope
    sessionCoroutineScope: CoroutineScope,
) {
    private val roomList = roomListService.createRoomList(
        pageSize = NYUTAG_MAX_PINNED_CHATS,
        source = RoomList.Source.All,
        coroutineScope = sessionCoroutineScope,
    )

    val summaries: Flow<List<RoomListRoomSummary>> = roomList.summaries
        .map { summaries -> summaries.map { roomListRoomSummaryFactory.create(it).copy(isPinned = true) } }
        .flowOn(coroutineDispatchers.computation)

    suspend fun updateFilter(filter: RoomListFilter) {
        roomList.updateFilter(filter)
    }
}

/** Закреплённые — сверху в порядке закрепа, в основном списке их больше нет. */
fun mergePinnedRooms(
    pins: List<String>,
    pinned: List<RoomListRoomSummary>,
    rooms: List<RoomListRoomSummary>,
): List<RoomListRoomSummary> {
    if (pins.isEmpty()) return rooms
    val byId = pinned.associateBy { it.roomId.value }
    val top = pins.mapNotNull { byId[it] }
    val topIds = top.mapTo(HashSet()) { it.roomId.value }
    return top + rooms.filterNot { it.roomId.value in topIds }
}

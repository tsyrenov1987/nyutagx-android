/*
 * Нютаг (форк Element X Android).
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.appnav.loggedin

import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.roomlist.FakeDynamicRoomList
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.services.toolbox.test.strings.FakeStringProvider
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NyutagSavedMessagesNameTest {
    private val roomList = FakeDynamicRoomList()
    private val setName = lambdaRecorder<String, Result<Unit>> { Result.success(Unit) }
    private val client = FakeMatrixClient(
        roomListService = FakeRoomListService(allRooms = roomList),
        getAccountDataLambda = { Result.success("{\"room_id\":\"${A_ROOM_ID.value}\"}") },
    ).apply {
        givenGetRoomResult(A_ROOM_ID, FakeJoinedRoom(setNameResult = setName))
    }

    @Test
    fun `saved messages created in another language get the app language name once`() = runTest {
        NyutagSavedMessagesName(client, FakeStringProvider("Хадагалһан")).launchIn(backgroundScope)
        roomList.summaries.value = listOf(
            aRoomSummary(roomId = A_ROOM_ID, rawName = "Saved messages"),
            // Чужая комната с таким же именем — не «Избранное», её не трогаем.
            aRoomSummary(roomId = A_ROOM_ID_2, rawName = "Saved messages"),
        )
        advanceTimeBy(1_000)
        runCurrent()
        roomList.summaries.value = listOf(aRoomSummary(roomId = A_ROOM_ID, rawName = "Избранное"))
        advanceTimeBy(1_000)
        runCurrent()
        setName.assertions().isCalledOnce().with(value("Хадагалһан"))
    }

    @Test
    fun `a custom name and an already translated name are kept`() = runTest {
        NyutagSavedMessagesName(client, FakeStringProvider("Избранное")).launchIn(backgroundScope)
        roomList.summaries.value = listOf(aRoomSummary(roomId = A_ROOM_ID, rawName = "Мои заметки"))
        advanceTimeBy(1_000)
        runCurrent()
        roomList.summaries.value = listOf(aRoomSummary(roomId = A_ROOM_ID, rawName = "Избранное"))
        advanceTimeBy(1_000)
        runCurrent()
        setName.assertions().isNeverCalled()
    }
}

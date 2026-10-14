/*
 * Нютаг (форк Element X Android) — поиск сообщений внутри одного чата.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.roomdetails.impl.nyutagsearch

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.eventformatter.test.FakeRoomLatestEventFormatter
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.search.MessageSearchPaginationState
import io.element.android.libraries.matrix.api.search.MessageSearchResult
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.search.FakeMessageSearch
import io.element.android.libraries.matrix.test.search.FakeMessageSearchService
import io.element.android.tests.testutils.test
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NyutagRoomSearchPresenterTest {
    @Test
    fun `search is scoped to the room and starts empty`() = runTest {
        val service = FakeMessageSearchService()
        createPresenter(service).test {
            val state = awaitItem()
            assertThat(service.lastRoomId).isEqualTo(A_ROOM_ID)
            assertThat(state.searchedQuery).isEmpty()
            assertThat(state.results).isEmpty()
            assertThat(state.isLoading).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing sets a trimmed query and pages until a screen is filled`() = runTest {
        val search = FakeMessageSearch()
        createPresenter(FakeMessageSearchService(search)).test {
            awaitItem().queryState.setTextAndPlaceCursorAtEnd("  омуль ")
            advanceUntilIdle()
            assertThat(search.lastQuery).isEqualTo("омуль")
            // Ни одного результата этого чата и конец не достигнут — дочитываем.
            assertThat(search.paginateCallCount).isGreaterThan(0)
            val calls = search.paginateCallCount
            search.emitResults(List(NYUTAG_ROOM_SEARCH_PAGE) { aResult("\$$it") }.toImmutableList())
            advanceUntilIdle()
            // Экран набран — больше не дочитываем.
            assertThat(search.paginateCallCount).isEqualTo(calls)
            val state = expectMostRecentItem()
            assertThat(state.searchedQuery).isEqualTo("омуль")
            assertThat(state.results).hasSize(NYUTAG_ROOM_SEARCH_PAGE)
            assertThat(state.isLoading).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no paging and empty state once the end is reached`() = runTest {
        val search = FakeMessageSearch()
        search.emitPaginationState(MessageSearchPaginationState.Idle(endReached = true))
        createPresenter(FakeMessageSearchService(search)).test {
            awaitItem().queryState.setTextAndPlaceCursorAtEnd("буузы")
            advanceUntilIdle()
            assertThat(search.paginateCallCount).isEqualTo(0)
            val state = expectMostRecentItem()
            assertThat(state.results).isEmpty()
            assertThat(state.isEndReached).isTrue()
            assertThat(state.isLoading).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load more asks for another page`() = runTest {
        val search = FakeMessageSearch()
        createPresenter(FakeMessageSearchService(search)).test {
            awaitItem().queryState.setTextAndPlaceCursorAtEnd("омуль")
            search.emitResults(List(NYUTAG_ROOM_SEARCH_PAGE) { aResult("\$$it") }.toImmutableList())
            advanceUntilIdle()
            val calls = search.paginateCallCount
            expectMostRecentItem().eventSink(NyutagRoomSearchEvent.LoadMore)
            advanceUntilIdle()
            assertThat(search.paginateCallCount).isGreaterThan(calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun aResult(eventId: String) = MessageSearchResult(
        roomId = A_ROOM_ID,
        eventId = EventId(eventId),
        senderId = A_USER_ID,
        senderProfile = ProfileDetails.Ready(displayName = "Доржо", displayNameAmbiguous = false, avatarUrl = null, displayedStatus = null),
        content = MessageContent(body = "омуль", inReplyTo = null, isEdited = false, threadInfo = null, type = TextMessageType("омуль", null)),
        timestamp = 0L,
    )

    private fun createPresenter(service: FakeMessageSearchService) = NyutagRoomSearchPresenter(
        room = FakeBaseRoom(roomId = A_ROOM_ID),
        messageSearchService = service,
        latestEventFormatter = FakeRoomLatestEventFormatter(),
        dateFormatter = FakeDateFormatter(),
    )
}

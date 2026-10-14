/*
 * Нютаг (форк Element X Android) — поиск сообщений внутри одного чата.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.roomdetails.impl.nyutagsearch

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.eventformatter.api.RoomLatestEventFormatter
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.roomlist.LatestEventValue
import io.element.android.libraries.matrix.api.search.MessageSearchPaginationState
import io.element.android.libraries.matrix.api.search.MessageSearchResult
import io.element.android.libraries.matrix.api.search.MessageSearchService
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class NyutagRoomSearchItem(
    val eventId: EventId,
    val senderId: UserId,
    val senderName: String,
    val senderAvatarUrl: String?,
    val body: String,
    val formattedTimestamp: String,
)

data class NyutagRoomSearchState(
    val queryState: TextFieldState,
    /** Запрос, по которому показаны результаты (после паузы ввода), пустой — поиск ещё не начат. */
    val searchedQuery: String,
    val results: ImmutableList<NyutagRoomSearchItem>,
    val isLoading: Boolean,
    val isEndReached: Boolean,
    val eventSink: (NyutagRoomSearchEvent) -> Unit,
)

sealed interface NyutagRoomSearchEvent {
    data object LoadMore : NyutagRoomSearchEvent
    data object ClearQuery : NyutagRoomSearchEvent
}

internal const val NYUTAG_ROOM_SEARCH_PAGE = 20

/**
 * Тот же локальный индекс SDK, что у общего поиска (шифрованные чаты тоже), но выдача — только этой комнаты.
 * Индекс один на все чаты, поэтому страницы дочитываются, пока не наберётся экран сообщений этого чата.
 */
@Inject
class NyutagRoomSearchPresenter(
    private val room: BaseRoom,
    private val messageSearchService: MessageSearchService,
    private val latestEventFormatter: RoomLatestEventFormatter,
    private val dateFormatter: DateFormatter,
) : Presenter<NyutagRoomSearchState> {
    @Composable
    override fun present(): NyutagRoomSearchState {
        val coroutineScope = rememberCoroutineScope()
        val search = remember { messageSearchService.createMessageSearch(scope = coroutineScope, roomId = room.roomId) }
        val queryState = rememberTextFieldState()
        var searchedQuery by remember { mutableStateOf("") }
        var wanted by remember { mutableIntStateOf(NYUTAG_ROOM_SEARCH_PAGE) }
        val results by search.results.collectAsState()
        val paginationState by search.paginationState.collectAsState()

        LaunchedEffect(queryState.text) {
            val query = queryState.text.toString().trim()
            if (query.isNotEmpty()) {
                // Пауза, чтобы не искать на каждую букву.
                delay(250.milliseconds)
                wanted = NYUTAG_ROOM_SEARCH_PAGE
                searchedQuery = query
                search.setQuery(query)
            } else {
                searchedQuery = ""
            }
        }

        LaunchedEffect(searchedQuery, results.size, paginationState, wanted) {
            val state = paginationState
            if (searchedQuery.isNotEmpty() && state is MessageSearchPaginationState.Idle && !state.endReached && results.size < wanted) {
                search.paginate()
            }
        }

        val items = remember(results, searchedQuery) {
            if (searchedQuery.isEmpty()) persistentListOf() else results.map(::toItem).toImmutableList()
        }
        val isEndReached = (paginationState as? MessageSearchPaginationState.Idle)?.endReached == true

        fun handleEvent(event: NyutagRoomSearchEvent) {
            when (event) {
                NyutagRoomSearchEvent.LoadMore -> wanted = items.size + NYUTAG_ROOM_SEARCH_PAGE
                NyutagRoomSearchEvent.ClearQuery -> coroutineScope.launch { queryState.clearText() }
            }
        }

        return NyutagRoomSearchState(
            queryState = queryState,
            searchedQuery = searchedQuery,
            results = items,
            isLoading = searchedQuery.isNotEmpty() && !isEndReached && items.size < wanted,
            isEndReached = isEndReached,
            eventSink = ::handleEvent,
        )
    }

    private fun toItem(result: MessageSearchResult): NyutagRoomSearchItem {
        val body = latestEventFormatter.format(
            latestEvent = LatestEventValue.Remote(
                timestamp = result.timestamp,
                content = result.content,
                senderId = result.senderId,
                senderProfile = result.senderProfile,
                isOwn = result.senderId == room.sessionId,
            ),
            // Имя отправителя и так стоит заголовком строки.
            isDmRoom = true,
        )
        return NyutagRoomSearchItem(
            eventId = result.eventId,
            senderId = result.senderId,
            senderName = result.senderProfile.getDisambiguatedDisplayName(result.senderId),
            senderAvatarUrl = (result.senderProfile as? ProfileDetails.Ready)?.avatarUrl,
            body = body?.toString().orEmpty(),
            formattedTimestamp = dateFormatter.format(
                timestamp = result.timestamp,
                mode = DateFormatterMode.TimeOrDate,
                useRelative = true,
            ),
        )
    }
}

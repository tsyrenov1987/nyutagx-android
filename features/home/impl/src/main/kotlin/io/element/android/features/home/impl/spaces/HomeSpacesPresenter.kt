/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spaces

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.invite.api.SeenInvitesStore
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.nyutag.NyutagCommunities
import io.element.android.libraries.matrix.api.nyutag.NyutagCommunity
import io.element.android.libraries.matrix.api.room.RoomType
import io.element.android.libraries.matrix.api.room.join.JoinRule
import io.element.android.libraries.matrix.api.spaces.SpaceRoom
import io.element.android.libraries.matrix.ui.safety.rememberHideInvitesAvatar
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

@Inject
class HomeSpacesPresenter(
    private val client: MatrixClient,
    private val seenInvitesStore: SeenInvitesStore,
    private val nyutagCommunities: NyutagCommunities,
) : Presenter<HomeSpacesState> {
    @Composable
    override fun present(): HomeSpacesState {
        val hideInvitesAvatar by client.rememberHideInvitesAvatar()
        val spaceRooms by remember {
            client.spaceService.topLevelSpacesFlow.map { it.toImmutableList() }
        }.collectAsState(persistentListOf())

        val seenSpaceInvites by remember {
            seenInvitesStore.seenRoomIds().map { it.toImmutableSet() }
        }.collectAsState(persistentSetOf())

        var searchQuery by rememberSaveable { mutableStateOf("") }
        var catalogRefreshes by remember { mutableIntStateOf(0) }
        var serverCommunities by remember { mutableStateOf(emptyList<SpaceRoom>()) }
        var isLoadingCatalog by remember { mutableStateOf(true) }
        LaunchedEffect(searchQuery, catalogRefreshes, spaceRooms.size) {
            // Перезапуск эффекта отменяет прошлый запрос — пауза работает как debounce набора.
            if (searchQuery.isNotBlank()) delay(SEARCH_DEBOUNCE_MILLIS)
            isLoadingCatalog = true
            nyutagCommunities.communities(client.sessionId, searchQuery)
                .onSuccess { page -> serverCommunities = page.communities.map { it.toSpaceRoom() } }
            isLoadingCatalog = false
        }
        val joinedIds = spaceRooms.map { it.roomId }.toSet()
        val catalog = serverCommunities.filter { it.roomId !in joinedIds }.toImmutableList()

        fun handleEvent(event: HomeSpacesEvent) {
            when (event) {
                is HomeSpacesEvent.UpdateSearchQuery -> searchQuery = event.query
                HomeSpacesEvent.RefreshCatalog -> catalogRefreshes++
            }
        }

        return HomeSpacesState(
            space = CurrentSpace.Root,
            spaceRooms = spaceRooms,
            seenSpaceInvites = seenSpaceInvites,
            hideInvitesAvatar = hideInvitesAvatar,
            // TODO enable once we can link to the screen to explore public spaces
            canExploreSpaces = false,
            searchQuery = searchQuery,
            catalog = catalog,
            isLoadingCatalog = isLoadingCatalog,
            eventSink = ::handleEvent,
        )
    }
}

private const val SEARCH_DEBOUNCE_MILLIS = 350L

/** Сообщество из каталога в виде строки списка; [SpaceRoom.state] = null — пользователь ещё не участник. */
private fun NyutagCommunity.toSpaceRoom() = SpaceRoom(
    rawName = name,
    displayName = name ?: alias ?: roomId.value,
    avatarUrl = avatarUrl,
    canonicalAlias = null,
    childrenCount = 0,
    guestCanJoin = false,
    heroes = persistentListOf(),
    joinRule = if (isOpen) JoinRule.Public else JoinRule.Knock,
    numJoinedMembers = membersCount,
    roomId = roomId,
    roomType = RoomType.Space,
    state = null,
    topic = topic,
    worldReadable = false,
    via = persistentListOf(),
    isDirect = false,
    isDm = false,
)

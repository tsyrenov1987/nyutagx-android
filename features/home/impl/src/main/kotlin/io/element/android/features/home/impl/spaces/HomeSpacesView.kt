/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.ui.components.SpaceRoomItemView
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun HomeSpacesView(
    state: HomeSpacesState,
    lazyListState: LazyListState,
    contentPadding: PaddingValues,
    onSpaceClick: (RoomId) -> Unit,
    onCreateSpaceClick: () -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Нютаг: каталог обновляется при каждом открытии вкладки — новые сообщества видны сразу.
    LaunchedEffect(Unit) { state.eventSink(HomeSpacesEvent.RefreshCatalog) }
    val mySpaces = state.visibleSpaceRooms
    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = contentPadding,
    ) {
        item(key = "search") {
            CommunitySearchField(
                query = state.searchQuery,
                onQueryChange = { state.eventSink(HomeSpacesEvent.UpdateSearchQuery(it)) },
            )
        }
        if (mySpaces.isNotEmpty()) {
            item(key = "mine") {
                ListSectionHeader(title = stringResource(CommonStrings.nyutag_communities_mine), hasDivider = false)
            }
            itemsIndexed(
                items = mySpaces,
                key = { _, spaceRoom -> spaceRoom.roomId.value }
            ) { index, spaceRoom ->
                val isInvitation = spaceRoom.state == CurrentUserMembership.INVITED
                SpaceRoomItemView(
                    spaceRoom = spaceRoom,
                    showUnreadIndicator = isInvitation && spaceRoom.roomId !in state.seenSpaceInvites,
                    hideAvatars = isInvitation && state.hideInvitesAvatar,
                    onClick = {
                        onSpaceClick(spaceRoom.roomId)
                    },
                    onLongClick = {},
                )
                if (index != mySpaces.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
        if (state.catalog.isNotEmpty()) {
            item(key = "all") {
                ListSectionHeader(title = stringResource(CommonStrings.nyutag_communities_all), hasDivider = mySpaces.isNotEmpty())
            }
            itemsIndexed(
                items = state.catalog,
                key = { _, spaceRoom -> "catalog:${spaceRoom.roomId.value}" }
            ) { index, spaceRoom ->
                SpaceRoomItemView(
                    spaceRoom = spaceRoom,
                    showUnreadIndicator = false,
                    hideAvatars = false,
                    onClick = {
                        onSpaceClick(spaceRoom.roomId)
                    },
                    onLongClick = {},
                )
                if (index != state.catalog.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
        if (mySpaces.isEmpty() && state.catalog.isEmpty()) {
            item(key = "empty") {
                when {
                    state.isLoadingCatalog -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    state.searchQuery.isNotBlank() -> Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        text = stringResource(CommonStrings.common_no_results),
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    else -> EmptyCommunitiesView(onCreateSpaceClick = onCreateSpaceClick)
                }
            }
        }
    }
}

@Composable
private fun CommunitySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        value = query,
        onValueChange = onQueryChange,
        placeholder = stringResource(CommonStrings.nyutag_communities_search),
        leadingIcon = {
            Icon(imageVector = CompoundIcons.Search(), contentDescription = null)
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = CompoundIcons.Close(), contentDescription = stringResource(CommonStrings.action_clear))
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun EmptyCommunitiesView(
    onCreateSpaceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 16.dp, start = 40.dp, end = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BigIcon(
            style = BigIcon.Style.Default(CompoundIcons.SpaceSolid())
        )
        Text(
            text = stringResource(CommonStrings.nyutag_communities_empty),
            style = ElementTheme.typography.fontHeadingSmMedium,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(CommonStrings.action_create_space),
            onClick = onCreateSpaceClick,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun HomeSpacesViewPreview(
    @PreviewParameter(HomeSpacesStatePreviewParam::class) state: HomeSpacesState,
) = ElementPreview {
    HomeSpacesView(
        state = state,
        lazyListState = rememberLazyListState(),
        onSpaceClick = {},
        onCreateSpaceClick = {},
        onExploreClick = {},
        contentPadding = PaddingValues(bottom = 112.dp),
    )
}

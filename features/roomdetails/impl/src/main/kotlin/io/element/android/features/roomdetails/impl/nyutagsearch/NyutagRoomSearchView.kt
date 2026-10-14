/*
 * Нютаг (форк Element X Android) — поиск сообщений внутри одного чата.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.roomdetails.impl.nyutagsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.modifiers.niceClickable
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.FilledTextField
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.scaffoldScrollableContentInsets
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun NyutagRoomSearchView(
    state: NyutagRoomSearchState,
    onBackClick: () -> Unit,
    onResultClick: (EventId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onClick = onBackClick) },
                title = { SearchField(state) },
            )
        },
        contentWindowInsets = scaffoldScrollableContentInsets,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item { HorizontalDivider(color = ElementTheme.colors.borderInteractivePrimary) }
            if (state.searchedQuery.isEmpty()) {
                item { Hint(stringResource(CommonStrings.nyutag_room_search_note)) }
            } else if (state.results.isEmpty() && state.isEndReached) {
                item { Hint(stringResource(CommonStrings.nyutag_room_search_empty), centered = true) }
            }
            itemsIndexed(state.results, key = { _, item -> item.eventId.value }) { index, item ->
                ResultRow(item = item, onClick = { onResultClick(item.eventId) })
                if (index == state.results.lastIndex) {
                    LaunchedEffect(state.results.size) { state.eventSink(NyutagRoomSearchEvent.LoadMore) }
                }
            }
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(state: NyutagRoomSearchState) {
    val focusRequester = remember { FocusRequester() }
    val label = stringResource(CommonStrings.nyutag_room_search_hint)
    FilledTextField(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = label },
        state = state.queryState,
        placeholder = { Text(label) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
        trailingIcon = if (state.queryState.text.isNotEmpty()) {
            @Composable {
                IconButton(
                    modifier = Modifier.clip(CircleShape).size(20.dp),
                    onClick = { state.eventSink(NyutagRoomSearchEvent.ClearQuery) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = ElementTheme.colors.borderInteractivePrimary,
                        contentColor = ElementTheme.colors.iconOnSolidPrimary,
                    ),
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = CompoundIcons.Close(),
                        contentDescription = stringResource(CommonStrings.a11y_clear_search_field),
                    )
                }
            }
        } else {
            null
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun Hint(text: String, centered: Boolean = false) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (centered) 32.dp else 16.dp),
        text = text,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        style = if (centered) ElementTheme.typography.fontBodyLgRegular else ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textSecondary,
    )
}

@Composable
private fun ResultRow(item: NyutagRoomSearchItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .niceClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(
            modifier = Modifier.padding(top = 12.dp),
            avatarData = AvatarData(id = item.senderId.value, name = item.senderName, url = item.senderAvatarUrl, size = AvatarSize.SearchRoomListItem),
            avatarType = AvatarType.User,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = item.senderName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ElementTheme.typography.fontBodyMdMedium,
                )
                Text(
                    text = item.formattedTimestamp,
                    maxLines = 1,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            Text(
                text = item.body,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

internal class NyutagRoomSearchStateProvider : PreviewParameterProvider<NyutagRoomSearchState> {
    override val values = sequenceOf(
        aNyutagRoomSearchState(),
        aNyutagRoomSearchState(
            searchedQuery = "омуль",
            results = persistentListOf(
                NyutagRoomSearchItem(EventId("\$1"), UserId("@dorzho:nyutag.chat"), "Доржо", null, "Омуль с Байкала привезу в субботу", "12:40"),
                NyutagRoomSearchItem(EventId("\$2"), UserId("@sesegma:nyutag.chat"), "Сэсэгма", null, "Где брали омуль?", "вчера"),
            ),
        ),
        aNyutagRoomSearchState(searchedQuery = "буузы", isEndReached = true),
        aNyutagRoomSearchState(searchedQuery = "буузы", isLoading = true),
    )
}

internal fun aNyutagRoomSearchState(
    searchedQuery: String = "",
    results: ImmutableList<NyutagRoomSearchItem> = persistentListOf(),
    isLoading: Boolean = false,
    isEndReached: Boolean = false,
) = NyutagRoomSearchState(
    queryState = TextFieldState(searchedQuery),
    searchedQuery = searchedQuery,
    results = results,
    isLoading = isLoading,
    isEndReached = isEndReached,
    eventSink = {},
)

@PreviewsDayNight
@Composable
internal fun NyutagRoomSearchViewPreview(@PreviewParameter(NyutagRoomSearchStateProvider::class) state: NyutagRoomSearchState) = ElementPreview {
    NyutagRoomSearchView(state = state, onBackClick = {}, onResultClick = {})
}

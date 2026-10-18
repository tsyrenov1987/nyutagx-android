/*
 * Нютаг — вкладка «Контакты» на главной: телефонная книга, сверху те, кто уже в Нютаге.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun NyutagContactsView(
    state: NyutagContactsState,
    onRoomClick: (RoomId) -> Unit,
    onInviteClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    // Вкладку открыли — книга и сверка (без force сервер спрашиваем не чаще раза в час).
    LaunchedEffect(Unit) { state.eventSink(NyutagContactsEvent.Load(force = false)) }
    LazyColumn(modifier = modifier, state = lazyListState, contentPadding = contentPadding) {
        contacts(state, onInviteClick = onInviteClick)
    }
    AsyncActionView(
        async = state.startDmAction,
        onSuccess = onRoomClick,
        onErrorDismiss = { state.eventSink(NyutagContactsEvent.CancelStartDM) },
    )
}

private fun LazyListScope.contacts(state: NyutagContactsState, onInviteClick: () -> Unit) {
    if (!state.canReadContacts) {
        item { ContactsPermissionRow(onGranted = { state.eventSink(NyutagContactsEvent.Load(force = true)) }) }
        return
    }
    val inNyutag = state.inNyutag
    val others = state.others
    item { ListSectionHeader(title = stringResource(R.string.nyutag_contacts_on_nyutag), hasDivider = false) }
    when {
        inNyutag.isNotEmpty() -> items(inNyutag, key = { "n:" + it.user!!.userId.value }) { contact ->
            MatrixUserRow(
                modifier = Modifier.clickable { state.eventSink(NyutagContactsEvent.StartDM(contact)) },
                matrixUser = contact.user!!.copy(displayName = contact.name),
            )
        }
        state.contacts is AsyncData.Loading || state.contacts is AsyncData.Uninitialized || state.contacts is AsyncData.Failure -> item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.contacts !is AsyncData.Failure) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(
                        if (state.contacts is AsyncData.Failure) R.string.nyutag_contacts_error_network else R.string.nyutag_contacts_syncing
                    ),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
        else -> item {
            Text(
                text = stringResource(R.string.nyutag_contacts_empty),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
    if (others.isNotEmpty()) {
        item { ListSectionHeader(title = stringResource(R.string.nyutag_contacts_invite_section)) }
        items(others, key = { "p:" + it.phone }) { contact -> PhoneBookRow(contact, onClick = onInviteClick) }
    }
}

@Composable
private fun PhoneBookRow(contact: NyutagHomeContact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarData = AvatarData(id = contact.phone, name = contact.name, size = AvatarSize.UserListItem),
            avatarType = AvatarType.User,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = contact.phone,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        Icon(
            imageVector = CompoundIcons.UserAdd(),
            contentDescription = stringResource(R.string.nyutag_drawer_invite),
            tint = ElementTheme.colors.iconSecondary,
        )
    }
}

@Composable
private fun ContactsPermissionRow(onGranted: () -> Unit) {
    var denied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) onGranted()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = CompoundIcons.UserAdd(),
                tint = ElementTheme.colors.iconSecondary,
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.nyutag_contacts_allow),
                style = ElementTheme.typography.fontBodyLgRegular,
            )
        }
        Text(
            text = stringResource(if (denied) R.string.nyutag_contacts_denied else R.string.nyutag_contacts_allow_subtitle),
            style = ElementTheme.typography.fontBodySmRegular,
            color = if (denied) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 40.dp, top = 4.dp),
        )
    }
}

internal fun aNyutagContactsState(
    canReadContacts: Boolean = true,
    contacts: AsyncData<ImmutableList<NyutagHomeContact>> = AsyncData.Uninitialized,
) = NyutagContactsState(
    canReadContacts = canReadContacts,
    contacts = contacts,
    startDmAction = AsyncAction.Uninitialized,
    eventSink = {},
)

internal class NyutagContactsStateProvider : PreviewParameterProvider<NyutagContactsState> {
    override val values = sequenceOf(
        aNyutagContactsState(canReadContacts = false),
        aNyutagContactsState(contacts = AsyncData.Loading()),
        aNyutagContactsState(
            contacts = AsyncData.Success(
                persistentListOf(
                    NyutagHomeContact("Баир", "+7 900 111-22-33", MatrixUser(UserId("@bair:nyutag.chat"), "Bair")),
                    NyutagHomeContact("Аюна", "+7 900 555-66-77"),
                    NyutagHomeContact("Сэсэг", "8 900 111-00-00"),
                )
            )
        ),
        aNyutagContactsState(contacts = AsyncData.Success(persistentListOf(NyutagHomeContact("Аюна", "+7 900 555-66-77")))),
    )
}

@PreviewsDayNight
@Composable
internal fun NyutagContactsViewPreview(@PreviewParameter(NyutagContactsStateProvider::class) state: NyutagContactsState) = ElementPreview {
    NyutagContactsView(state = state, onRoomClick = {}, onInviteClick = {}, contentPadding = PaddingValues())
}

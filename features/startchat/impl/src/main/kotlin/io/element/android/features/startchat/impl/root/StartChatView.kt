/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.startchat.impl.root

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.startchat.api.ConfirmingStartDmWithMatrixUser
import io.element.android.features.startchat.impl.R
import io.element.android.features.startchat.impl.components.UserListView
import io.element.android.libraries.androidutils.ui.hideKeyboardAndAwaitAnimation
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.icons.CompoundDrawables
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.lazyColumnContentPadding
import io.element.android.libraries.designsystem.utils.scaffoldScrollableContentInsets
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.CreateDmConfirmationBottomSheet
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
fun StartChatView(
    state: StartChatState,
    onCloseClick: () -> Unit,
    onNewRoomClick: () -> Unit,
    onOpenDM: (RoomId) -> Unit,
    onInviteFriendsClick: () -> Unit,
    onJoinByAddressClick: () -> Unit,
    onRoomDirectorySearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFindByPhoneClick: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxWidth(),
        topBar = {
            if (!state.userListState.isSearchActive) {
                CreateRoomRootViewTopBar(onCloseClick = onCloseClick)
            }
        },
        contentWindowInsets = scaffoldScrollableContentInsets,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val view = LocalView.current

            UserListView(
                modifier = Modifier.fillMaxWidth(),
                // Do not render suggestions in this case, the suggestion will be rendered
                // by CreateRoomActionButtonsList
                state = state.userListState.copy(
                    recentDirectRooms = persistentListOf(),
                ),
                onSelectUser = {
                    coroutineScope.launch {
                        view.hideKeyboardAndAwaitAnimation()
                        state.eventSink(StartChatEvent.StartDM(it))
                    }
                },
                onDeselectUser = { },
            )

            if (!state.userListState.isSearchActive) {
                CreateRoomActionButtonsList(
                    state = state,
                    onNewRoomClick = onNewRoomClick,
                    onInvitePeopleClick = onInviteFriendsClick,
                    onJoinByAddressClick = onJoinByAddressClick,
                    onRoomDirectorySearchClick = onRoomDirectorySearchClick,
                    onFindByPhoneClick = onFindByPhoneClick,
                    onDmClick = onOpenDM,
                )
            }
        }
    }

    AsyncActionView(
        async = state.startDmAction,
        progressDialog = {
            AsyncActionViewDefaults.ProgressDialog(
                progressText = stringResource(CommonStrings.common_starting_chat),
            )
        },
        onSuccess = { onOpenDM(it) },
        errorMessage = { stringResource(R.string.screen_start_chat_error_starting_chat) },
        onRetry = {
            state.userListState.selectedUsers.firstOrNull()
                ?.let { state.eventSink(StartChatEvent.StartDM(it)) }
            // Cancel start DM if there is no more selected user (should not happen)
                ?: state.eventSink(StartChatEvent.CancelStartDM)
        },
        onErrorDismiss = { state.eventSink(StartChatEvent.CancelStartDM) },
        confirmationDialog = { data ->
            if (data is ConfirmingStartDmWithMatrixUser) {
                CreateDmConfirmationBottomSheet(
                    matrixUser = data.matrixUser,
                    isUserIdentityUnknown = data.isUserIdentityUnknown,
                    onSendInvite = {
                        state.eventSink(StartChatEvent.StartDM(data.matrixUser))
                    },
                    onDismiss = {
                        state.eventSink(StartChatEvent.CancelStartDM)
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRoomRootViewTopBar(
    onCloseClick: () -> Unit,
) {
    TopAppBar(
        titleStr = stringResource(id = CommonStrings.action_start_chat),
        navigationIcon = {
            BackButton(
                imageVector = CompoundIcons.Close(),
                onClick = onCloseClick,
            )
        }
    )
}

@Composable
private fun CreateRoomActionButtonsList(
    state: StartChatState,
    onNewRoomClick: () -> Unit,
    onInvitePeopleClick: () -> Unit,
    onJoinByAddressClick: () -> Unit,
    onRoomDirectorySearchClick: () -> Unit,
    onFindByPhoneClick: () -> Unit,
    onDmClick: (RoomId) -> Unit,
) {
    LazyColumn(
        contentPadding = lazyColumnContentPadding,
    ) {
        item {
            // Нютаг: найти знакомого по номеру; нет в Нютаге — пригласить ссылкой, QR или SMS.
            CreateRoomActionButton(
                iconRes = CompoundDrawables.ic_compound_mobile,
                text = stringResource(R.string.nyutag_by_phone_title),
                onClick = onFindByPhoneClick,
            )
        }
        item {
            val name = stringResource(CommonStrings.nyutag_saved_messages)
            val topic = stringResource(CommonStrings.nyutag_saved_messages_topic)
            CreateRoomActionButton(
                iconRes = CompoundDrawables.ic_compound_favourite,
                text = name,
                onClick = { state.eventSink(StartChatEvent.OpenSavedMessages(name, topic)) },
            )
        }
        item {
            CreateRoomActionButton(
                iconRes = CompoundDrawables.ic_compound_plus,
                text = stringResource(id = R.string.screen_create_room_action_create_room),
                onClick = onNewRoomClick,
            )
        }
        item {
            CreateRoomActionButton(
                iconRes = CompoundDrawables.ic_compound_list_bulleted,
                text = stringResource(id = R.string.screen_room_directory_search_title),
                onClick = onRoomDirectorySearchClick,
            )
        }
        item {
            CreateRoomActionButton(
                iconRes = CompoundDrawables.ic_compound_share_android,
                text = stringResource(id = CommonStrings.action_invite_friends_to_app, state.applicationName),
                onClick = onInvitePeopleClick,
            )
        }
        item {
            CreateRoomActionButton(
                iconRes = CompoundDrawables.ic_compound_room,
                text = stringResource(R.string.screen_start_chat_join_room_by_address_action),
                onClick = onJoinByAddressClick,
            )
        }
        nyutagContacts(state, onContactClick = { state.eventSink(StartChatEvent.StartDMWithContact(it)) })
        if (state.userListState.recentDirectRooms.isNotEmpty()) {
            item {
                ListSectionHeader(
                    title = stringResource(id = CommonStrings.common_suggestions),
                    hasDivider = false,
                )
            }
            state.userListState.recentDirectRooms.forEach { recentDirectRoom ->
                item {
                    MatrixUserRow(
                        modifier = Modifier.clickable(
                            onClick = {
                                onDmClick(recentDirectRoom.roomId)
                            }
                        ),
                        matrixUser = recentDirectRoom.matrixUser,
                    )
                }
            }
        }
    }
}

/** Нютаг: кто из записной книжки уже здесь. Без разрешения — строка «Найти знакомых из контактов». */
private fun LazyListScope.nyutagContacts(state: StartChatState, onContactClick: (MatrixUser) -> Unit) {
    if (!state.canReadContacts) {
        item { NyutagContactsPermissionRow(onGranted = { state.eventSink(StartChatEvent.SyncContacts(force = true)) }) }
        return
    }
    item {
        ListSectionHeader(title = stringResource(R.string.nyutag_contacts_section), hasDivider = false)
    }
    val contacts = state.nyutagContacts.dataOrNull()
    when {
        contacts == null -> item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.nyutagContacts is AsyncData.Loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(
                        if (state.nyutagContacts is AsyncData.Failure) R.string.nyutag_by_phone_error_network else R.string.nyutag_contacts_syncing
                    ),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
        contacts.isEmpty() -> item {
            Text(
                text = stringResource(R.string.nyutag_contacts_empty),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        else -> items(contacts, key = { it.user.userId.value }) { contact ->
            // Имя — как записано в книжке; аватар — из профиля в Нютаге.
            val user = contact.user.copy(displayName = contact.name)
            MatrixUserRow(
                modifier = Modifier.clickable { onContactClick(user) },
                matrixUser = user,
            )
        }
    }
}

@Composable
internal fun NyutagContactsPermissionRow(onGranted: () -> Unit) {
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
                tint = ElementTheme.colors.iconSecondary,
                resourceId = CompoundDrawables.ic_compound_user_add,
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

@Composable
private fun CreateRoomActionButton(
    @DrawableRes iconRes: Int,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            tint = ElementTheme.colors.iconSecondary,
            resourceId = iconRes,
            contentDescription = null,
        )
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyLgRegular,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun StartChatViewPreview(@PreviewParameter(StartChatStatePreviewParam::class) state: StartChatState) =
    ElementPreview {
        StartChatView(
            state = state,
            onCloseClick = {},
            onNewRoomClick = {},
            onOpenDM = {},
            onJoinByAddressClick = {},
            onInviteFriendsClick = {},
            onRoomDirectorySearchClick = {},
        )
    }

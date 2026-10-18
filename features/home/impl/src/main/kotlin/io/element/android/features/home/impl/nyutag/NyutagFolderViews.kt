/*
 * Нютаг — вкладки папок над фильтрами и окна «Добавить в папку» / «Папка», как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.nyutag

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings

/** Вкладки «Все | папки…»; долгое нажатие на папку — переименовать или удалить. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NyutagFolderTabs(
    folders: List<NyutagChatFolder>,
    selectedFolderId: String?,
    eventSink: (RoomListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var actionsFor by remember { mutableStateOf<NyutagChatFolder?>(null) }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        item(key = "all") {
            FolderTab(
                title = stringResource(R.string.nyutag_folder_all),
                selected = selectedFolderId == null,
                onClick = { eventSink(RoomListEvent.SelectFolder(null)) },
                onLongClick = null,
            )
        }
        items(folders, key = { it.id }) { folder ->
            FolderTab(
                title = folder.name,
                selected = folder.id == selectedFolderId,
                onClick = { eventSink(RoomListEvent.SelectFolder(folder.id)) },
                onLongClick = { actionsFor = folder },
            )
        }
    }
    actionsFor?.let { folder ->
        NyutagFolderActionsSheet(folder = folder, eventSink = eventSink, onDismiss = { actionsFor = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = if (selected) ElementTheme.typography.fontBodyMdMedium else ElementTheme.typography.fontBodyMdRegular,
            color = if (selected) ElementTheme.colors.textActionAccent else ElementTheme.colors.textSecondary,
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(24.dp)
                .height(3.dp)
                .background(
                    color = if (selected) ElementTheme.colors.textActionAccent else Color.Transparent,
                    shape = RoundedCornerShape(2.dp),
                )
        )
    }
}

/** «Добавить в папку»: галочки по папкам и «Новая папка» с этим чатом внутри. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NyutagFolderPickerSheet(
    roomId: RoomId,
    folders: List<NyutagChatFolder>,
    eventSink: (RoomListEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var askName by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                content = { Text(text = stringResource(R.string.nyutag_folder_add), style = ElementTheme.typography.fontBodyLgMedium) },
            )
            folders.forEach { folder ->
                val inFolder = roomId.value in folder.rooms
                ListItem(
                    content = { Text(text = folder.name) },
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Folder())),
                    trailingContent = ListItemContent.Checkbox(checked = inFolder),
                    onClick = { eventSink(RoomListEvent.SetRoomInFolder(folder.id, roomId, !inFolder)) },
                )
            }
            ListItem(
                content = { Text(text = stringResource(R.string.nyutag_folder_new)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
                style = ListItemStyle.Primary,
                onClick = { askName = true },
            )
        }
    }
    if (askName) {
        FolderNameDialog(
            title = stringResource(R.string.nyutag_folder_new),
            initial = null,
            onSubmit = { name ->
                askName = false
                eventSink(RoomListEvent.CreateFolder(name, roomId))
            },
            onDismiss = { askName = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NyutagFolderActionsSheet(
    folder: NyutagChatFolder,
    eventSink: (RoomListEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    if (!rename && !delete) {
        ModalBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(content = { Text(text = folder.name, style = ElementTheme.typography.fontBodyLgMedium) })
                ListItem(
                    content = { Text(text = stringResource(R.string.nyutag_folder_rename)) },
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Edit())),
                    onClick = { rename = true },
                )
                ListItem(
                    content = { Text(text = stringResource(R.string.nyutag_folder_delete)) },
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Delete())),
                    style = ListItemStyle.Destructive,
                    onClick = { delete = true },
                )
            }
        }
    }
    if (rename) {
        FolderNameDialog(
            title = stringResource(R.string.nyutag_folder_rename),
            initial = folder.name,
            onSubmit = { name ->
                eventSink(RoomListEvent.RenameFolder(folder.id, name))
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
    if (delete) {
        ConfirmationDialog(
            title = stringResource(R.string.nyutag_folder_delete),
            content = stringResource(R.string.nyutag_folder_delete_hint),
            submitText = stringResource(CommonStrings.action_delete),
            destructiveSubmit = true,
            onSubmitClick = {
                eventSink(RoomListEvent.DeleteFolder(folder.id))
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initial: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    TextFieldDialog(
        title = title,
        value = initial,
        placeholder = stringResource(R.string.nyutag_folder_name),
        validation = { !it.isNullOrBlank() },
        onSubmit = onSubmit,
        onDismissRequest = onDismiss,
    )
}

@PreviewsDayNight
@Composable
internal fun NyutagFolderTabsPreview() = ElementPreview {
    NyutagFolderTabs(
        folders = listOf(
            NyutagChatFolder(id = "1", name = "Семья"),
            NyutagChatFolder(id = "2", name = "Работа"),
        ),
        selectedFolderId = "1",
        eventSink = {},
    )
}

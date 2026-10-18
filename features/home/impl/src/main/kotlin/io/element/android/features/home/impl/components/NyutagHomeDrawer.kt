/*
 * Нютаг — боковое меню главной, как в Telegram для Android: открывается тапом по своему аватару.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.aMatrixUser
import io.element.android.libraries.matrix.ui.model.getAvatarData

/** Пункты бокового меню. */
enum class NyutagDrawerAction {
    MyProfile,
    OwnAvatar,
    NewGroup,
    Contacts,
    SavedMessages,
    Settings,
    Invite,
}

@Composable
fun NyutagHomeDrawer(
    currentUser: MatrixUser,
    onAction: (NyutagDrawerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = ElementTheme.colors.bgCanvasDefault,
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Avatar(
                    avatarData = currentUser.getAvatarData(size = AvatarSize.EditProfileDetails),
                    avatarType = AvatarType.User,
                    forcedAvatarSize = 72.dp,
                    contentDescription = currentUser.displayName,
                    // Свой аватар во весь экран — только если он есть, иначе сразу в профиль, где его ставят.
                    modifier = Modifier.clickable {
                        onAction(if (currentUser.avatarUrl != null) NyutagDrawerAction.OwnAvatar else NyutagDrawerAction.MyProfile)
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = currentUser.displayName?.takeIf { it.isNotBlank() } ?: currentUser.userId.value,
                    style = ElementTheme.typography.fontBodyLgMedium,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currentUser.userId.value,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            DrawerItem(CompoundIcons.UserProfile(), R.string.nyutag_drawer_my_profile) { onAction(NyutagDrawerAction.MyProfile) }
            DrawerItem(CompoundIcons.Group(), R.string.nyutag_drawer_new_group) { onAction(NyutagDrawerAction.NewGroup) }
            DrawerItem(CompoundIcons.User(), R.string.nyutag_drawer_contacts) { onAction(NyutagDrawerAction.Contacts) }
            DrawerItem(CompoundIcons.Favourite(), R.string.nyutag_drawer_saved_messages) { onAction(NyutagDrawerAction.SavedMessages) }
            DrawerItem(CompoundIcons.Settings(), R.string.nyutag_drawer_settings) { onAction(NyutagDrawerAction.Settings) }
            DrawerItem(CompoundIcons.UserAdd(), R.string.nyutag_drawer_invite) { onAction(NyutagDrawerAction.Invite) }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: Int, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(text = stringResource(label), style = ElementTheme.typography.fontBodyLgRegular) },
        icon = { Icon(imageVector = icon, contentDescription = null, tint = ElementTheme.colors.iconSecondary) },
        selected = false,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = ElementTheme.colors.bgCanvasDefault,
            unselectedTextColor = ElementTheme.colors.textPrimary,
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@PreviewsDayNight
@Composable
internal fun NyutagHomeDrawerPreview() = ElementPreview {
    NyutagHomeDrawer(currentUser = aMatrixUser(displayName = "Баир"), onAction = {})
}

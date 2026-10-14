/*
 * Нютаг (форк Element X Android) — страница сообщества: обложка, кнопки действий, заголовок списка, пустое состояние.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.space.impl.root

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

/** Обложка в фирменных цветах за аватаром сообщества: полоса до середины аватара, как во ВКонтакте/Telegram. */
@Composable
internal fun NyutagCommunityCover(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clipToBounds()
            .background(
                Brush.linearGradient(
                    listOf(ElementTheme.colors.bgAccentRest, ElementTheme.colors.bgAccentPressed)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 40.dp, y = 50.dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        )
    }
}

/** Ряд круглых кнопок под шапкой: пригласить, участники, настройки (админу), выйти. */
@Composable
internal fun NyutagCommunityActions(
    canAccessSettings: Boolean,
    onInviteClick: () -> Unit,
    onViewMembersClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLeaveSpaceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NyutagCommunityAction(stringResource(CommonStrings.nyutag_community_invite), CompoundIcons.UserAdd(), onClick = onInviteClick)
        NyutagCommunityAction(stringResource(CommonStrings.nyutag_community_members), CompoundIcons.User(), onClick = onViewMembersClick)
        if (canAccessSettings) {
            NyutagCommunityAction(stringResource(CommonStrings.common_settings), CompoundIcons.Settings(), onClick = onSettingsClick)
        }
        NyutagCommunityAction(
            stringResource(CommonStrings.nyutag_community_leave),
            CompoundIcons.Leave(),
            isDestructive = true,
            onClick = onLeaveSpaceClick,
        )
    }
}

@Composable
private fun RowScope.NyutagCommunityAction(
    title: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ElementTheme.colors.bgSubtleSecondary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) ElementTheme.colors.iconCriticalPrimary else ElementTheme.colors.iconAccentPrimary,
            )
        }
        Text(
            text = title,
            style = ElementTheme.typography.fontBodySmRegular,
            color = if (isDestructive) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «Группы · 3» над списком. */
@Composable
internal fun NyutagCommunitySectionHeader(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(CommonStrings.nyutag_community_groups) + " · " + count,
        style = ElementTheme.typography.fontBodySmMedium,
        color = ElementTheme.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** Пустое сообщество для обычного участника: без кнопок админа, с понятной подсказкой. */
@Composable
internal fun NyutagCommunityMemberEmptyState(modifier: Modifier = Modifier) {
    IconTitleSubtitleMolecule(
        title = stringResource(CommonStrings.nyutag_community_empty_title),
        subTitle = stringResource(CommonStrings.nyutag_community_empty_subtitle),
        iconStyle = BigIcon.Style.Default(vectorIcon = CompoundIcons.Room(), usePrimaryTint = true),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
    )
}

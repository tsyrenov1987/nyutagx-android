/*
 * Нютаг (форк Element X) — отметки у своих сообщений, как в Telegram: 🕓 отправляется, ✓ отправлено, ✓✓ прочитано.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.ui.strings.CommonStrings

/** Свои сообщения, которые кто-то другой уже прочитал (считает [io.element.android.features.messages.impl.timeline.TimelineState]). */
val LocalNyutagReadOutgoingIds = staticCompositionLocalOf<Set<UniqueId>> { emptySet() }

/** В личном чате прочтение показывают ✓✓ в пузыре, ряд аватарок под сообщениями не нужен. */
val LocalNyutagIsDm = staticCompositionLocalOf { false }

@Composable
internal fun NyutagDeliveryMarks(event: TimelineItem.Event) {
    val tint = ElementTheme.colors.iconSecondary
    when {
        event.localSendState is LocalEventSendState.Sending -> Icon(
            imageVector = CompoundIcons.Time(),
            contentDescription = stringResource(CommonStrings.common_sending),
            tint = tint,
            modifier = Modifier.padding(start = 2.dp).size(14.dp),
        )
        event.id in LocalNyutagReadOutgoingIds.current -> Box(modifier = Modifier.padding(start = 2.dp).size(width = 20.dp, height = 14.dp)) {
            val accent = ElementTheme.colors.iconAccentPrimary
            Icon(imageVector = CompoundIcons.Check(), contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Icon(
                imageVector = CompoundIcons.Check(),
                contentDescription = stringResource(CommonStrings.nyutag_message_read),
                tint = accent,
                modifier = Modifier.padding(start = 5.dp).size(14.dp),
            )
        }
        else -> Icon(
            imageVector = CompoundIcons.Check(),
            contentDescription = stringResource(CommonStrings.common_sent),
            tint = tint,
            modifier = Modifier.padding(start = 2.dp).size(14.dp),
        )
    }
}

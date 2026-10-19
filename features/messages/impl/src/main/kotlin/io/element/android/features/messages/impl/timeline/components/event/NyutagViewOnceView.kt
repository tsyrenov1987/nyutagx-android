/*
 * Нютаг (форк Element X) — исчезающее фото: заглушка в ленте и одноразовый просмотр без скриншотов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.model.event.NyutagViewOnceState
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemImageContent
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun NyutagViewOnceView(
    content: TimelineItemImageContent,
    onLongClick: (() -> Unit)?,
    onOpened: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isReceived = content.nyutagViewOnce == NyutagViewOnceState.Received
    // Переживает пересоздание экрана, чтобы фото нельзя было открыть второй раз, пока удаление не дошло.
    var isOpened by rememberSaveable { mutableStateOf(false) }
    var isViewing by rememberSaveable { mutableStateOf(false) }
    val label = when {
        !isReceived -> stringResource(CommonStrings.nyutag_view_once_sent)
        isOpened -> stringResource(CommonStrings.nyutag_view_once_opened)
        else -> stringResource(CommonStrings.nyutag_view_once_received)
    }
    Row(
        modifier = modifier
            .combinedClickable(
                enabled = isReceived && !isOpened || onLongClick != null,
                onClick = { if (isReceived && !isOpened) isViewing = true },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.Time(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
        )
        Text(
            text = label,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
        )
    }
    if (isViewing) {
        NyutagViewOnceViewer(
            content = content,
            onDismiss = {
                isViewing = false
                isOpened = true
                onOpened()
            },
        )
    }
}

@Composable
private fun NyutagViewOnceViewer(
    content: TimelineItemImageContent,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // Запрет скриншотов и записи экрана — на окне самого диалога (поиск окна через LocalView.parent давал null).
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = MediaRequestData(
                    source = content.mediaSource,
                    kind = MediaRequestData.Kind.File(fileName = content.filename, mimeType = content.mimeType),
                ),
                contentScale = ContentScale.Fit,
                contentDescription = stringResource(CommonStrings.common_image),
            )
        }
    }
}

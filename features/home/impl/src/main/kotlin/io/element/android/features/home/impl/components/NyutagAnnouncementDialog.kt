/*
 * Нютаг (форк Element X) — окно с обращением разработчика ко всем пользователям.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.home.impl.HomeEvent
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.nyutag.NyutagAnnouncement
import io.element.android.libraries.ui.strings.CommonStrings

/** Заголовок, текст и кнопки приходят с ядра (`announce.json`); закрыть можно только кнопкой. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NyutagAnnouncementDialog(
    announcement: NyutagAnnouncement,
    eventSink: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    BasicAlertDialog(onDismissRequest = {}, modifier = modifier) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = ElementTheme.colors.bgCanvasDefault) {
            Column(modifier = Modifier.padding(24.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = announcement.title,
                        style = ElementTheme.typography.fontHeadingMdBold,
                        color = ElementTheme.colors.textPrimary,
                    )
                    Text(
                        text = announcement.body,
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                announcement.actions.forEach { action ->
                    Button(
                        text = action.label,
                        onClick = {
                            eventSink(HomeEvent.NyutagAnnouncementAction(action))
                            action.url?.let { runCatching { uriHandler.openUri(it) } }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }
                TextButton(
                    text = announcement.dismissLabel ?: stringResource(CommonStrings.action_not_now),
                    onClick = { eventSink(HomeEvent.NyutagAnnouncementDismiss) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

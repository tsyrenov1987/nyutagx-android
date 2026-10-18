/*
 * Нютаг — таймер разговора поверх Element Call, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Сколько длится разговор, начиная с [joinedAtMillis] (эпоха, мс); обновляется раз в секунду.
 * Если задан [endedAtMillis], разговор окончен — показываем итог и больше не считаем.
 */
@Composable
internal fun NyutagCallDuration(
    joinedAtMillis: Long,
    modifier: Modifier = Modifier,
    endedAtMillis: Long? = null,
) {
    var seconds by remember(joinedAtMillis, endedAtMillis) { mutableLongStateOf(elapsedSeconds(joinedAtMillis, endedAtMillis)) }
    LaunchedEffect(joinedAtMillis, endedAtMillis) {
        while (endedAtMillis == null) {
            delay(1_000L - (System.currentTimeMillis() - joinedAtMillis).mod(1_000L))
            seconds = elapsedSeconds(joinedAtMillis, null)
        }
    }
    Text(
        text = formatCallDuration(seconds),
        color = Color.White,
        style = ElementTheme.typography.fontBodyMdMedium,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

internal fun elapsedSeconds(joinedAtMillis: Long, endedAtMillis: Long?): Long =
    (((endedAtMillis ?: System.currentTimeMillis()) - joinedAtMillis) / 1_000L).coerceAtLeast(0L)

/** 0:05, 12:34, 1:02:03 — как в Telegram. */
internal fun formatCallDuration(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val rest = seconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, rest)
    }
}

@PreviewsDayNight
@Composable
internal fun NyutagCallDurationPreview() = ElementPreview {
    NyutagCallDuration(joinedAtMillis = System.currentTimeMillis() - 754_000L)
}

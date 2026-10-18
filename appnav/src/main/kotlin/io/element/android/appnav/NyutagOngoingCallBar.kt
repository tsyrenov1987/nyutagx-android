/*
 * Нютаг — полоса идущего звонка внутри приложения, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.appnav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.call.api.NyutagOngoingCall
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Нютаг: зелёная полоса над приложением, пока звонок свёрнут, — имя и время разговора, микрофон и «Завершить»;
 * тап по полосе возвращает к звонку. Отступ под строку состояния полоса берёт на себя.
 */
@Composable
internal fun NyutagOngoingCallBarContainer(
    ongoingCall: NyutagOngoingCall?,
    onReturnToCall: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit = {},
) {
    // Последний звонок — чтобы уезжающая полоса не опустела.
    var lastCall by remember { mutableStateOf(ongoingCall) }
    SideEffect {
        if (ongoingCall != null) lastCall = ongoingCall
    }
    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = ongoingCall != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            (ongoingCall ?: lastCall)?.let { call ->
                NyutagOngoingCallBar(
                    call = call,
                    onReturnToCall = onReturnToCall,
                    onToggleMicrophone = onToggleMicrophone,
                    onHangUp = onHangUp,
                )
            }
        }
        content(if (ongoingCall != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)
    }
}

@Composable
private fun NyutagOngoingCallBar(
    call: NyutagOngoingCall,
    onReturnToCall: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onHangUp: () -> Unit,
) {
    val contentColor = ElementTheme.colors.iconOnSolidPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.iconSuccessPrimary)
            .windowInsetsPadding(WindowInsets.statusBars)
            .clickable(onClickLabel = stringResource(CommonStrings.nyutag_call_bar_return), onClick = onReturnToCall)
            .heightIn(min = 44.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.VoiceCallSolid(),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = listOfNotNull(call.title, rememberCallStatusText(call)).joinToString(" · "),
            color = ElementTheme.colors.textOnSolidPrimary,
            style = ElementTheme.typography.fontBodyMdMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleMicrophone) {
            Icon(
                imageVector = if (call.isMicrophoneEnabled) CompoundIcons.MicOn() else CompoundIcons.MicOff(),
                contentDescription = stringResource(if (call.isMicrophoneEnabled) CommonStrings.common_mute else CommonStrings.common_unmute),
                tint = contentColor,
            )
        }
        IconButton(onClick = onHangUp) {
            Icon(
                imageVector = CompoundIcons.EndCall(),
                contentDescription = stringResource(CommonStrings.nyutag_call_bar_end),
                tint = contentColor,
            )
        }
    }
}

/** «Вызов…», пока собеседник не ответил, потом время разговора; когда разговор кончился — замирает. */
@Composable
private fun rememberCallStatusText(call: NyutagOngoingCall): String {
    val joinedAtMillis = call.joinedAtMillis ?: return stringResource(CommonStrings.nyutag_call_bar_calling)
    val endedAtMillis = call.endedAtMillis
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(joinedAtMillis, endedAtMillis) {
        while (endedAtMillis == null) {
            now = System.currentTimeMillis()
            delay(1_000L - (now - joinedAtMillis).mod(1_000L))
        }
    }
    val seconds = (((endedAtMillis ?: now) - joinedAtMillis) / 1_000L).coerceAtLeast(0L)
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
internal fun NyutagOngoingCallBarContainerPreview() = ElementPreview {
    NyutagOngoingCallBarContainer(
        ongoingCall = NyutagOngoingCall(
            roomId = RoomId("!room:nyutag.chat"),
            title = "Эржена",
            joinedAtMillis = 0L,
            endedAtMillis = 754_000L,
            isMicrophoneEnabled = true,
        ),
        onReturnToCall = {},
        onToggleMicrophone = {},
        onHangUp = {},
    )
}

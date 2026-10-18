/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.call.impl.R
import io.element.android.features.call.impl.notifications.CallNotificationData
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

// Нютаг: экран входящего — как у обычного звонка по сотовой сети: всегда тёмный, имя сверху, «Отклонить» слева, «Ответить» справа.
private val CallBackgroundTop = Color(0xFF1E2A38)
private val CallBackgroundBottom = Color(0xFF0B1016)
private val CallTextSecondary = Color.White.copy(alpha = 0.7f)
private val CallAnswerColor = Color(0xFF2FB560)
private val CallDeclineColor = Color(0xFFE5483D)

@Composable
internal fun IncomingCallScreen(
    notificationData: CallNotificationData,
    onAnswer: (CallNotificationData) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CallBackgroundTop, CallBackgroundBottom)))
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        Text(
            text = notificationData.senderName ?: notificationData.senderId.value,
            style = ElementTheme.typography.fontHeadingXlRegular,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.screen_incoming_call_subtitle_android),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = CallTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            RingingAvatar(notificationData)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, bottom = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CallActionButton(
                onClick = onCancel,
                icon = CompoundIcons.EndCall(),
                title = stringResource(CommonStrings.action_reject),
                backgroundColor = CallDeclineColor,
            )
            CallActionButton(
                onClick = { onAnswer(notificationData) },
                icon = if (notificationData.audioOnly) CompoundIcons.VoiceCallSolid() else CompoundIcons.VideoCallSolid(),
                title = stringResource(CommonStrings.nyutag_incoming_call_answer),
                backgroundColor = CallAnswerColor,
                pulse = true,
            )
        }
    }
}

/** Аватар с расходящимися кругами, пока звонок звонит. */
@Composable
private fun RingingAvatar(notificationData: CallNotificationData) {
    val transition = rememberInfiniteTransition(label = "ringing")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2_000, easing = LinearEasing), RepeatMode.Restart),
        label = "ringingWaves",
    )
    Box(
        modifier = Modifier.drawBehind {
            val base = size.minDimension / 2
            for (wave in 0..1) {
                val phase = (progress + wave * 0.5f) % 1f
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f * (1f - phase)),
                    radius = base * (1f + 0.5f * phase),
                )
            }
        },
    ) {
        Avatar(
            avatarData = AvatarData(
                id = notificationData.senderId.value,
                name = notificationData.senderName,
                url = notificationData.avatarUrl,
                size = AvatarSize.IncomingCall,
            ),
            avatarType = AvatarType.User,
        )
    }
}

@Composable
private fun CallActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    title: String,
    backgroundColor: Color,
    pulse: Boolean = false,
) {
    val scale = if (pulse) {
        val transition = rememberInfiniteTransition(label = "answerPulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
            label = "answerPulseScale",
        )
        value
    } else {
        1f
    }
    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledIconButton(
            modifier = Modifier
                .size(72.dp)
                .scale(scale),
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = backgroundColor,
                contentColor = Color.White,
            )
        ) {
            Icon(
                modifier = Modifier.size(32.dp),
                imageVector = icon,
                contentDescription = title,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun IncomingCallScreenPreview(
    @PreviewParameter(CallNotificationDataPreviewParam::class) state: CallNotificationData,
) = ElementPreview {
    IncomingCallScreen(
        notificationData = state,
        onAnswer = {},
        onCancel = {},
    )
}

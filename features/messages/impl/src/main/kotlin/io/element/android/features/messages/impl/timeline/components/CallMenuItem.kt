/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.roomcall.api.RoomCallState
import io.element.android.features.roomcall.api.RoomCallStatePreviewParam
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
internal fun CallMenuItem(
    roomCallState: RoomCallState,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (roomCallState) {
        RoomCallState.Unavailable -> {
            Box(modifier)
        }
        is RoomCallState.StandBy -> {
            StandByCallMenuItem(
                roomCallState = roomCallState,
                onJoinCallClick = onJoinCallClick,
                modifier = modifier,
            )
        }
        is RoomCallState.OnGoing -> {
            OnGoingCallMenuItem(
                roomCallState = roomCallState,
                onJoinCallClick = { onJoinCallClick(roomCallState.isAudioCall) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun StandByCallMenuItem(
    roomCallState: RoomCallState.StandBy,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        // Only show voice call in DMs
        if (roomCallState.isDM) {
            IconButton(
                onClick = { onJoinCallClick(true) },
                enabled = roomCallState.canStartCall,
            ) {
                Icon(
                    imageVector = CompoundIcons.VoiceCallSolid(),
                    contentDescription = stringResource(CommonStrings.a11y_start_voice_call),
                )
            }
        }
        IconButton(
            onClick = { onJoinCallClick(false) },
            enabled = roomCallState.canStartCall,
        ) {
            Icon(
                imageVector = CompoundIcons.VideoCallSolid(),
                contentDescription = stringResource(CommonStrings.a11y_start_call),
            )
        }
    }
}

@Composable
private fun OnGoingCallMenuItem(
    roomCallState: RoomCallState.OnGoing,
    onJoinCallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Нютаг: без пилюли «Присоединиться» — идущий звонок выглядит как обычная кнопка звонка.
    if (!roomCallState.isUserLocallyInTheCall) {
        IconButton(
            onClick = onJoinCallClick,
            enabled = roomCallState.canJoinCall,
            modifier = modifier,
        ) {
            Icon(
                imageVector = if (roomCallState.isAudioCall) {
                    CompoundIcons.VoiceCallSolid()
                } else {
                    CompoundIcons.VideoCallSolid()
                },
                contentDescription = stringResource(CommonStrings.a11y_join_call),
            )
        }
    } else {
        Box(modifier)
    }
}

@PreviewsDayNight
@Composable
internal fun CallMenuItemPreview(
    @PreviewParameter(RoomCallStatePreviewParam::class) roomCallState: RoomCallState
) = ElementPreview {
    CallMenuItem(
        roomCallState = roomCallState,
        onJoinCallClick = {}
    )
}

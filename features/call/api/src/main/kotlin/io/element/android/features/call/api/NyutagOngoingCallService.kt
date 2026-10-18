/*
 * Нютаг — полоса идущего звонка внутри приложения, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.api

import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.StateFlow

/**
 * Нютаг: идущий звонок для полосы «вернуться к звонку» внутри приложения, как в Telegram.
 * Нужна, когда звонок свёрнут, а системное окно поверх (PiP) запрещено — на Xiaomi и HONOR так часто бывает.
 */
interface NyutagOngoingCallService {
    /** Идущий звонок или null, если звонка нет. */
    val ongoingCall: StateFlow<NyutagOngoingCall?>

    /** Открыть экран звонка. */
    fun returnToCall()

    /** Выключить или включить микрофон. */
    fun toggleMicrophone()

    /** Положить трубку. */
    fun hangUp()
}

data class NyutagOngoingCall(
    val roomId: RoomId,
    /** Имя собеседника или группы. */
    val title: String?,
    /** Когда собеседник ответил (эпоха, мс); null, пока идёт вызов. */
    val joinedAtMillis: Long?,
    /** Когда разговор кончился; таймер на этом месте замирает. */
    val endedAtMillis: Long?,
    val isMicrophoneEnabled: Boolean,
)

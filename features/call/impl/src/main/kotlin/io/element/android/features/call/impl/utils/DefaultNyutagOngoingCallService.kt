/*
 * Нютаг — полоса идущего звонка внутри приложения, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.impl.utils

import android.content.Context
import android.content.Intent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.features.call.api.NyutagOngoingCall
import io.element.android.features.call.api.NyutagOngoingCallService
import io.element.android.features.call.impl.ui.ElementCallActivity
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.appnavstate.api.AppForegroundStateService
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

/** Нютаг: команды экрану звонка — от кнопок свёрнутого окна (PiP) и полосы в приложении. */
internal object NyutagCallActions {
    const val HANG_UP = "chat.nyutag.messenger.call.PIP_HANG_UP"
    const val TOGGLE_MICROPHONE = "chat.nyutag.messenger.call.PIP_TOGGLE_MICROPHONE"
    const val TOGGLE_SPEAKER = "chat.nyutag.messenger.call.PIP_TOGGLE_SPEAKER"
}

/** Нютаг: экран звонка сообщает сюда, что звонок идёт (или кончился), — для полосы в приложении. */
fun interface NyutagOngoingCallPublisher {
    fun publish(call: NyutagOngoingCall?)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<NyutagOngoingCallService>())
@ContributesBinding(AppScope::class, binding = binding<NyutagOngoingCallPublisher>())
class DefaultNyutagOngoingCallService(
    @ApplicationContext private val context: Context,
    @AppCoroutineScope coroutineScope: CoroutineScope,
    dispatchers: CoroutineDispatchers,
    appForegroundStateService: AppForegroundStateService,
    private val stringProvider: StringProvider,
) : NyutagOngoingCallService, NyutagOngoingCallPublisher {
    private val state = MutableStateFlow<NyutagOngoingCall?>(null)
    override val ongoingCall: StateFlow<NyutagOngoingCall?> = state.asStateFlow()

    // Нютаг: свернули Нютаг посреди звонка — плашка звонка поверх других приложений (в приложении её заменяет полоса).
    private val overlay = NyutagCallOverlay(
        context = context,
        onReturnToCall = ::returnToCall,
        onToggleMicrophone = ::toggleMicrophone,
        onHangUp = ::hangUp,
    )

    init {
        combine(state, appForegroundStateService.isInForeground) { call, isInForeground -> call.takeUnless { isInForeground } }
            .onEach { call -> overlay.update(call, ::overlayLabels) }
            .flowOn(dispatchers.main)
            .launchIn(coroutineScope)
    }

    private fun overlayLabels() = NyutagCallOverlayLabels(
        calling = stringProvider.getString(CommonStrings.nyutag_call_bar_calling),
        mute = stringProvider.getString(CommonStrings.common_mute),
        unmute = stringProvider.getString(CommonStrings.common_unmute),
        end = stringProvider.getString(CommonStrings.nyutag_call_bar_end),
    )

    override fun publish(call: NyutagOngoingCall?) {
        state.value = call
    }

    override fun returnToCall() {
        // Экран звонка живёт в своей задаче: намерение без данных звонка просто выводит его вперёд (и разворачивает из PiP).
        runCatchingExceptions {
            context.startActivity(Intent(context, ElementCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Timber.w(it, "Nyutag: could not return to the call") }
    }

    override fun toggleMicrophone() = sendToCallScreen(NyutagCallActions.TOGGLE_MICROPHONE)

    override fun hangUp() = sendToCallScreen(NyutagCallActions.HANG_UP)

    private fun sendToCallScreen(action: String) {
        context.sendBroadcast(Intent(action).setPackage(context.packageName))
    }
}

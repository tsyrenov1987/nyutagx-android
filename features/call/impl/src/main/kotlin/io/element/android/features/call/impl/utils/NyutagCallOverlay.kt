/*
 * Нютаг — плашка идущего звонка поверх других приложений.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.impl.utils

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.theme.Theme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.call.api.NyutagOngoingCall
import io.element.android.features.call.impl.ui.elapsedSeconds
import io.element.android.features.call.impl.ui.formatCallDuration
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.math.roundToInt

/** Подписи плашки — на языке, выбранном в приложении (у окна поверх приложений нет экрана, который бы его применил). */
internal data class NyutagCallOverlayLabels(
    val calling: String,
    val mute: String,
    val unmute: String,
    val end: String,
)

/**
 * Нютаг: зелёная плашка идущего звонка поверх всех приложений, пока Нютаг свёрнут, — как плашка звонка у телефона.
 * Имя и время разговора, микрофон и «Завершить»; тап — назад в звонок; плашку можно перетащить.
 * Нужно разрешение «поверх других приложений» (то же, что для экрана входящего); без него остаётся полоса в приложении.
 */
internal class NyutagCallOverlay(
    private val context: Context,
    private val onReturnToCall: () -> Unit,
    private val onToggleMicrophone: () -> Unit,
    private val onHangUp: () -> Unit,
) {
    private val windowManager = context.getSystemService<WindowManager>()
    private val call = mutableStateOf<NyutagOngoingCall?>(null)
    private var view: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = (context.resources.displayMetrics.density * 48).roundToInt()
    }

    /** Показать плашку со звонком [ongoingCall] или убрать её (null). Только с главного потока. */
    fun update(ongoingCall: NyutagOngoingCall?, labels: () -> NyutagCallOverlayLabels) {
        if (ongoingCall == null || !Settings.canDrawOverlays(context)) {
            detach()
            return
        }
        call.value = ongoingCall
        if (view == null) attach(labels())
    }

    private fun attach(labels: NyutagCallOverlayLabels) {
        val windowManager = windowManager ?: return
        val owner = OverlayLifecycleOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                ElementTheme(theme = Theme.Dark, applySystemBarsUpdate = false) {
                    call.value?.let { ongoingCall ->
                        NyutagCallOverlayPill(
                            call = ongoingCall,
                            labels = labels,
                            onReturnToCall = onReturnToCall,
                            onToggleMicrophone = onToggleMicrophone,
                            onHangUp = onHangUp,
                            onDrag = ::moveBy,
                        )
                    }
                }
            }
        }
        runCatchingExceptions {
            owner.resume()
            windowManager.addView(composeView, params)
            view = composeView
            lifecycleOwner = owner
        }.onFailure {
            Timber.w(it, "Nyutag: could not show the call overlay")
            owner.destroy()
        }
    }

    private fun detach() {
        val composeView = view ?: return
        runCatchingExceptions { windowManager?.removeViewImmediate(composeView) }
        lifecycleOwner?.destroy()
        view = null
        lifecycleOwner = null
    }

    private fun moveBy(offset: Offset) {
        val composeView = view ?: return
        params.x += offset.x.roundToInt()
        params.y = (params.y + offset.y.roundToInt()).coerceAtLeast(0)
        runCatchingExceptions { windowManager?.updateViewLayout(composeView, params) }
    }

    /** Жизненный цикл для Compose в окне без экрана. */
    private class OverlayLifecycleOwner : SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

        fun resume() {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

@Composable
internal fun NyutagCallOverlayPill(
    call: NyutagOngoingCall,
    labels: NyutagCallOverlayLabels,
    onReturnToCall: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onHangUp: () -> Unit,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(8.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(CallOverlayColor)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
            .clickable(onClick = onReturnToCall)
            .height(48.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.VoiceCallSolid(),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = listOfNotNull(call.title, callStatusText(call, labels.calling)).joinToString(" · "),
            color = Color.White,
            style = ElementTheme.typography.fontBodyMdMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        IconButton(onClick = onToggleMicrophone) {
            Icon(
                imageVector = if (call.isMicrophoneEnabled) CompoundIcons.MicOn() else CompoundIcons.MicOff(),
                contentDescription = if (call.isMicrophoneEnabled) labels.mute else labels.unmute,
                tint = Color.White,
            )
        }
        IconButton(onClick = onHangUp) {
            Icon(
                imageVector = CompoundIcons.EndCall(),
                contentDescription = labels.end,
                tint = Color.White,
            )
        }
    }
}

private val CallOverlayColor = Color(0xFF2FB560)

/** «Вызов…», пока собеседник не ответил, потом время разговора; разговор кончился — время замирает. */
@Composable
private fun callStatusText(call: NyutagOngoingCall, calling: String): String {
    val joinedAtMillis = call.joinedAtMillis ?: return calling
    val endedAtMillis = call.endedAtMillis
    var seconds by remember(joinedAtMillis, endedAtMillis) { mutableLongStateOf(elapsedSeconds(joinedAtMillis, endedAtMillis)) }
    LaunchedEffect(joinedAtMillis, endedAtMillis) {
        while (endedAtMillis == null) {
            delay(1_000L - (System.currentTimeMillis() - joinedAtMillis).mod(1_000L))
            seconds = elapsedSeconds(joinedAtMillis, null)
        }
    }
    return formatCallDuration(seconds)
}

@PreviewsDayNight
@Composable
internal fun NyutagCallOverlayPillPreview() = ElementPreview {
    NyutagCallOverlayPill(
        call = NyutagOngoingCall(
            roomId = RoomId("!room:nyutag.chat"),
            title = "Эржена",
            joinedAtMillis = 0L,
            endedAtMillis = 754_000L,
            isMicrophoneEnabled = true,
        ),
        labels = NyutagCallOverlayLabels(calling = "Calling…", mute = "Mute", unmute = "Unmute", end = "End call"),
        onReturnToCall = {},
        onToggleMicrophone = {},
        onHangUp = {},
        onDrag = {},
    )
}

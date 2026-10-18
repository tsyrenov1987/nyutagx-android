/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.MobileScreen
import io.element.android.features.call.api.CallData
import io.element.android.features.call.api.NyutagOngoingCall
import io.element.android.features.call.impl.data.WidgetMessage
import io.element.android.features.call.impl.utils.ActiveCallManager
import io.element.android.features.call.impl.utils.CallWidgetProvider
import io.element.android.features.call.impl.utils.NyutagOngoingCallPublisher
import io.element.android.features.call.impl.utils.WidgetMessageInterceptor
import io.element.android.features.call.impl.utils.WidgetMessageSerializer
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.api.widget.MatrixWidgetDriver
import io.element.android.libraries.network.useragent.UserAgentProvider
import io.element.android.services.analytics.api.ScreenTracker
import io.element.android.services.appnavstate.api.AppForegroundStateService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.UUID
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class CallScreenPresenter(
    @Assisted private val callData: CallData,
    @Assisted private val navigator: CallScreenNavigator,
    private val callWidgetProvider: CallWidgetProvider,
    userAgentProvider: UserAgentProvider,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    private val matrixClientsProvider: MatrixClientProvider,
    private val screenTracker: ScreenTracker,
    private val activeCallManager: ActiveCallManager,
    private val languageTagProvider: LanguageTagProvider,
    private val appForegroundStateService: AppForegroundStateService,
    @AppCoroutineScope
    private val appCoroutineScope: CoroutineScope,
    private val widgetMessageSerializer: WidgetMessageSerializer,
    private val ongoingCallPublisher: NyutagOngoingCallPublisher,
) : Presenter<CallScreenState> {
    @AssistedFactory
    interface Factory {
        fun create(callData: CallData, navigator: CallScreenNavigator): CallScreenPresenter
    }

    private val userAgent = userAgentProvider.provide()

    @Composable
    override fun present(): CallScreenState {
        val coroutineScope = rememberCoroutineScope()
        val urlState = remember { mutableStateOf<AsyncData<String>>(AsyncData.Uninitialized) }
        val callWidgetDriver = remember { mutableStateOf<MatrixWidgetDriver?>(null) }
        val messageInterceptor = remember { mutableStateOf<WidgetMessageInterceptor?>(null) }
        var isWidgetLoaded by rememberSaveable { mutableStateOf(false) }
        var ignoreWebViewError by rememberSaveable { mutableStateOf(false) }
        var webViewError by remember { mutableStateOf<String?>(null) }
        var isMicrophoneEnabled by remember { mutableStateOf(true) }
        var joinedAtMillis by remember { mutableStateOf<Long?>(null) }
        var endedAtMillis by remember { mutableStateOf<Long?>(null) }
        var hasJoinedCall by remember { mutableStateOf(false) }
        var hasHungUp by remember { mutableStateOf(false) }
        val roomCallInfo by observeRoomCallInfo()
        val languageTag = languageTagProvider.provideLanguageTag()
        val theme = "dark"

        DisposableEffect(Unit) {
            coroutineScope.launch {
                // Sets the call as joined
                activeCallManager.joinedCall(callData)
                fetchRoomCallUrl(
                    callData = callData,
                    urlState = urlState,
                    callWidgetDriver = callWidgetDriver,
                    languageTag = languageTag,
                    theme = theme,
                )
            }
            onDispose {
                appCoroutineScope.launch { activeCallManager.hangUpCall(callData) }
            }
        }
        screenTracker.TrackScreen(screen = MobileScreen.ScreenName.RoomCall)
        HandleMatrixClientSyncState()

        callWidgetDriver.value?.let { driver ->
            LaunchedEffect(Unit) {
                driver.incomingMessages
                    .onEach {
                        // Relay message to the WebView
                        messageInterceptor.value?.sendMessage(it)
                    }
                    .launchIn(this)

                driver.run()
            }
        }

        messageInterceptor.value?.let { interceptor ->
            LaunchedEffect(Unit) {
                interceptor.interceptedMessages
                    .onEach {
                        // We are receiving messages from the WebView, consider that the application is loaded
                        ignoreWebViewError = true
                        // Relay message to Widget Driver
                        callWidgetDriver.value?.send(it)

                        val parsedMessage = parseMessage(it)
                        if (parsedMessage?.direction == WidgetMessage.Direction.FromWidget) {
                            if (parsedMessage.action == WidgetMessage.Action.Close) {
                                close(callWidgetDriver.value, navigator)
                            } else if (parsedMessage.action == WidgetMessage.Action.ContentLoaded) {
                                isWidgetLoaded = true
                            } else if (parsedMessage.action == WidgetMessage.Action.Join) {
                                // Нютаг: мы вошли в звонок; таймер пойдёт, когда войдёт и собеседник (см. ниже).
                                hasJoinedCall = true
                            } else if (parsedMessage.action == WidgetMessage.Action.HangUp) {
                                // Нютаг: трубку положили кнопкой Element Call — таймер замирает сразу, не ждём закрытия экрана.
                                hasHungUp = true
                            } else if (parsedMessage.action == WidgetMessage.Action.DeviceMute) {
                                // Нютаг: Element Call сообщает состояние микрофона — для кнопки в свёрнутом окне.
                                (parsedMessage.data as? JsonObject)?.get("audio_enabled")?.jsonPrimitive?.booleanOrNull?.let { isMicrophoneEnabled = it }
                            }
                        }
                    }
                    .launchIn(this)
            }

            // Нютаг: отсчёт — с готового адреса звонка, а не с создания WebView: до адреса идут запуск прокси туннеля
            // и замер UDP до SFU, а на медленном телефоне это секунды из тех же десяти.
            val isUrlReady = urlState.value is AsyncData.Success
            LaunchedEffect(isUrlReady) {
                if (!isUrlReady) return@LaunchedEffect
                // Wait for the call to be joined, if it takes too long, we display an error
                delay(10.seconds)

                if (!isWidgetLoaded) {
                    Timber.w("The call took too long to load. Displaying an error before exiting.")

                    // This will display a simple 'Sorry, an error occurred' dialog and force the user to exit the call
                    webViewError = ""
                }
            }
        }

        // Нютаг: как в Telegram — время разговора идёт с ответа собеседника, а не с набора («Вызов…» без таймера),
        // и замирает, когда трубку положили или собеседник вышел.
        // snapshotFlow, а не ключи LaunchedEffect: свёрнутый без PiP экран остановлен и не перекомпонуется,
        // а момент ответа нужен сразу — его показывает полоса звонка в приложении.
        LaunchedEffect(Unit) {
            snapshotFlow { Triple(hasJoinedCall, roomCallInfo.isOtherPartyInCall, hasHungUp) }
                .collect { (joined, otherPartyInCall, hungUp) ->
                    when {
                        hungUp -> if (joinedAtMillis != null && endedAtMillis == null) endedAtMillis = clock.epochMillis()
                        !joined -> Unit
                        otherPartyInCall -> {
                            if (joinedAtMillis == null) joinedAtMillis = clock.epochMillis()
                            endedAtMillis = null
                        }
                        joinedAtMillis != null && endedAtMillis == null -> endedAtMillis = clock.epochMillis()
                    }
                }
        }

        // Нютаг: полоса «вернуться к звонку» в приложении (NyutagOngoingCallService). Трубку положили — полоса сразу уходит.
        LaunchedEffect(Unit) {
            snapshotFlow {
                if (hasHungUp) {
                    null
                } else {
                    NyutagOngoingCall(
                        roomId = callData.roomId,
                        title = roomCallInfo.title,
                        joinedAtMillis = joinedAtMillis,
                        endedAtMillis = endedAtMillis,
                        isMicrophoneEnabled = isMicrophoneEnabled,
                    )
                }
            }.collect { ongoingCallPublisher.publish(it) }
        }
        DisposableEffect(Unit) {
            onDispose { ongoingCallPublisher.publish(null) }
        }

        fun handleEvent(event: CallScreenEvent) {
            when (event) {
                is CallScreenEvent.Hangup -> {
                    hasHungUp = true
                    val widgetId = callWidgetDriver.value?.id
                    val interceptor = messageInterceptor.value
                    if (widgetId != null && interceptor != null && isWidgetLoaded) {
                        // If the call was joined, we need to hang up first. Then the UI will be dismissed automatically.
                        sendHangupMessage(widgetId, interceptor)
                        isWidgetLoaded = false

                        coroutineScope.launch {
                            // Wait for a couple of seconds to receive the hangup message
                            // If we don't get it in time, we close the screen anyway
                            delay(2.seconds)
                            close(callWidgetDriver.value, navigator)
                        }
                    } else {
                        coroutineScope.launch {
                            close(callWidgetDriver.value, navigator)
                        }
                    }
                }
                is CallScreenEvent.SetupMessageChannels -> {
                    messageInterceptor.value = event.widgetMessageInterceptor
                }
                is CallScreenEvent.ToggleMicrophone -> {
                    val widgetId = callWidgetDriver.value?.id
                    val interceptor = messageInterceptor.value
                    if (widgetId != null && interceptor != null && isWidgetLoaded) {
                        val enabled = !isMicrophoneEnabled
                        sendDeviceMuteMessage(widgetId, interceptor, audioEnabled = enabled)
                        isMicrophoneEnabled = enabled
                    }
                }
                is CallScreenEvent.OnWebViewError -> {
                    if (!ignoreWebViewError) {
                        webViewError = event.description.orEmpty()
                    }
                    // Else ignore the error, give a chance the Element Call to recover by itself.
                }
            }
        }

        return CallScreenState(
            urlState = urlState.value,
            webViewError = webViewError,
            userAgent = userAgent,
            isCallActive = isWidgetLoaded,
            isMicrophoneEnabled = isMicrophoneEnabled,
            isAudioCall = callData.isAudioCall,
            joinedAtMillis = joinedAtMillis,
            endedAtMillis = endedAtMillis,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun fetchRoomCallUrl(
        callData: CallData,
        urlState: MutableState<AsyncData<String>>,
        callWidgetDriver: MutableState<MatrixWidgetDriver?>,
        languageTag: String?,
        theme: String?,
    ) {
        urlState.runCatchingUpdatingState {
            val result = callWidgetProvider.getWidget(
                sessionId = callData.sessionId,
                roomId = callData.roomId,
                clientId = UUID.randomUUID().toString(),
                isAudioCall = callData.isAudioCall,
                languageTag = languageTag,
                theme = theme,
            ).getOrThrow()
            callWidgetDriver.value = result.driver
            Timber.d("Call widget driver initialized for sessionId: ${callData.sessionId}, roomId: ${callData.roomId}")
            result.url
        }
    }

    /** Нютаг: имя собеседника или группы и есть ли в звонке кто-то кроме нас (участники звонка из состояния комнаты). */
    @Composable
    private fun observeRoomCallInfo(): State<NyutagRoomCallInfo> {
        return produceState(initialValue = NyutagRoomCallInfo(title = null, isOtherPartyInCall = false)) {
            val client = matrixClientsProvider.getOrNull(callData.sessionId) ?: return@produceState
            client.getRoomInfoFlow(callData.roomId)
                .map { roomInfo ->
                    val info = roomInfo.getOrNull()
                    NyutagRoomCallInfo(
                        title = info?.name,
                        isOtherPartyInCall = info?.activeRoomCallParticipants.orEmpty().any { it != callData.sessionId },
                    )
                }
                .distinctUntilChanged()
                .collect { value = it }
        }
    }

    private data class NyutagRoomCallInfo(
        val title: String?,
        val isOtherPartyInCall: Boolean,
    )

    @Composable
    private fun HandleMatrixClientSyncState() {
        val coroutineScope = rememberCoroutineScope()
        DisposableEffect(Unit) {
            val client = matrixClientsProvider.getOrNull(callData.sessionId) ?: return@DisposableEffect onDispose {
                Timber.w("No MatrixClient found for sessionId, can't send call notification: ${callData.sessionId}")
            }
            coroutineScope.launch {
                Timber.d("Observing sync state in-call for sessionId: ${callData.sessionId}")
                client.syncService.syncState
                    .collect { state ->
                        if (state != SyncState.Running) {
                            appForegroundStateService.updateIsInCallState(true)
                        }
                    }
            }
            onDispose {
                Timber.d("Stopped observing sync state in-call for sessionId: ${callData.sessionId}")
                // Make sure we mark the call as ended in the app state
                appForegroundStateService.updateIsInCallState(false)
            }
        }
    }

    private fun parseMessage(message: String): WidgetMessage? {
        return widgetMessageSerializer.deserialize(message).getOrNull()
    }

    private fun sendHangupMessage(widgetId: String, messageInterceptor: WidgetMessageInterceptor) {
        val message = WidgetMessage(
            direction = WidgetMessage.Direction.ToWidget,
            widgetId = widgetId,
            requestId = "widgetapi-${clock.epochMillis()}",
            action = WidgetMessage.Action.HangUp,
            data = null,
        )
        messageInterceptor.sendMessage(widgetMessageSerializer.serialize(message))
    }

    /** Нютаг: переключить микрофон в Element Call (то же сообщение, которым iOS глушит звонок из CallKit). */
    private fun sendDeviceMuteMessage(widgetId: String, messageInterceptor: WidgetMessageInterceptor, audioEnabled: Boolean) {
        val message = WidgetMessage(
            direction = WidgetMessage.Direction.ToWidget,
            widgetId = widgetId,
            requestId = "widgetapi-${clock.epochMillis()}",
            action = WidgetMessage.Action.DeviceMute,
            data = buildJsonObject { put("audio_enabled", audioEnabled) },
        )
        messageInterceptor.sendMessage(widgetMessageSerializer.serialize(message))
    }

    private fun CoroutineScope.close(widgetDriver: MatrixWidgetDriver?, navigator: CallScreenNavigator) = launch(dispatchers.io) {
        navigator.close()
        widgetDriver?.close()
    }
}

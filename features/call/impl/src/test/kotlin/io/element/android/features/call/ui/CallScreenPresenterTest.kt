/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.ui

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import im.vector.app.features.analytics.plan.MobileScreen
import io.element.android.features.call.api.CallData
import io.element.android.features.call.api.NyutagOngoingCall
import io.element.android.features.call.impl.ui.CallScreenEvent
import io.element.android.features.call.impl.ui.CallScreenNavigator
import io.element.android.features.call.impl.ui.CallScreenPresenter
import io.element.android.features.call.impl.utils.NyutagOngoingCallPublisher
import io.element.android.features.call.impl.utils.WidgetMessageSerializer
import io.element.android.features.call.utils.FakeActiveCallManager
import io.element.android.features.call.utils.FakeCallWidgetProvider
import io.element.android.features.call.utils.FakeNyutagOngoingCallPublisher
import io.element.android.features.call.utils.FakeWidgetMessageInterceptor
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_NAME
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.sync.FakeSyncService
import io.element.android.libraries.matrix.test.widget.FakeMatrixWidgetDriver
import io.element.android.libraries.network.useragent.UserAgentProvider
import io.element.android.services.analytics.api.ScreenTracker
import io.element.android.services.analytics.test.FakeScreenTracker
import io.element.android.services.appnavstate.test.FakeAppForegroundStateService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.Optional
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CallScreenPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - with CallData sets call as active, loads URL and runs WidgetDriver`() = runTest {
        val widgetDriver = FakeMatrixWidgetDriver()
        val widgetProvider = FakeCallWidgetProvider(widgetDriver)
        val analyticsLambda = lambdaRecorder<MobileScreen.ScreenName, Unit> {}
        val joinedCallLambda = lambdaRecorder<CallData, Unit> {}
        val presenter = createCallScreenPresenter(
            callData = CallData(A_SESSION_ID, A_ROOM_ID, false),
            widgetDriver = widgetDriver,
            widgetProvider = widgetProvider,
            screenTracker = FakeScreenTracker(analyticsLambda),
            activeCallManager = FakeActiveCallManager(joinedCallResult = joinedCallLambda),
        )
        presenter.test {
            // Wait until the URL is loaded
            advanceTimeBy(1.seconds)
            skipItems(1)

            joinedCallLambda.assertions().isCalledOnce()
            val initialState = awaitItem()
            assertThat(initialState.urlState).isInstanceOf(AsyncData.Loading::class.java)
            assertThat(initialState.isCallActive).isFalse()
            assertThat(widgetProvider.getWidgetCalled).isTrue()
            assertThat(widgetDriver.runCalledCount).isEqualTo(1)
            analyticsLambda.assertions().isCalledOnce().with(value(MobileScreen.ScreenName.RoomCall))

            // Wait until the WidgetDriver is loaded
            skipItems(1)

            assertThat(awaitItem().urlState).isInstanceOf(AsyncData.Success::class.java)
        }
    }

    @Test
    fun `present - set message interceptor, send and receive messages`() = runTest {
        val widgetDriver = FakeMatrixWidgetDriver()
        val presenter = createCallScreenPresenter(
            callData = CallData(A_SESSION_ID, A_ROOM_ID, false),
            widgetDriver = widgetDriver,
            screenTracker = FakeScreenTracker {},
        )
        val messageInterceptor = FakeWidgetMessageInterceptor()
        presenter.test {
            // Give it time to load the URL and WidgetDriver
            advanceTimeBy(1.seconds)

            val initialState = awaitItem()
            initialState.eventSink(CallScreenEvent.SetupMessageChannels(messageInterceptor))

            // And incoming message from the Widget Driver is passed to the WebView
            widgetDriver.givenIncomingMessage("A message")
            assertThat(messageInterceptor.sentMessages).containsExactly("A message")

            // And incoming message from the WebView is passed to the Widget Driver
            messageInterceptor.givenInterceptedMessage("A reply")
            assertThat(widgetDriver.sentMessages).containsExactly("A reply")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - hang up event closes the screen and stops the widget driver`() = runTest(UnconfinedTestDispatcher()) {
        val navigator = FakeCallScreenNavigator()
        val widgetDriver = FakeMatrixWidgetDriver()
        val presenter = createCallScreenPresenter(
            callData = CallData(A_SESSION_ID, A_ROOM_ID, false),
            widgetDriver = widgetDriver,
            navigator = navigator,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
            screenTracker = FakeScreenTracker {},
        )
        val messageInterceptor = FakeWidgetMessageInterceptor()
        presenter.test {
            val initialState = awaitItem()

            // Give it time to load the URL and WidgetDriver
            advanceTimeBy(1.seconds)

            initialState.eventSink(CallScreenEvent.SetupMessageChannels(messageInterceptor))

            initialState.eventSink(CallScreenEvent.Hangup)

            // Let background coroutines run and the widget drive be received
            runCurrent()

            assertThat(navigator.closeCalled).isTrue()
            assertThat(widgetDriver.closeCalledCount).isEqualTo(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a received close message closes the screen and stops the widget driver`() = runTest(UnconfinedTestDispatcher()) {
        val navigator = FakeCallScreenNavigator()
        val widgetDriver = FakeMatrixWidgetDriver()
        val presenter = createCallScreenPresenter(
            callData = CallData(A_SESSION_ID, A_ROOM_ID, false),
            widgetDriver = widgetDriver,
            navigator = navigator,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
            screenTracker = FakeScreenTracker {},
        )
        val messageInterceptor = FakeWidgetMessageInterceptor()
        presenter.test {
            val initialState = awaitItem()

            // Give it time to load the URL and WidgetDriver
            advanceTimeBy(1.seconds)

            initialState.eventSink(CallScreenEvent.SetupMessageChannels(messageInterceptor))

            messageInterceptor.givenInterceptedMessage("""{"action":"io.element.close","api":"fromWidget","widgetId":"1","requestId":"1"}""")

            // Let background coroutines run
            advanceTimeBy(1.seconds)
            runCurrent()

            assertThat(navigator.closeCalled).isTrue()
            assertThat(widgetDriver.closeCalledCount).isEqualTo(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a received 'content loaded' action makes the call to be active`() = runTest {
        val navigator = FakeCallScreenNavigator()
        val widgetDriver = FakeMatrixWidgetDriver()
        val presenter = createCallScreenPresenter(
            callData = CallData(A_SESSION_ID, A_ROOM_ID, false),
            widgetDriver = widgetDriver,
            navigator = navigator,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
            screenTracker = FakeScreenTracker {},
        )
        val messageInterceptor = FakeWidgetMessageInterceptor()
        presenter.test {
            // Give it time to load the URL and WidgetDriver
            advanceTimeBy(1.seconds)
            skipItems(2)
            val initialState = awaitItem()
            assertThat(initialState.isCallActive).isFalse()
            initialState.eventSink(CallScreenEvent.SetupMessageChannels(messageInterceptor))
            messageInterceptor.givenInterceptedMessage(
                """
                    {
                        "action":"content_loaded",
                        "api":"fromWidget",
                        "widgetId":"1",
                        "requestId":"1"
                    }
                """.trimIndent()
            )
            skipItems(2)
            val finalState = awaitItem()
            assertThat(finalState.isCallActive).isTrue()
        }
    }

    @Test
    fun `present - if in room mode and no join action is received an error is displayed`() = runTest {
        val navigator = FakeCallScreenNavigator()
        val widgetDriver = FakeMatrixWidgetDriver()
        val presenter = createCallScreenPresenter(
            callData = CallData(A_SESSION_ID, A_ROOM_ID, false),
            widgetDriver = widgetDriver,
            navigator = navigator,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
            screenTracker = FakeScreenTracker {},
        )
        val messageInterceptor = FakeWidgetMessageInterceptor()
        presenter.test {
            // Give it time to load the URL and WidgetDriver
            advanceTimeBy(1.seconds)
            skipItems(2)
            val initialState = awaitItem()
            assertThat(initialState.isCallActive).isFalse()
            initialState.eventSink(CallScreenEvent.SetupMessageChannels(messageInterceptor))
            skipItems(2)

            // Wait for the timeout to trigger
            advanceTimeBy(10.seconds)

            val finalState = awaitItem()
            assertThat(finalState.isCallActive).isFalse()
            // The error dialog that will force the user to leave the call is displayed
            assertThat(finalState.webViewError).isNotNull()
            assertThat(finalState.webViewError).isEmpty()
        }
    }

    @Test
    fun `present - the load timeout counts from the moment the call URL is ready`() = runTest {
        val widgetDriver = FakeMatrixWidgetDriver()
        val presenter = createCallScreenPresenter(
            callData = CallData(A_SESSION_ID, A_ROOM_ID, false),
            widgetDriver = widgetDriver,
            // Нютаг: до адреса звонка идут запуск прокси туннеля и замер UDP.
            widgetProvider = FakeCallWidgetProvider(widgetDriver, beforeResult = { delay(8.seconds) }),
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
            screenTracker = FakeScreenTracker {},
        )
        presenter.test {
            awaitItem().eventSink(CallScreenEvent.SetupMessageChannels(FakeWidgetMessageInterceptor()))
            // The URL is ready at 8 s. Counting from the WebView creation would have displayed the error at 10 s.
            advanceTimeBy(12.seconds)
            assertThat(expectMostRecentItem().webViewError).isNull()

            // 10 s after the URL is ready: the error is displayed.
            assertThat(awaitItem().webViewError).isEmpty()
            assertThat(testScheduler.currentTime).isAtLeast(18.seconds.inWholeMilliseconds)
            assertThat(testScheduler.currentTime).isLessThan(19.seconds.inWholeMilliseconds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - automatically sets the isInCall state when starting the call and disposing the screen`() = runTest {
        val navigator = FakeCallScreenNavigator()
        val widgetDriver = FakeMatrixWidgetDriver()
        val startSyncLambda = lambdaRecorder<Result<Unit>> { Result.success(Unit) }
        val syncService = FakeSyncService(SyncState.Idle).apply {
            this.startSyncLambda = startSyncLambda
        }
        val matrixClient = FakeMatrixClient(syncService = syncService)
        val appForegroundStateService = FakeAppForegroundStateService()
        val presenter = createCallScreenPresenter(
            callData = CallData(A_SESSION_ID, A_ROOM_ID, false),
            widgetDriver = widgetDriver,
            navigator = navigator,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
            matrixClientsProvider = FakeMatrixClientProvider(getClient = { Result.success(matrixClient) }),
            screenTracker = FakeScreenTracker {},
            appForegroundStateService = appForegroundStateService,
        )
        val hasRun = Mutex(true)
        val job = launch {
            moleculeFlow(RecompositionMode.Immediate) {
                presenter.present()
            }.collect {
                hasRun.unlock()
            }
        }

        appForegroundStateService.isInCall.test {
            // The initial isInCall state will always be false
            assertThat(awaitItem()).isFalse()

            // Wait until the call starts
            hasRun.lock()

            // Then it'll be true once the call is active
            assertThat(awaitItem()).isTrue()

            // If we dispose the screen
            job.cancelAndJoin()

            // The isInCall state is now false
            assertThat(awaitItem()).isFalse()

            // And there are no more events
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `present - the timer waits for the other party to answer and freezes on hang up, the in-app call bar follows`() = runTest {
        var now = 1_000L
        val roomInfoFlow = MutableStateFlow<Optional<RoomInfo>>(
            Optional.of(aRoomInfo(activeRoomCallParticipants = listOf(A_SESSION_ID)))
        )
        val matrixClient = FakeMatrixClient().apply { getRoomInfoFlowLambda = { roomInfoFlow } }
        val ongoingCallPublisher = FakeNyutagOngoingCallPublisher()
        val presenter = createCallScreenPresenter(
            callData = CallData(A_SESSION_ID, A_ROOM_ID, true),
            matrixClientsProvider = FakeMatrixClientProvider(getClient = { Result.success(matrixClient) }),
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
            screenTracker = FakeScreenTracker {},
            clock = SystemClock { now },
            ongoingCallPublisher = ongoingCallPublisher,
        )
        val messageInterceptor = FakeWidgetMessageInterceptor()
        presenter.test {
            advanceTimeBy(1.seconds)
            awaitItem().eventSink(CallScreenEvent.SetupMessageChannels(messageInterceptor))
            messageInterceptor.givenInterceptedMessage(
                """{"action":"content_loaded","api":"fromWidget","widgetId":"1","requestId":"1"}"""
            )
            // We joined the call, but nobody answered yet: still "Calling…", no timer.
            messageInterceptor.givenInterceptedMessage(
                """{"action":"io.element.join","api":"fromWidget","widgetId":"1","requestId":"2"}"""
            )
            now = 5_000L
            runCurrent()
            assertThat(expectMostRecentItem().joinedAtMillis).isNull()
            assertThat(ongoingCallPublisher.lastCall).isEqualTo(
                NyutagOngoingCall(roomId = A_ROOM_ID, title = A_ROOM_NAME, joinedAtMillis = null, endedAtMillis = null, isMicrophoneEnabled = true)
            )

            // The other party answers: the timer starts from this moment.
            now = 16_000L
            roomInfoFlow.value = Optional.of(aRoomInfo(activeRoomCallParticipants = listOf(A_SESSION_ID, A_USER_ID_2)))
            runCurrent()
            val talking = expectMostRecentItem()
            assertThat(talking.joinedAtMillis).isEqualTo(16_000L)
            assertThat(talking.endedAtMillis).isNull()
            assertThat(ongoingCallPublisher.lastCall?.joinedAtMillis).isEqualTo(16_000L)

            // Hang up: the timer stops right away, not when the screen closes.
            now = 58_000L
            talking.eventSink(CallScreenEvent.Hangup)
            runCurrent()
            val ended = expectMostRecentItem()
            assertThat(ended.joinedAtMillis).isEqualTo(16_000L)
            assertThat(ended.endedAtMillis).isEqualTo(58_000L)
            // The call bar in the app goes away as soon as we hang up.
            assertThat(ongoingCallPublisher.lastCall).isNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.createCallScreenPresenter(
        callData: CallData,
        navigator: CallScreenNavigator = FakeCallScreenNavigator(),
        widgetDriver: FakeMatrixWidgetDriver = FakeMatrixWidgetDriver(),
        widgetProvider: FakeCallWidgetProvider = FakeCallWidgetProvider(widgetDriver),
        dispatchers: CoroutineDispatchers = testCoroutineDispatchers(),
        matrixClientsProvider: FakeMatrixClientProvider = FakeMatrixClientProvider(),
        activeCallManager: FakeActiveCallManager = FakeActiveCallManager(),
        screenTracker: ScreenTracker = FakeScreenTracker(),
        appForegroundStateService: FakeAppForegroundStateService = FakeAppForegroundStateService(),
        clock: SystemClock = SystemClock { 0 },
        ongoingCallPublisher: NyutagOngoingCallPublisher = FakeNyutagOngoingCallPublisher(),
    ): CallScreenPresenter {
        val userAgentProvider = object : UserAgentProvider {
            override fun provide(): String {
                return "Test"
            }
        }
        return CallScreenPresenter(
            callData = callData,
            navigator = navigator,
            callWidgetProvider = widgetProvider,
            userAgentProvider = userAgentProvider,
            clock = clock,
            dispatchers = dispatchers,
            matrixClientsProvider = matrixClientsProvider,
            activeCallManager = activeCallManager,
            screenTracker = screenTracker,
            languageTagProvider = FakeLanguageTagProvider("en-US"),
            appForegroundStateService = appForegroundStateService,
            appCoroutineScope = backgroundScope,
            widgetMessageSerializer = WidgetMessageSerializer(DefaultJsonProvider()),
            ongoingCallPublisher = ongoingCallPublisher,
        )
    }
}

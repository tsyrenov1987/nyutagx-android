/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.webkit.PermissionRequest
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import dev.zacsweers.metro.Inject
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.compound.theme.ForcedDarkElementTheme
import io.element.android.features.call.api.CallData
import io.element.android.features.call.impl.DefaultElementCallEntryPoint
import io.element.android.features.call.impl.R
import io.element.android.features.call.impl.di.CallBindings
import io.element.android.features.call.impl.pip.PictureInPictureEvent
import io.element.android.features.call.impl.pip.PictureInPicturePresenter
import io.element.android.features.call.impl.pip.PictureInPictureState
import io.element.android.features.call.impl.pip.PipView
import io.element.android.features.call.impl.services.CallForegroundService
import io.element.android.features.call.impl.utils.NyutagCallActions
import io.element.android.features.call.impl.utils.WebViewAudioManager
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.androidutils.browser.ConsoleMessageLogger
import io.element.android.libraries.androidutils.media.setAspectRatioFromOrientation
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.bindings
import io.element.android.libraries.audio.api.AudioFocus
import io.element.android.libraries.audio.api.AudioFocusRequester
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.log.logger.LoggerTag
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.icons.CompoundDrawables
import io.element.android.libraries.designsystem.theme.ElementThemeApp
import io.element.android.libraries.designsystem.utils.hasCompactHeightWindowSize
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber

private val loggerTag = LoggerTag("ElementCallActivity")

class ElementCallActivity :
    AppCompatActivity(),
    CallScreenNavigator,
    PipView {
    @Inject lateinit var presenterFactory: CallScreenPresenter.Factory
    @Inject lateinit var appPreferencesStore: AppPreferencesStore
    @Inject lateinit var featureFlagService: FeatureFlagService
    @Inject lateinit var enterpriseService: EnterpriseService
    @Inject lateinit var pictureInPicturePresenter: PictureInPicturePresenter
    @Inject lateinit var buildMeta: BuildMeta
    @Inject lateinit var audioFocus: AudioFocus
    @Inject lateinit var consoleMessageLogger: ConsoleMessageLogger

    private lateinit var presenter: Presenter<CallScreenState>

    private var requestPermissionCallback: RequestPermissionCallback? = null

    private val requestPermissionsLauncher = registerPermissionResultLauncher()

    private val webViewTarget = mutableStateOf<CallData?>(null)

    // Нютаг: микрофон запрещён — без предупреждения звонок молча идёт без звука, и собеседник не слышит.
    private var isMicrophoneDenied by mutableStateOf(false)

    private var eventSink: ((CallScreenEvent) -> Unit)? = null

    private var currentPipOrientation: Int? = null

    // Нютаг: кнопки в свёрнутом окне звонка (микрофон, громкая связь, завершить) — через широковещательные намерения.
    private var isMicrophoneEnabled = true
    private var isSpeakerOn = false
    private var webViewAudioManager by mutableStateOf<WebViewAudioManager?>(null)
    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PIP_HANG_UP -> eventSink?.invoke(CallScreenEvent.Hangup)
                ACTION_PIP_TOGGLE_MICROPHONE -> eventSink?.invoke(CallScreenEvent.ToggleMicrophone)
                ACTION_PIP_TOGGLE_SPEAKER -> webViewAudioManager?.toggleSpeaker()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindings<CallBindings>().inject(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        setCallData(intent)
        // If presenter is not created at this point, it means we have no call to display, the Activity is finishing, so return early
        if (!::presenter.isInitialized) {
            return
        }

        pictureInPicturePresenter.setPipView(this)
        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter().apply {
                addAction(ACTION_PIP_HANG_UP)
                addAction(ACTION_PIP_TOGGLE_MICROPHONE)
                addAction(ACTION_PIP_TOGGLE_SPEAKER)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        Timber.d("Created ElementCallActivity with call type: ${webViewTarget.value}")

        setContent {
            val pipState = pictureInPicturePresenter.present()
            ListenToAndroidEvents(pipState)
            val colors by remember(webViewTarget.value?.sessionId) {
                enterpriseService.semanticColorsFlow(sessionId = webViewTarget.value?.sessionId)
            }.collectAsState(SemanticColorsLightDark.default)

            // When the height is compact, hide the system bars by default to maximize the space for the call, using immersive mode
            val hasCompactHeight = hasCompactHeightWindowSize()
            DisposableEffect(hasCompactHeight, pipState.isInPictureInPicture) {
                if (hasCompactHeight && !pipState.isInPictureInPicture) {
                    val window = this@ElementCallActivity.window ?: return@DisposableEffect onDispose {}
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    val systemBarInsets = WindowInsetsCompat.Type.systemBars()
                    insetsController.hide(systemBarInsets)

                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                    onDispose {
                        insetsController.show(systemBarInsets)
                        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    }
                } else {
                    onDispose {}
                }
            }

            ElementThemeApp(
                appPreferencesStore = appPreferencesStore,
                featureFlagService = featureFlagService,
                compoundLight = colors.light,
                compoundDark = colors.dark,
                buildMeta = buildMeta,
            ) {
                ForcedDarkElementTheme(
                    colors = colors,
                ) {
                    val state = presenter.present()
                    eventSink = state.eventSink
                    LaunchedEffect(state.isCallActive) {
                        if (state.isCallActive) {
                            setCallIsActive()
                        }
                    }
                    LaunchedEffect(state.isMicrophoneEnabled) {
                        isMicrophoneEnabled = state.isMicrophoneEnabled
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            setPipParams()
                        }
                    }
                    val speakerOn by (webViewAudioManager?.isSpeakerOn ?: flowOf(false)).collectAsState(initial = false)
                    LaunchedEffect(speakerOn) {
                        isSpeakerOn = speakerOn
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            setPipParams()
                        }
                    }
                    CallScreenView(
                        state = state,
                        pipState = pipState,
                        onConsoleMessage = {
                            consoleMessageLogger.log("ElementCall", it)
                        },
                        requestPermissions = { permissions, callback ->
                            requestPermissionCallback = callback
                            requestPermissionsLauncher.launch(permissions)
                        },
                        onAudioManagerChange = { webViewAudioManager = it },
                        onMinimizeToApp = ::minimizeToApp,
                    )
                    if (isMicrophoneDenied) {
                        ConfirmationDialog(
                            title = stringResource(R.string.nyutag_call_microphone_denied_title),
                            content = stringResource(R.string.nyutag_call_microphone_denied_message),
                            submitText = stringResource(CommonStrings.action_go_to_settings),
                            onSubmitClick = {
                                isMicrophoneDenied = false
                                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                            },
                            onDismiss = { isMicrophoneDenied = false },
                        )
                    }
                }
            }
        }
    }

    private fun setCallIsActive() {
        audioFocus.requestAudioFocus(
            requester = AudioFocusRequester.ElementCall,
            onFocusLost = {
                // If the audio focus is lost, we do not stop the call.
                Timber.tag(loggerTag.value).w("Audio focus lost")
            }
        )
        CallForegroundService.start(this)
    }

    @Composable
    private fun ListenToAndroidEvents(pipState: PictureInPictureState) {
        val pipEventSink by rememberUpdatedState(pipState.eventSink)
        DisposableEffect(Unit) {
            val listener = Runnable {
                if (requestPermissionCallback != null) {
                    Timber.tag(loggerTag.value).w("Ignoring onUserLeaveHint event because user is asked to grant permissions")
                } else {
                    pipEventSink(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            PictureInPictureEvent.EnterPictureInPictureNow
                        } else {
                            PictureInPictureEvent.EnterPictureInPicture
                        }
                    )
                }
            }
            addOnUserLeaveHintListener(listener)
            onDispose {
                removeOnUserLeaveHintListener(listener)
            }
        }
        DisposableEffect(Unit) {
            val onPictureInPictureModeChangedListener = Consumer { _: PictureInPictureModeChangedInfo ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setPipParams()
                }
                pipEventSink(PictureInPictureEvent.OnPictureInPictureModeChanged(isInPictureInPictureMode))
                if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    Timber.tag(loggerTag.value).d("Exiting PiP mode: Hangup the call")
                    eventSink?.invoke(CallScreenEvent.Hangup)
                }
            }
            addOnPictureInPictureModeChangedListener(onPictureInPictureModeChangedListener)
            onDispose {
                removeOnPictureInPictureModeChangedListener(onPictureInPictureModeChangedListener)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setCallData(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::presenter.isInitialized) {
            unregisterReceiver(pipActionReceiver)
        }
        audioFocus.releaseAudioFocus()
        CallForegroundService.stop(this)
        pictureInPicturePresenter.setPipView(null)
    }

    override fun finish() {
        // Also remove the task from recents
        finishAndRemoveTask()
    }

    override fun close() {
        finish()
    }

    private fun setCallData(intent: Intent?) {
        val callData = intent?.let {
            IntentCompat.getParcelableExtra(intent, DefaultElementCallEntryPoint.EXTRA_CALL_TYPE, CallData::class.java)
        }
        val currentCallData = webViewTarget.value
        if (currentCallData == null) {
            if (callData == null) {
                Timber.tag(loggerTag.value).d("Re-opened the activity but we have no url to load or a cached one, finish the activity")
                finish()
            } else {
                Timber.tag(loggerTag.value).d("Set the call type and create the presenter")
                webViewTarget.value = callData
                presenter = presenterFactory.create(callData, this)
            }
        } else {
            if (callData == null) {
                Timber.tag(loggerTag.value).d("Coming back from notification, do nothing")
            } else if (callData != currentCallData) {
                Timber.tag(loggerTag.value).d("User starts another call, restart the Activity")
                setIntent(intent)
                recreate()
            } else {
                // Starting the same call again, should not happen, the UI is preventing this. But maybe when using external links.
                Timber.tag(loggerTag.value).d("Starting the same call again, do nothing")
            }
        }
    }

    private fun registerPermissionResultLauncher(): ActivityResultLauncher<Array<String>> {
        return registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val callback = requestPermissionCallback ?: return@registerForActivityResult
            val permissionsToGrant = mutableListOf<String>()
            permissions.forEach { (permission, granted) ->
                if (granted) {
                    val webKitPermission = when (permission) {
                        Manifest.permission.CAMERA -> PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        Manifest.permission.RECORD_AUDIO -> PermissionRequest.RESOURCE_AUDIO_CAPTURE
                        else -> return@forEach
                    }
                    permissionsToGrant.add(webKitPermission)
                } else if (permission == Manifest.permission.RECORD_AUDIO) {
                    Timber.tag(loggerTag.value).w("Microphone permission denied, the other side will not hear us")
                    isMicrophoneDenied = true
                }
            }
            callback(permissionsToGrant.toTypedArray())
            requestPermissionCallback = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun setPipParams() {
        setPictureInPictureParams(getPictureInPictureParams())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun enterPipMode(): Boolean {
        return if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            enterPictureInPictureMode(getPictureInPictureParams())
        } else {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun setPipOrientation(orientation: Int?) {
        currentPipOrientation = orientation
        setPictureInPictureParams(getPictureInPictureParams())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getPictureInPictureParams(): PictureInPictureParams {
        // Portrait for calls seems more appropriate as a fallback value
        val orientation = currentPipOrientation ?: Configuration.ORIENTATION_PORTRAIT
        return PictureInPictureParams.Builder()
            .setAspectRatioFromOrientation(orientation)
            .setActions(pipActions())
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(true)
                }
            }
            .build()
    }

    /** Нютаг: кнопки свёрнутого окна — микрофон, громкая связь и завершить звонок. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun pipActions(): List<RemoteAction> {
        fun action(requestCode: Int, actionName: String, icon: Int, title: String) = RemoteAction(
            Icon.createWithResource(this, icon),
            title,
            title,
            PendingIntent.getBroadcast(
                this,
                requestCode,
                Intent(actionName).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        val microphone = if (isMicrophoneEnabled) {
            action(1, ACTION_PIP_TOGGLE_MICROPHONE, CompoundDrawables.ic_compound_mic_on, getString(CommonStrings.common_mute))
        } else {
            action(1, ACTION_PIP_TOGGLE_MICROPHONE, CompoundDrawables.ic_compound_mic_off, getString(CommonStrings.common_unmute))
        }
        val speaker = if (isSpeakerOn) {
            action(3, ACTION_PIP_TOGGLE_SPEAKER, CompoundDrawables.ic_compound_volume_on, getString(R.string.nyutag_call_speaker_off))
        } else {
            action(3, ACTION_PIP_TOGGLE_SPEAKER, CompoundDrawables.ic_compound_volume_off, getString(R.string.nyutag_call_speaker_on))
        }
        val hangUp = action(2, ACTION_PIP_HANG_UP, CompoundDrawables.ic_compound_end_call, getString(R.string.nyutag_call_end))
        return listOf(microphone, speaker, hangUp)
    }

    override fun hangUp() {
        eventSink?.invoke(CallScreenEvent.Hangup)
    }

    /**
     * Нютаг: свернуть звонок в приложение, как в Telegram, — дальше им управляет полоса сверху (NyutagOngoingCallService).
     * NO_USER_ACTION: без него сработает onUserLeaveHint, и звонок уйдёт в окно поверх.
     */
    private fun minimizeToApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        runCatchingExceptions { startActivity(launchIntent) }
            .onFailure { Timber.tag(loggerTag.value).w(it, "Could not minimize the call to the app") }
    }

    private companion object {
        const val ACTION_PIP_HANG_UP = NyutagCallActions.HANG_UP
        const val ACTION_PIP_TOGGLE_MICROPHONE = NyutagCallActions.TOGGLE_MICROPHONE
        const val ACTION_PIP_TOGGLE_SPEAKER = NyutagCallActions.TOGGLE_SPEAKER
    }
}

internal fun mapWebkitPermissions(permissions: Array<String>): List<String> {
    return permissions.mapNotNull { permission ->
        when (permission) {
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
            else -> null
        }
    }
}

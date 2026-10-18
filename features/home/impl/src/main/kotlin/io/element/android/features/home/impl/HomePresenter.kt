/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.home.impl.contacts.NyutagContactsState
import io.element.android.features.home.impl.roomlist.RoomListState
import io.element.android.features.home.impl.spaces.HomeSpacesState
import io.element.android.features.logout.api.direct.DirectLogoutState
import io.element.android.features.rageshake.api.RageshakeFeatureAvailability
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.nyutag.NyutagLanguageOption
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.indicator.api.IndicatorService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.nyutag.NyutagAnnouncement
import io.element.android.libraries.matrix.api.nyutag.NyutagInvites
import io.element.android.libraries.matrix.api.sync.SyncService
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

@Inject
class HomePresenter(
    private val client: MatrixClient,
    private val syncService: SyncService,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val indicatorService: IndicatorService,
    private val roomListPresenter: Presenter<RoomListState>,
    private val homeSpacesPresenter: Presenter<HomeSpacesState>,
    private val contactsPresenter: Presenter<NyutagContactsState>,
    private val logoutPresenter: Presenter<DirectLogoutState>,
    private val rageshakeFeatureAvailability: RageshakeFeatureAvailability,
    private val sessionStore: SessionStore,
    private val nyutagInvites: NyutagInvites,
) : Presenter<HomeState> {
    private val currentUserWithNeighborsBuilder = CurrentUserWithNeighborsBuilder()

    @Composable
    override fun present(): HomeState {
        val coroutineState = rememberCoroutineScope()
        val matrixUser by client.userProfile.collectAsState()
        val currentUserAndNeighbors by remember {
            combine(
                client.userProfile,
                sessionStore.sessionsFlow(),
                currentUserWithNeighborsBuilder::build,
            )
        }.collectAsState(initial = persistentListOf(matrixUser))
        val isOnline by syncService.isOnline.collectAsState()
        val canReportBug by remember { rageshakeFeatureAvailability.isAvailable() }.collectAsState(false)
        val roomListState = roomListPresenter.present()
        val homeSpacesState = homeSpacesPresenter.present()
        val contactsState = contactsPresenter.present()
        var currentHomeNavigationBarItemOrdinal by rememberSaveable { mutableIntStateOf(HomeNavigationBarItem.Chats.ordinal) }
        val currentHomeNavigationBarItem by remember {
            derivedStateOf {
                HomeNavigationBarItem.from(currentHomeNavigationBarItemOrdinal)
            }
        }
        LaunchedEffect(Unit) {
            // Force a refresh of the profile
            client.getUserProfile()
        }
        // Нютаг: обращение разработчика — после того, как список успеет появиться.
        var nyutagAnnouncement by remember { mutableStateOf<NyutagAnnouncement?>(null) }
        var isNyutagDonateOpen by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(4_000)
            val language = runCatching { NyutagLanguageOption.current().tags }.getOrDefault("").ifEmpty { Locale.getDefault().language }
            nyutagInvites.nextAnnouncement(language)
                .onSuccess { nyutagAnnouncement = it }
                .onFailure { Timber.w(it, "Nyutag announce: failed") }
        }
        // Нютаг: красную точку на аватаре читают как статус «онлайн»; напоминание о ключе — баннер и строка в настройках.
        val showAvatarIndicator = false
        val directLogoutState = logoutPresenter.present()

        fun handleEvent(event: HomeEvent) {
            when (event) {
                is HomeEvent.SelectHomeNavigationBarItem -> {
                    currentHomeNavigationBarItemOrdinal = event.item.ordinal
                }
                is HomeEvent.SwitchToAccount -> coroutineState.launch {
                    sessionStore.setLatestSession(event.sessionId.value)
                }
                is HomeEvent.NyutagAnnouncementDismiss -> {
                    nyutagAnnouncement?.let { nyutagInvites.markAnnouncement(it, later = true) }
                    nyutagAnnouncement = null
                }
                is HomeEvent.NyutagAnnouncementAction -> {
                    nyutagAnnouncement?.let { nyutagInvites.markAnnouncement(it, later = false) }
                    nyutagAnnouncement = null
                    if (event.action.kind == NyutagAnnouncement.Kind.Donate) isNyutagDonateOpen = true
                }
                is HomeEvent.NyutagDonateDismiss -> isNyutagDonateOpen = false
            }
        }

        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
        return HomeState(
            currentUserAndNeighbors = currentUserAndNeighbors,
            showAvatarIndicator = showAvatarIndicator,
            hasNetworkConnection = isOnline,
            currentHomeNavigationBarItem = currentHomeNavigationBarItem,
            roomListState = roomListState,
            homeSpacesState = homeSpacesState,
            contactsState = contactsState,
            snackbarMessage = snackbarMessage,
            canReportBug = canReportBug,
            directLogoutState = directLogoutState,
            eventSink = ::handleEvent,
            nyutagAnnouncement = nyutagAnnouncement,
            isNyutagDonateOpen = isNyutagDonateOpen,
        )
    }
}

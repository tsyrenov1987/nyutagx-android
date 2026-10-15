/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.startchat.impl.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.startchat.api.StartDMAction
import io.element.android.features.startchat.impl.NyutagSavedMessages
import io.element.android.features.startchat.impl.userlist.SelectionMode
import io.element.android.features.startchat.impl.userlist.UserListDataStore
import io.element.android.features.startchat.impl.userlist.UserListPresenter
import io.element.android.features.startchat.impl.userlist.UserListPresenterArgs
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.nyutag.NyutagAddressBook
import io.element.android.libraries.matrix.api.nyutag.NyutagContactsSync
import io.element.android.libraries.matrix.api.nyutag.NyutagMatchedContact
import io.element.android.libraries.usersearch.api.UserRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Inject
class StartChatPresenter(
    presenterFactory: UserListPresenter.Factory,
    userRepository: UserRepository,
    userListDataStore: UserListDataStore,
    private val startDMAction: StartDMAction,
    private val savedMessages: NyutagSavedMessages,
    private val buildMeta: BuildMeta,
    private val matrixClient: MatrixClient,
    private val addressBook: NyutagAddressBook,
    private val contactsSync: NyutagContactsSync,
) : Presenter<StartChatState> {
    private val presenter = presenterFactory.create(
        UserListPresenterArgs(
            selectionMode = SelectionMode.Single,
        ),
        userRepository,
        userListDataStore,
    )

    @Composable
    override fun present(): StartChatState {
        val userListState = presenter.present()

        val localCoroutineScope = rememberCoroutineScope()
        val startDmActionState: MutableState<AsyncAction<RoomId>> = remember { mutableStateOf(AsyncAction.Uninitialized) }
        var canReadContacts by remember { mutableStateOf(addressBook.canRead()) }
        var nyutagContacts by remember { mutableStateOf<AsyncData<ImmutableList<NyutagMatchedContact>>>(AsyncData.Uninitialized) }

        fun syncContacts(force: Boolean) = localCoroutineScope.launch {
            canReadContacts = addressBook.canRead()
            if (!canReadContacts) return@launch
            // Прошлый итог — сразу, сверка с сервером — следом (не чаще раза в час, если не просили обновить).
            val cached = contactsSync.cached(matrixClient.sessionId)
            nyutagContacts = if (cached != null) AsyncData.Loading(cached.toImmutableList()) else AsyncData.Loading()
            nyutagContacts = contactsSync.sync(matrixClient.sessionId, force).fold(
                onSuccess = { AsyncData.Success(it.toImmutableList()) },
                onFailure = { error -> cached?.let { AsyncData.Success(it.toImmutableList()) } ?: AsyncData.Failure(error) },
            )
        }

        LaunchedEffect(Unit) { syncContacts(force = false) }

        fun handleEvent(event: StartChatEvent) {
            when (event) {
                is StartChatEvent.StartDM -> localCoroutineScope.launch {
                    startDMAction.execute(
                        matrixUser = event.matrixUser,
                        createIfDmDoesNotExist = startDmActionState.value is AsyncAction.Confirming,
                        actionState = startDmActionState,
                    )
                }
                StartChatEvent.CancelStartDM -> startDmActionState.value = AsyncAction.Uninitialized
                is StartChatEvent.SyncContacts -> syncContacts(event.force)
                is StartChatEvent.StartDMWithContact -> localCoroutineScope.launch {
                    startDMAction.execute(matrixUser = event.matrixUser, createIfDmDoesNotExist = true, actionState = startDmActionState)
                }
                is StartChatEvent.OpenSavedMessages -> localCoroutineScope.launch {
                    startDmActionState.value = AsyncAction.Loading
                    startDmActionState.value = savedMessages.roomId(event.name, event.topic).fold(
                        onSuccess = { AsyncAction.Success(it) },
                        onFailure = { AsyncAction.Failure(it) },
                    )
                }
            }
        }

        return StartChatState(
            applicationName = buildMeta.applicationName,
            userListState = userListState,
            startDmAction = startDmActionState.value,
            canReadContacts = canReadContacts,
            nyutagContacts = nyutagContacts,
            eventSink = ::handleEvent,
        )
    }
}

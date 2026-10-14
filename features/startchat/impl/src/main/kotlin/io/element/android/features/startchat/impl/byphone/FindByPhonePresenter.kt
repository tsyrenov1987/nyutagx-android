/*
 * Нютаг — поиск человека по номеру телефона.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.startchat.impl.byphone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.startchat.api.StartDMAction
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.nyutag.NyutagInvite
import io.element.android.libraries.matrix.api.nyutag.NyutagInvites
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Inject
class FindByPhonePresenter(
    private val invites: NyutagInvites,
    private val matrixClient: MatrixClient,
    private val startDMAction: StartDMAction,
) : Presenter<FindByPhoneState> {
    @Composable
    override fun present(): FindByPhoneState {
        val scope = rememberCoroutineScope()
        var phone by rememberSaveable { mutableStateOf("") }
        var search by remember { mutableStateOf<AsyncData<MatrixUser?>>(AsyncData.Uninitialized) }
        var invite by remember { mutableStateOf<AsyncData<NyutagInvite>>(AsyncData.Uninitialized) }
        // Код одноразовый: один на номер, повторный поиск того же номера его не тратит.
        val invitesByPhone = remember { mutableMapOf<String, NyutagInvite>() }
        var hasOwnPhone by remember { mutableStateOf<Boolean?>(null) }
        var ownPhone by rememberSaveable { mutableStateOf("") }
        var ownPhoneSave by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        val startDmAction: MutableState<AsyncAction<RoomId>> = remember { mutableStateOf(AsyncAction.Uninitialized) }
        var searchJob by remember { mutableStateOf<Job?>(null) }

        LaunchedEffect(Unit) {
            invites.hasPhone(matrixClient.sessionId).onSuccess { hasOwnPhone = it }
        }

        fun phoneKey(value: String) = value.filter { it.isDigit() }

        fun runSearch() {
            val query = phone.trim()
            invite = invitesByPhone[phoneKey(query)]?.let { AsyncData.Success(it) } ?: AsyncData.Uninitialized
            searchJob?.cancel()
            search = AsyncData.Loading()
            searchJob = scope.launch {
                search = invites.lookupPhones(matrixClient.sessionId, listOf(query)).fold(
                    onSuccess = { matches ->
                        val userId = matches.values.firstOrNull()?.let(::UserId)
                        AsyncData.Success(userId?.let { matrixClient.getProfile(it).getOrNull() ?: MatrixUser(it) })
                    },
                    onFailure = { AsyncData.Failure(it) },
                )
            }
        }

        fun handleEvent(event: FindByPhoneEvent) {
            when (event) {
                is FindByPhoneEvent.SetPhone -> if (event.phone != phone) {
                    phone = event.phone
                    searchJob?.cancel()
                    search = AsyncData.Uninitialized
                    invite = AsyncData.Uninitialized
                }
                is FindByPhoneEvent.PickedPhone -> {
                    phone = event.phone
                    runSearch()
                }
                FindByPhoneEvent.Search -> if (phone.count { it.isDigit() } >= FindByPhoneState.MIN_PHONE_DIGITS) runSearch()
                FindByPhoneEvent.CreateInvite -> if (!invite.isLoading()) {
                    val key = phoneKey(phone)
                    invite = AsyncData.Loading()
                    scope.launch {
                        invite = invites.createInvite(matrixClient.sessionId).fold(
                            onSuccess = {
                                invitesByPhone[key] = it
                                AsyncData.Success(it)
                            },
                            onFailure = { AsyncData.Failure(it) },
                        )
                    }
                }
                is FindByPhoneEvent.StartDM -> scope.launch {
                    // Человека выбрали явно по номеру — подтверждение «начать чат?» не нужно.
                    startDMAction.execute(event.user, createIfDmDoesNotExist = true, actionState = startDmAction)
                }
                FindByPhoneEvent.CancelStartDM -> startDmAction.value = AsyncAction.Uninitialized
                is FindByPhoneEvent.SetOwnPhone -> {
                    ownPhone = event.phone
                    if (ownPhoneSave is AsyncAction.Failure) ownPhoneSave = AsyncAction.Uninitialized
                }
                FindByPhoneEvent.SaveOwnPhone -> if (!ownPhoneSave.isLoading()) {
                    ownPhoneSave = AsyncAction.Loading
                    scope.launch {
                        ownPhoneSave = invites.setPhone(matrixClient.sessionId, ownPhone.trim()).fold(
                            onSuccess = {
                                hasOwnPhone = true
                                AsyncAction.Success(Unit)
                            },
                            onFailure = { AsyncAction.Failure(it) },
                        )
                    }
                }
            }
        }

        return FindByPhoneState(
            phone = phone,
            search = search,
            invite = invite,
            hasOwnPhone = hasOwnPhone,
            ownPhone = ownPhone,
            ownPhoneSave = ownPhoneSave,
            startDmAction = startDmAction.value,
            eventSink = ::handleEvent,
        )
    }
}

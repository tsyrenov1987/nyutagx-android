/*
 * Нютаг — вкладка «Контакты» на главной: телефонная книга, сверху те, кто уже в Нютаге.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.contacts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.startchat.api.StartDMAction
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.nyutag.NyutagAddressBook
import io.element.android.libraries.matrix.api.nyutag.NyutagContact
import io.element.android.libraries.matrix.api.nyutag.NyutagContactsSync
import io.element.android.libraries.matrix.api.nyutag.NyutagMatchedContact
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Inject
class NyutagContactsPresenter(
    private val matrixClient: MatrixClient,
    private val addressBook: NyutagAddressBook,
    private val contactsSync: NyutagContactsSync,
    private val startDMAction: StartDMAction,
) : Presenter<NyutagContactsState> {
    @Composable
    override fun present(): NyutagContactsState {
        val scope = rememberCoroutineScope()
        var canReadContacts by remember { mutableStateOf(addressBook.canRead()) }
        var contacts by remember { mutableStateOf<AsyncData<ImmutableList<NyutagHomeContact>>>(AsyncData.Uninitialized) }
        val startDmAction: MutableState<AsyncAction<RoomId>> = remember { mutableStateOf(AsyncAction.Uninitialized) }
        var loadJob by remember { mutableStateOf<Job?>(null) }

        fun load(force: Boolean) {
            canReadContacts = addressBook.canRead()
            if (!canReadContacts || loadJob?.isActive == true) return
            // Книга без пометок (или с прошлым итогом) — сразу, сверка с сервером — следом, не чаще раза в час без force.
            loadJob = scope.launch {
                val book = addressBook.contacts()
                contacts = AsyncData.Loading(mergeHomeContacts(book, contactsSync.cached(matrixClient.sessionId).orEmpty()))
                contacts = contactsSync.sync(matrixClient.sessionId, force).fold(
                    onSuccess = { AsyncData.Success(mergeHomeContacts(book, it)) },
                    onFailure = { error ->
                        val known = contacts.dataOrNull()
                        if (known.isNullOrEmpty()) AsyncData.Failure(error) else AsyncData.Success(known)
                    },
                )
            }
        }

        fun handleEvent(event: NyutagContactsEvent) {
            when (event) {
                is NyutagContactsEvent.Load -> load(event.force)
                is NyutagContactsEvent.StartDM -> {
                    val user = event.contact.user ?: return
                    // Имя — как записано в книжке, аватар — из профиля в Нютаге; человека выбрали явно — без «начать чат?».
                    scope.launch {
                        startDMAction.execute(user.copy(displayName = event.contact.name), createIfDmDoesNotExist = true, actionState = startDmAction)
                    }
                }
                NyutagContactsEvent.CancelStartDM -> startDmAction.value = AsyncAction.Uninitialized
            }
        }

        return NyutagContactsState(
            canReadContacts = canReadContacts,
            contacts = contacts,
            startDmAction = startDmAction.value,
            eventSink = ::handleEvent,
        )
    }
}

/** Книга с пометкой, кто в Нютаге (по последним 10 цифрам номера): такие — наверх, дальше остальные по имени. */
internal fun mergeHomeContacts(book: List<NyutagContact>, matched: List<NyutagMatchedContact>): ImmutableList<NyutagHomeContact> {
    fun key(phone: String) = phone.filter { it.isDigit() }.takeLast(10)
    val users = matched.associateBy({ key(it.phone) }, { it.user })
    return book
        .map { NyutagHomeContact(name = it.name, phone = it.phone, user = users[key(it.phone)]) }
        .distinctBy { it.user?.userId?.value ?: ("p:" + key(it.phone)) }
        .sortedWith(compareByDescending<NyutagHomeContact> { it.user != null }.thenBy { it.name.lowercase() })
        .toImmutableList()
}

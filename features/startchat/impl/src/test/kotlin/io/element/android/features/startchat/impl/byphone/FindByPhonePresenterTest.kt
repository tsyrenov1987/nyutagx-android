/*
 * Нютаг — тесты поиска по номеру телефона.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.startchat.impl.byphone

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.invitepeople.test.FakeStartDMAction
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.nyutag.NyutagAddressBook
import io.element.android.libraries.matrix.api.nyutag.NyutagContact
import io.element.android.libraries.matrix.api.nyutag.NyutagContactsSync
import io.element.android.libraries.matrix.api.nyutag.NyutagInvite
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.nyutag.NyutagMatchedContact
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.nyutag.FakeNyutagContactsSync
import io.element.android.libraries.matrix.test.nyutag.FakeNyutagInvites
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class FindByPhonePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val dorzho = UserId("@dorzho:nyutag.chat")

    @Test
    fun `present - found number shows the person and writes to them without confirmation`() = runTest {
        val invites = FakeNyutagInvites(lookupResult = Result.success(mapOf("8 900 123-45-67" to dorzho.value)))
        val client = FakeMatrixClient().apply { givenGetProfileResult(dorzho, Result.success(MatrixUser(dorzho, "Доржо"))) }
        var createdWithoutConfirmation: Boolean? = null
        val startDM = FakeStartDMAction { _, create, state ->
            createdWithoutConfirmation = create
            state.value = AsyncAction.Success(RoomId("!dm:nyutag.chat"))
        }
        createPresenter(invites, client, startDM).test {
            val initial = awaitState { it.hasOwnPhone != null }
            assertThat(initial.canSearch).isFalse()
            initial.eventSink(FindByPhoneEvent.SetPhone("8 900 123-45-67"))
            awaitState { it.canSearch }.eventSink(FindByPhoneEvent.Search)
            val found = awaitState { it.search is AsyncData.Success }
            assertThat(invites.lookedUpPhones).containsExactly("8 900 123-45-67")
            val user = found.search.dataOrNull()!!
            assertThat(user.displayName).isEqualTo("Доржо")
            found.eventSink(FindByPhoneEvent.StartDM(user))
            assertThat(awaitState { it.startDmAction is AsyncAction.Success }.startDmAction.dataOrNull()).isEqualTo(RoomId("!dm:nyutag.chat"))
            assertThat(createdWithoutConfirmation).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - unknown number offers one invitation per number`() = runTest {
        val invites = FakeNyutagInvites(inviteResult = Result.success(NyutagInvite("7KQ2-M9XA", 0L)))
        createPresenter(invites).test {
            awaitItem().eventSink(FindByPhoneEvent.PickedPhone("+7 900 000-00-01"))
            val notFound = awaitState { it.search is AsyncData.Success }
            assertThat(notFound.search.dataOrNull()).isNull()
            assertThat(notFound.invite).isEqualTo(AsyncData.Uninitialized)
            notFound.eventSink(FindByPhoneEvent.CreateInvite)
            val invited = awaitState { it.invite is AsyncData.Success }
            assertThat(invited.invite.dataOrNull()?.code).isEqualTo("7KQ2-M9XA")
            // Повторный поиск того же номера показывает тот же код, новый не выпускается.
            invites.inviteResult = Result.failure(NyutagInviteException(NyutagInviteException.INVITE_LIMIT, ""))
            invited.eventSink(FindByPhoneEvent.Search)
            awaitState { it.search.isLoading() }
            val again = awaitState { it.search is AsyncData.Success }
            assertThat(again.invite.dataOrNull()?.code).isEqualTo("7KQ2-M9XA")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - own phone can be set when it is missing`() = runTest {
        val invites = FakeNyutagInvites(hasPhoneResult = Result.success(false))
        createPresenter(invites).test {
            val missing = awaitState { it.hasOwnPhone == false }
            missing.eventSink(FindByPhoneEvent.SetOwnPhone("+79001112233"))
            awaitState { it.canSaveOwnPhone }.eventSink(FindByPhoneEvent.SaveOwnPhone)
            val saved = awaitState { it.ownPhoneSave is AsyncAction.Success }
            assertThat(saved.hasOwnPhone).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - network error is shown on search`() = runTest {
        val invites = FakeNyutagInvites(lookupResult = Result.failure(NyutagInviteException(NyutagInviteException.NETWORK, "")))
        createPresenter(invites).test {
            awaitItem().eventSink(FindByPhoneEvent.PickedPhone("+79001112233"))
            val failed = awaitState { it.search is AsyncData.Failure }
            assertThat((failed.search.errorOrNull() as NyutagInviteException).errcode).isEqualTo(NyutagInviteException.NETWORK)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - phone book shows people on Nyutag first and narrows by what is typed`() = runTest {
        val bair = NyutagMatchedContact(name = "Баир", phone = "+7 900 111-22-33", user = MatrixUser(UserId("@bair:nyutag.chat"), "Bair"))
        val book = object : NyutagAddressBook {
            override fun canRead() = true
            override suspend fun phones() = emptyList<String>()
            override suspend fun contacts() = listOf(
                NyutagContact("Аюна", "+7 900 555-66-77"),
                NyutagContact("Баир", "+7 900 111-22-33"),
                NyutagContact("Сэсэг", "8 900 111-00-00"),
            )
        }
        var started: MatrixUser? = null
        val startDM = FakeStartDMAction { user, _, state ->
            started = user
            state.value = AsyncAction.Success(RoomId("!dm:nyutag.chat"))
        }
        val sync = FakeNyutagContactsSync(syncResult = Result.success(listOf(bair)))
        createPresenter(startDM = startDM, addressBook = book, contactsSync = sync).test {
            val loaded = awaitState { it.contacts is AsyncData.Success }
            assertThat(loaded.visibleContacts.map { it.name }).containsExactly("Баир", "Аюна", "Сэсэг").inOrder()
            assertThat(loaded.visibleContacts.first().user?.userId).isEqualTo(bair.user.userId)
            // «8 900 111» находит и «+7 900 111-22-33», и «8 900 111-00-00».
            loaded.eventSink(FindByPhoneEvent.SetPhone("8 900 111"))
            val narrowed = awaitState { it.phone == "8 900 111" }
            assertThat(narrowed.visibleContacts.map { it.name }).containsExactly("Баир", "Сэсэг").inOrder()
            // Тап по тому, кто в Нютаге, открывает чат под именем из книжки.
            narrowed.eventSink(FindByPhoneEvent.PickContact(narrowed.visibleContacts.first()))
            awaitState { it.startDmAction is AsyncAction.Success }
            assertThat(started?.displayName).isEqualTo("Баир")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - without contacts permission the book is not read`() = runTest {
        val sync = FakeNyutagContactsSync()
        createPresenter(addressBook = noContacts(canRead = false), contactsSync = sync).test {
            val state = awaitState { it.hasOwnPhone != null }
            assertThat(state.canReadContacts).isFalse()
            assertThat(sync.syncCalls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun noContacts(canRead: Boolean) = object : NyutagAddressBook {
        override fun canRead() = canRead
        override suspend fun phones() = emptyList<String>()
    }

    private fun createPresenter(
        invites: FakeNyutagInvites = FakeNyutagInvites(),
        client: FakeMatrixClient = FakeMatrixClient(),
        startDM: FakeStartDMAction = FakeStartDMAction(),
        addressBook: NyutagAddressBook = noContacts(canRead = true),
        contactsSync: NyutagContactsSync = FakeNyutagContactsSync(),
    ) = FindByPhonePresenter(invites, client, startDM, addressBook, contactsSync)

    private suspend fun TurbineTestContext<FindByPhoneState>.awaitState(predicate: (FindByPhoneState) -> Boolean): FindByPhoneState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }
}

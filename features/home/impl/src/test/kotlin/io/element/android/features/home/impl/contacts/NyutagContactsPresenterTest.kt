/*
 * Нютаг — вкладка «Контакты» на главной.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.contacts

import com.google.common.truth.Truth.assertThat
import io.element.android.features.invitepeople.test.FakeStartDMAction
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.nyutag.NyutagAddressBook
import io.element.android.libraries.matrix.api.nyutag.NyutagContact
import io.element.android.libraries.matrix.api.nyutag.NyutagMatchedContact
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.nyutag.FakeNyutagContactsSync
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.awaitLastSequentialItem
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class NyutagContactsPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val bair = MatrixUser(UserId("@bair:nyutag.chat"), "Bair")
    private val book = object : NyutagAddressBook {
        override fun canRead() = true
        override suspend fun phones() = emptyList<String>()
        override suspend fun contacts() = listOf(
            NyutagContact("Сэсэг", "8 900 111-00-00"),
            NyutagContact("Аюна", "+7 900 555-66-77"),
            NyutagContact("Баир", "8 (900) 111-22-33"),
        )
    }

    @Test
    fun `present - nothing is read until the tab asks, without permission nothing is read at all`() = runTest {
        val sync = FakeNyutagContactsSync()
        val presenter = NyutagContactsPresenter(FakeMatrixClient(), noContacts(canRead = false), sync, FakeStartDMAction())
        presenter.test {
            val initial = awaitItem()
            assertThat(initial.canReadContacts).isFalse()
            initial.eventSink(NyutagContactsEvent.Load(force = false))
            expectNoEvents()
            assertThat(sync.syncCalls).isEqualTo(0)
        }
    }

    @Test
    fun `present - people on Nyutag go first under their book name, the rest are sorted by name`() = runTest {
        val sync = FakeNyutagContactsSync(syncResult = Result.success(listOf(NyutagMatchedContact("Баир", "+79001112233", bair))))
        val presenter = NyutagContactsPresenter(FakeMatrixClient(), book, sync, FakeStartDMAction())
        presenter.test {
            val initial = awaitItem()
            assertThat(initial.contacts).isEqualTo(AsyncData.Uninitialized)
            initial.eventSink(NyutagContactsEvent.Load(force = false))
            val state = awaitLastSequentialItem()
            assertThat(state.contacts).isInstanceOf(AsyncData.Success::class.java)
            assertThat(state.inNyutag.map { it.user to it.name }).containsExactly(bair to "Баир")
            assertThat(state.others.map { it.name }).containsExactly("Аюна", "Сэсэг").inOrder()
        }
    }

    @Test
    fun `present - server unreachable keeps the book visible, empty book shows the error`() = runTest {
        val failing = FakeNyutagContactsSync(syncResult = Result.failure(Exception("offline")))
        NyutagContactsPresenter(FakeMatrixClient(), book, failing, FakeStartDMAction()).test {
            awaitItem().eventSink(NyutagContactsEvent.Load(force = false))
            val state = awaitLastSequentialItem()
            assertThat(state.contacts).isInstanceOf(AsyncData.Success::class.java)
            assertThat(state.others).hasSize(3)
        }
        NyutagContactsPresenter(FakeMatrixClient(), noContacts(canRead = true), failing, FakeStartDMAction()).test {
            awaitItem().eventSink(NyutagContactsEvent.Load(force = false))
            assertThat(awaitLastSequentialItem().contacts).isInstanceOf(AsyncData.Failure::class.java)
        }
    }

    @Test
    fun `present - tapping a person on Nyutag opens the chat right away, with the book name`() = runTest {
        var started: Pair<MatrixUser, Boolean>? = null
        val startDM = FakeStartDMAction { user, create, state ->
            started = user to create
            state.value = AsyncAction.Success(A_ROOM_ID)
        }
        val presenter = NyutagContactsPresenter(FakeMatrixClient(), book, FakeNyutagContactsSync(), startDM)
        presenter.test {
            awaitItem().eventSink(NyutagContactsEvent.StartDM(NyutagHomeContact("Баир", "+79001112233", bair)))
            assertThat(awaitLastSequentialItem().startDmAction).isEqualTo(AsyncAction.Success(A_ROOM_ID))
            assertThat(started).isEqualTo(bair.copy(displayName = "Баир") to true)
        }
    }

    private fun noContacts(canRead: Boolean) = object : NyutagAddressBook {
        override fun canRead() = canRead
        override suspend fun phones() = emptyList<String>()
    }
}

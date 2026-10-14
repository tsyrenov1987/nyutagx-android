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
import io.element.android.libraries.matrix.api.nyutag.NyutagInvite
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.FakeMatrixClient
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
        FindByPhonePresenter(invites, client, startDM).test {
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
        FindByPhonePresenter(invites, FakeMatrixClient(), FakeStartDMAction()).test {
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
        FindByPhonePresenter(invites, FakeMatrixClient(), FakeStartDMAction()).test {
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
        FindByPhonePresenter(invites, FakeMatrixClient(), FakeStartDMAction()).test {
            awaitItem().eventSink(FindByPhoneEvent.PickedPhone("+79001112233"))
            val failed = awaitState { it.search is AsyncData.Failure }
            assertThat((failed.search.errorOrNull() as NyutagInviteException).errcode).isEqualTo(NyutagInviteException.NETWORK)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun TurbineTestContext<FindByPhoneState>.awaitState(predicate: (FindByPhoneState) -> Boolean): FindByPhoneState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }
}

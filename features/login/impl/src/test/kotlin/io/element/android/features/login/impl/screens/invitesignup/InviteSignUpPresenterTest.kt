/*
 * Нютаг — регистрация по шагам.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.invitesignup

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.impl.accountprovider.SaveAccountProviderToHistory
import io.element.android.features.login.impl.accountprovider.anAccountProviderDataSource
import io.element.android.libraries.matrix.api.nyutag.NyutagContactMedium
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.nyutag.NyutagSignUpConfig
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.libraries.matrix.test.auth.aMatrixHomeServerDetails
import io.element.android.libraries.matrix.test.nyutag.FakeNyutagInvites
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InviteSignUpPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val code = "7KQ2-M9XA"

    @Test
    fun `present - without contact channels there are three steps`() = runTest {
        createPresenter().test {
            val state = awaitState { it.steps.size == 3 }
            assertThat(state.steps).containsExactly(SignUpStep.Code, SignUpStep.Name, SignUpStep.Password).inOrder()
            assertThat(state.step).isEqualTo(SignUpStep.Code)
            assertThat(state.stepNumber).isEqualTo(1)
            assertThat(state.canContinue).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - full code is checked automatically and shows the inviter`() = runTest {
        createPresenter().test {
            awaitItem().eventSink(InviteSignUpEvent.SetCode(code))
            val checked = awaitState { it.isCodeValid }
            assertThat(checked.inviter).isEqualTo("@bob:nyutag.chat")
            assertThat(checked.step).isEqualTo(SignUpStep.Code)
            checked.eventSink(InviteSignUpEvent.Next)
            assertThat(awaitState { it.step == SignUpStep.Name }.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - code accepts lowercase and look-alike Cyrillic letters`() = runTest {
        createPresenter().test {
            awaitItem().eventSink(InviteSignUpEvent.SetCode("7кq2 м9ха"))
            assertThat(awaitState { it.form.code.isNotEmpty() }.form.code).isEqualTo(code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - unknown code shows an error and does not advance`() = runTest {
        val invites = FakeNyutagInvites(checkInviteResult = Result.failure(NyutagInviteException(NyutagInviteException.UNKNOWN_CODE, "")))
        createPresenter(invites = invites).test {
            awaitItem().eventSink(InviteSignUpEvent.SetCode(code))
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat((failed.error as NyutagInviteException).errcode).isEqualTo(NyutagInviteException.UNKNOWN_CODE)
            assertThat(failed.isCodeValid).isFalse()
            failed.eventSink(InviteSignUpEvent.Next)
            val again = awaitState { it.error != null && !it.isBusy }
            assertThat(again.step).isEqualTo(SignUpStep.Code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - contact step sends a code and checks it automatically`() = runTest {
        val invites = FakeNyutagInvites(config = Result.success(NyutagSignUpConfig(true, true, emailAvailable = true, phoneAvailable = true)))
        createPresenter(invites = invites).test {
            awaitState { it.steps.size == 4 }.eventSink(InviteSignUpEvent.SetCode(code))
            awaitState { it.isCodeValid }.eventSink(InviteSignUpEvent.Next)
            val contact = awaitState { it.step == SignUpStep.Contact }
            assertThat(contact.canSkipContact).isFalse()
            assertThat(contact.availableMedia).hasSize(2)
            contact.eventSink(InviteSignUpEvent.SetContact("+79000000000"))
            awaitState { it.canContinue }.eventSink(InviteSignUpEvent.Next)
            val sent = awaitState { it.verification != null }
            assertThat(sent.resendInSeconds).isEqualTo(60)
            sent.eventSink(InviteSignUpEvent.SetVerifyCode("12-34-56"))
            assertThat(awaitState { it.step == SignUpStep.Name }.form.verifyCode).isEqualTo("123456")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - email and phone are both required, phone without SMS goes on its own step`() = runTest {
        val config = NyutagSignUpConfig(
            requireInvite = true,
            requireContact = true,
            emailAvailable = true,
            phoneAvailable = false,
            requireEmail = true,
            requirePhone = true,
            phoneUnverified = true,
        )
        val invites = FakeNyutagInvites(config = Result.success(config), checkPhoneResult = Result.success("+79001234567"))
        createPresenter(invites = invites, appScope = this).test {
            val first = awaitState { it.steps.size == 5 }
            assertThat(first.steps).containsExactly(SignUpStep.Code, SignUpStep.Contact, SignUpStep.Phone, SignUpStep.Name, SignUpStep.Password).inOrder()
            first.eventSink(InviteSignUpEvent.SetCode(code))
            awaitState { it.isCodeValid }.eventSink(InviteSignUpEvent.Next)
            val contact = awaitState { it.step == SignUpStep.Contact }
            // Почта обязательна: пропустить нельзя, и телефона среди способов нет — у него свой шаг.
            assertThat(contact.canSkipContact).isFalse()
            assertThat(contact.availableMedia).containsExactly(NyutagContactMedium.Email)
            contact.eventSink(InviteSignUpEvent.SetContact("dorzho@example.com"))
            awaitState { it.canContinue }.eventSink(InviteSignUpEvent.Next)
            awaitState { it.verification != null }.eventSink(InviteSignUpEvent.SetVerifyCode("123456"))
            val phone = awaitState { it.step == SignUpStep.Phone }
            assertThat(phone.canContinue).isFalse()
            phone.eventSink(InviteSignUpEvent.SetPhone("8 900 123"))
            assertThat(awaitState { it.form.phone == "8 900 123" }.canContinue).isFalse()
            phone.eventSink(InviteSignUpEvent.SetPhone("8 900 123-45-67"))
            awaitState { it.canContinue }.eventSink(InviteSignUpEvent.Next)
            awaitState { it.step == SignUpStep.Name }.eventSink(InviteSignUpEvent.SetDisplayName("Доржо"))
            awaitState { it.canContinue }.eventSink(InviteSignUpEvent.Next)
            val password = awaitState { it.step == SignUpStep.Password }
            password.eventSink(InviteSignUpEvent.SetPassword("password1"))
            password.eventSink(InviteSignUpEvent.SetPasswordRepeat("password1"))
            awaitState { it.canContinue }.eventSink(InviteSignUpEvent.Next)
            awaitState { it.isBusy }
            assertThat(awaitState { !it.isBusy }.error).isNull()
            assertThat(invites.registeredSid).isEqualTo("sid")
            assertThat(invites.registeredPhone).isEqualTo("+79001234567")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - taken phone shows an error and stays on the phone step`() = runTest {
        val config = NyutagSignUpConfig(true, false, false, false, requirePhone = true, phoneUnverified = true)
        val invites = FakeNyutagInvites(
            config = Result.success(config),
            checkPhoneResult = Result.failure(NyutagInviteException(NyutagInviteException.CONTACT_IN_USE, "")),
        )
        createPresenter(invites = invites).test {
            awaitState { it.steps.size == 4 }.eventSink(InviteSignUpEvent.SetCode(code))
            awaitState { it.isCodeValid }.eventSink(InviteSignUpEvent.Next)
            awaitState { it.step == SignUpStep.Phone }.eventSink(InviteSignUpEvent.SetPhone("+79001234567"))
            awaitState { it.canContinue }.eventSink(InviteSignUpEvent.Next)
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat((failed.error as NyutagInviteException).errcode).isEqualTo(NyutagInviteException.CONTACT_IN_USE)
            assertThat(failed.step).isEqualTo(SignUpStep.Phone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - code from an invitation link is filled in and checked`() = runTest {
        createPresenter(initialCode = "7kq2m9xa").test {
            val checked = awaitState { it.isCodeValid }
            assertThat(checked.form.code).isEqualTo(code)
            assertThat(checked.inviter).isEqualTo("@bob:nyutag.chat")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - username comes from the name and a taken one can be edited`() = runTest {
        val invites = FakeNyutagInvites(checkUsernameResult = Result.failure(NyutagInviteException(NyutagInviteException.USER_IN_USE, "")))
        createPresenter(invites = invites).test {
            val name = goToName()
            name.eventSink(InviteSignUpEvent.SetDisplayName("Доржо Цыренов"))
            val typed = awaitState { it.form.displayName.isNotEmpty() }
            assertThat(typed.username).isEqualTo("dorzho.tsyrenov")
            typed.eventSink(InviteSignUpEvent.Next)
            val taken = awaitState { it.error != null && !it.isBusy }
            assertThat(invites.checkedUsernames).containsExactly("dorzho.tsyrenov")
            assertThat(taken.form.isEditingUsername).isTrue()
            assertThat(taken.form.username).isEqualTo("dorzho.tsyrenov")
            assertThat(taken.step).isEqualTo(SignUpStep.Name)
            invites.checkUsernameResult = Result.success(Unit)
            taken.eventSink(InviteSignUpEvent.SetUsername("Dorzho_88"))
            awaitState { it.form.username == "Dorzho_88" }.eventSink(InviteSignUpEvent.Next)
            awaitState { it.step == SignUpStep.Password }
            assertThat(invites.checkedUsernames.last()).isEqualTo("dorzho_88")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - passwords must match before the account is created`() = runTest {
        val invites = FakeNyutagInvites()
        val client = FakeMatrixClient()
        // Имя ставится в области приложения уже после входа — даём ей доработать до проверки.
        createPresenter(invites = invites, client = client, appScope = this).test {
            val password = goToPassword()
            password.eventSink(InviteSignUpEvent.SetPassword("password1"))
            password.eventSink(InviteSignUpEvent.SetPasswordRepeat("password2"))
            val mismatch = awaitState { it.form.passwordRepeat == "password2" }
            assertThat(mismatch.showsPasswordMismatch).isTrue()
            assertThat(mismatch.canContinue).isFalse()
            mismatch.eventSink(InviteSignUpEvent.SetPasswordRepeat("password1"))
            val ready = awaitState { it.canContinue }
            assertThat(ready.showsPasswordMismatch).isFalse()
            ready.eventSink(InviteSignUpEvent.Next)
            awaitState { it.isBusy }
            val done = awaitState { !it.isBusy }
            assertThat(done.error).isNull()
            assertThat(invites.registeredUsername).isEqualTo("dorzho")
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        assertThat(client.setDisplayNameCalled).isTrue()
    }

    @Test
    fun `present - failed sign-in after registration is retried without registering again`() = runTest {
        val invites = FakeNyutagInvites()
        val authenticationService = anAuthenticationService().apply { givenLoginError(IllegalStateException("offline")) }
        createPresenter(invites = invites, authenticationService = authenticationService, appScope = backgroundScope).test {
            val password = goToPassword()
            password.eventSink(InviteSignUpEvent.SetPassword("password1"))
            password.eventSink(InviteSignUpEvent.SetPasswordRepeat("password1"))
            awaitState { it.canContinue }.eventSink(InviteSignUpEvent.Next)
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat((failed.error as NyutagInviteException).errcode).isEqualTo(InviteSignUpPresenter.LOGIN_AFTER_REGISTER)
            // Повторная регистрация упала бы с этой ошибкой — значит, её нет.
            invites.registerResult = Result.failure(NyutagInviteException(NyutagInviteException.USER_IN_USE, ""))
            authenticationService.givenLoginError(null)
            failed.eventSink(InviteSignUpEvent.Next)
            awaitState { it.isBusy }
            assertThat(awaitState { !it.isBusy }.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - back returns to the previous step`() = runTest {
        createPresenter().test {
            val name = goToName()
            name.eventSink(InviteSignUpEvent.Back)
            val code = awaitState { it.step == SignUpStep.Code }
            assertThat(code.form.code).isEqualTo(this@InviteSignUpPresenterTest.code)
            assertThat(code.isCodeValid).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun TurbineTestContext<InviteSignUpState>.goToName(): InviteSignUpState {
        awaitItem().eventSink(InviteSignUpEvent.SetCode(code))
        awaitState { it.isCodeValid }.eventSink(InviteSignUpEvent.Next)
        return awaitState { it.step == SignUpStep.Name }
    }

    private suspend fun TurbineTestContext<InviteSignUpState>.goToPassword(): InviteSignUpState {
        goToName().eventSink(InviteSignUpEvent.SetDisplayName("Доржо"))
        awaitState { it.canContinue }.eventSink(InviteSignUpEvent.Next)
        return awaitState { it.step == SignUpStep.Password }
    }

    private suspend fun TurbineTestContext<InviteSignUpState>.awaitState(predicate: (InviteSignUpState) -> Boolean): InviteSignUpState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private fun anAuthenticationService() = FakeMatrixAuthenticationService(setHomeserverResult = { Result.success(aMatrixHomeServerDetails()) })

    private fun TestScope.createPresenter(
        invites: FakeNyutagInvites = FakeNyutagInvites(),
        authenticationService: FakeMatrixAuthenticationService = anAuthenticationService(),
        client: FakeMatrixClient = FakeMatrixClient(),
        appScope: CoroutineScope = backgroundScope,
        initialCode: String = "",
    ): InviteSignUpPresenter {
        val accountProviderDataSource = anAccountProviderDataSource()
        return InviteSignUpPresenter(
            invites = invites,
            authenticationService = authenticationService,
            accountProviderDataSource = accountProviderDataSource,
            saveAccountProviderToHistory = SaveAccountProviderToHistory(accountProviderDataSource, InMemoryAppPreferencesStore()),
            matrixClientProvider = FakeMatrixClientProvider { Result.success(client) },
            appCoroutineScope = appScope,
        ).also { it.initialCode = initialCode }
    }
}

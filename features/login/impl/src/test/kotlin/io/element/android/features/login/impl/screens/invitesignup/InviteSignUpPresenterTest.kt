/*
 * Нютаг — регистрация в одну форму.
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

    /** Как на сервере сейчас: без кода, почта с кодом из письма и телефон без SMS обязательны. */
    private val production = NyutagSignUpConfig(
        requireInvite = false,
        requireContact = true,
        emailAvailable = true,
        phoneAvailable = false,
        requireEmail = true,
        requirePhone = true,
        phoneUnverified = true,
    )

    @Test
    fun `present - the form shows only the fields the server asks for`() = runTest {
        createPresenter(invites = FakeNyutagInvites(config = Result.success(production))).test {
            val state = awaitState { it.contactMedium != null }
            assertThat(state.step).isEqualTo(SignUpStep.Form)
            assertThat(state.showsCode).isFalse()
            assertThat(state.showsPhone).isTrue()
            assertThat(state.contactMedium).isEqualTo(NyutagContactMedium.Email)
            assertThat(state.isContactOptional).isFalse()
            assertThat(state.canContinue).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        val bare = NyutagSignUpConfig(requireInvite = false, requireContact = false, emailAvailable = false, phoneAvailable = false)
        createPresenter(invites = FakeNyutagInvites(config = Result.success(bare))).test {
            val state = awaitState { !it.showsCode }
            assertThat(state.showsPhone).isFalse()
            assertThat(state.contactMedium).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - the code field appears when the server requires an invitation or the code came from a link`() = runTest {
        createPresenter().test {
            val state = awaitState { it.showsCode }
            assertThat(state.showsCode).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        val bare = NyutagSignUpConfig(requireInvite = false, requireContact = false, emailAvailable = false, phoneAvailable = false)
        createPresenter(invites = FakeNyutagInvites(config = Result.success(bare)), initialCode = code).test {
            val state = awaitState { it.isCodeValid }
            assertThat(state.showsCode).isTrue()
            assertThat(state.form.code).isEqualTo(code)
            assertThat(state.inviter).isEqualTo("@bob:nyutag.chat")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - full code is checked automatically and accepts look-alike Cyrillic letters`() = runTest {
        createPresenter().test {
            awaitItem().eventSink(InviteSignUpEvent.SetCode("7кq2 м9ха"))
            val checked = awaitState { it.isCodeValid }
            assertThat(checked.form.code).isEqualTo(code)
            assertThat(checked.inviter).isEqualTo("@bob:nyutag.chat")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - unknown code shows an error under the code field`() = runTest {
        val invites = FakeNyutagInvites(checkInviteResult = Result.failure(NyutagInviteException(NyutagInviteException.UNKNOWN_CODE, "")))
        createPresenter(invites = invites).test {
            awaitItem().eventSink(InviteSignUpEvent.SetCode(code))
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat((failed.error as NyutagInviteException).errcode).isEqualTo(NyutagInviteException.UNKNOWN_CODE)
            assertThat(failed.errorField).isEqualTo(SignUpField.Code)
            assertThat(failed.isCodeValid).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - username comes from the name and a taken one can be edited`() = runTest {
        val invites = FakeNyutagInvites(checkUsernameResult = Result.failure(NyutagInviteException(NyutagInviteException.USER_IN_USE, "")))
        createPresenter(invites = invites).test {
            val form = fillForm()
            assertThat(form.username).isEqualTo("dorzho.tsyrenov")
            form.eventSink(InviteSignUpEvent.Next)
            val taken = awaitState { it.error != null && !it.isBusy }
            assertThat(invites.checkedUsernames).containsExactly("dorzho.tsyrenov")
            assertThat(taken.errorField).isEqualTo(SignUpField.Username)
            assertThat(taken.form.isEditingUsername).isTrue()
            assertThat(taken.form.username).isEqualTo("dorzho.tsyrenov")
            assertThat(taken.step).isEqualTo(SignUpStep.Form)
            invites.checkUsernameResult = Result.success(Unit)
            taken.eventSink(InviteSignUpEvent.SetUsername("Dorzho_88"))
            awaitState { it.form.username == "Dorzho_88" && it.canContinue }.eventSink(InviteSignUpEvent.Next)
            awaitState { it.isBusy }
            assertThat(awaitState { !it.isBusy }.error).isNull()
            assertThat(invites.checkedUsernames.last()).isEqualTo("dorzho_88")
            assertThat(invites.registeredUsername).isEqualTo("dorzho_88")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - email and phone are both required, the code from the email comes after the form`() = runTest {
        val invites = FakeNyutagInvites(config = Result.success(production), checkPhoneResult = Result.success("+79001234567"))
        createPresenter(invites = invites, appScope = this).test {
            val state = awaitState { it.contactMedium != null }
            state.eventSink(InviteSignUpEvent.SetDisplayName("Доржо"))
            state.eventSink(InviteSignUpEvent.SetPhone("8 900 123"))
            state.eventSink(InviteSignUpEvent.SetContact("dorzho@example.com"))
            state.eventSink(InviteSignUpEvent.SetPassword("password1"))
            // Телефон слишком короткий — кнопка не активна.
            assertThat(awaitState { it.form.password == "password1" }.canContinue).isFalse()
            state.eventSink(InviteSignUpEvent.SetPhone("8 900 123-45-67"))
            awaitState { it.canContinue }.eventSink(InviteSignUpEvent.Next)
            val verify = awaitState { it.step == SignUpStep.Verify }
            assertThat(verify.verification).isNotNull()
            assertThat(verify.resendInSeconds).isEqualTo(60)
            assertThat(verify.canContinue).isFalse()
            assertThat(invites.registeredUsername).isNull()
            // Код введён целиком — проверяется и регистрирует сам.
            verify.eventSink(InviteSignUpEvent.SetVerifyCode("12-34-56"))
            awaitState { it.isBusy }
            assertThat(awaitState { !it.isBusy }.error).isNull()
            assertThat(invites.registeredSid).isEqualTo("sid")
            assertThat(invites.registeredPhone).isEqualTo("+79001234567")
            assertThat(invites.registeredUsername).isEqualTo("dorzho")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - taken phone shows an error under the phone field and stays on the form`() = runTest {
        val invites = FakeNyutagInvites(
            config = Result.success(production),
            checkPhoneResult = Result.failure(NyutagInviteException(NyutagInviteException.CONTACT_IN_USE, "")),
        )
        createPresenter(invites = invites).test {
            val state = awaitState { it.contactMedium != null }
            state.eventSink(InviteSignUpEvent.SetDisplayName("Доржо"))
            state.eventSink(InviteSignUpEvent.SetPhone("+79001234567"))
            state.eventSink(InviteSignUpEvent.SetContact("dorzho@example.com"))
            state.eventSink(InviteSignUpEvent.SetPassword("password1"))
            awaitState { it.canContinue }.eventSink(InviteSignUpEvent.Next)
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat((failed.error as NyutagInviteException).errcode).isEqualTo(NyutagInviteException.CONTACT_IN_USE)
            assertThat(failed.errorField).isEqualTo(SignUpField.Phone)
            assertThat(failed.step).isEqualTo(SignUpStep.Form)
            assertThat(failed.verification).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - wrong email code shows an error and back returns to the form`() = runTest {
        val invites = FakeNyutagInvites(
            config = Result.success(production),
            checkResult = Result.failure(NyutagInviteException(NyutagInviteException.VERIFY_WRONG, "")),
        )
        createPresenter(invites = invites).test {
            val state = awaitState { it.contactMedium != null }
            state.eventSink(InviteSignUpEvent.SetDisplayName("Доржо"))
            state.eventSink(InviteSignUpEvent.SetPhone("+79001234567"))
            state.eventSink(InviteSignUpEvent.SetContact("dorzho@example.com"))
            state.eventSink(InviteSignUpEvent.SetPassword("password1"))
            awaitState { it.canContinue }.eventSink(InviteSignUpEvent.Next)
            val verify = awaitState { it.step == SignUpStep.Verify }
            verify.eventSink(InviteSignUpEvent.SetVerifyCode("000000"))
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat(failed.errorField).isEqualTo(SignUpField.VerifyCode)
            assertThat(invites.registeredUsername).isNull()
            failed.eventSink(InviteSignUpEvent.Back)
            val form = awaitState { it.step == SignUpStep.Form }
            assertThat(form.form.contact).isEqualTo("dorzho@example.com")
            assertThat(form.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - without contact channels the account is created straight from the form`() = runTest {
        val invites = FakeNyutagInvites()
        val client = FakeMatrixClient()
        // Имя ставится в области приложения уже после входа — даём ей доработать до проверки.
        createPresenter(invites = invites, client = client, appScope = this).test {
            fillForm().eventSink(InviteSignUpEvent.Next)
            awaitState { it.isBusy }
            val done = awaitState { !it.isBusy }
            assertThat(done.error).isNull()
            assertThat(done.step).isEqualTo(SignUpStep.Form)
            assertThat(invites.registeredUsername).isEqualTo("dorzho.tsyrenov")
            assertThat(invites.registeredSid).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        assertThat(client.setDisplayNameCalled).isTrue()
    }

    @Test
    fun `present - a short password keeps the button disabled`() = runTest {
        createPresenter().test {
            val form = fillForm()
            form.eventSink(InviteSignUpEvent.SetPassword("1234567"))
            assertThat(awaitState { it.form.password == "1234567" }.canContinue).isFalse()
            form.eventSink(InviteSignUpEvent.SetPassword("12345678"))
            assertThat(awaitState { it.form.password == "12345678" }.canContinue).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - failed sign-in after registration is retried without registering again`() = runTest {
        val invites = FakeNyutagInvites()
        val authenticationService = anAuthenticationService().apply { givenLoginError(IllegalStateException("offline")) }
        createPresenter(invites = invites, authenticationService = authenticationService, appScope = backgroundScope).test {
            fillForm().eventSink(InviteSignUpEvent.Next)
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat((failed.error as NyutagInviteException).errcode).isEqualTo(InviteSignUpPresenter.LOGIN_AFTER_REGISTER)
            assertThat(failed.errorField).isEqualTo(SignUpField.Password)
            // Повторная регистрация упала бы с этой ошибкой — значит, её нет.
            invites.registerResult = Result.failure(NyutagInviteException(NyutagInviteException.USER_IN_USE, ""))
            authenticationService.givenLoginError(null)
            failed.eventSink(InviteSignUpEvent.Next)
            awaitState { it.isBusy }
            assertThat(awaitState { !it.isBusy }.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Конфигурация по умолчанию (код обязателен, контактов нет): код, имя и пароль — кнопка активна. */
    private suspend fun TurbineTestContext<InviteSignUpState>.fillForm(): InviteSignUpState {
        awaitItem().eventSink(InviteSignUpEvent.SetCode(code))
        val checked = awaitState { it.isCodeValid }
        checked.eventSink(InviteSignUpEvent.SetDisplayName("Доржо Цыренов"))
        checked.eventSink(InviteSignUpEvent.SetPassword("password1"))
        return awaitState { it.canContinue }
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

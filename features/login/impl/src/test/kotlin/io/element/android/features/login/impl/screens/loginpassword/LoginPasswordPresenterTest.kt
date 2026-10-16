/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.loginpassword

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.accountprovider.SaveAccountProviderToHistory
import io.element.android.features.login.impl.accountprovider.anAccountProviderDataSource
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.accountprovider.matrixOrgAccountProvider
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.nyutag.NyutagVerification
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.A_PASSWORD
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_USER_NAME
import io.element.android.libraries.matrix.test.A_USER_NAME_2
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.libraries.matrix.test.auth.aMatrixHomeServerDetails
import io.element.android.libraries.matrix.test.nyutag.FakeNyutagInvites
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class LoginPasswordPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state`() = runTest {
        createLoginPasswordPresenter().test {
            val initialState = awaitItem()
            assertThat(initialState.accountProvider).isEqualTo(matrixOrgAccountProvider)
            assertThat(initialState.formState).isEqualTo(LoginFormState.Default)
            assertThat(initialState.loginAction).isEqualTo(AsyncData.Uninitialized)
            assertThat(initialState.submitEnabled).isFalse()
        }
    }

    @Test
    fun `present - initial login is in the first state and can be modified`() = runTest {
        createLoginPasswordPresenter(
            initialLogin = A_USER_NAME,
        ).test {
            val initialState = awaitItem()
            assertThat(initialState.formState.login).isEqualTo(A_USER_NAME)
            // Login can be changed
            initialState.eventSink.invoke(LoginPasswordEvent.SetLogin(A_USER_NAME_2))
            val loginChangedState = awaitItem()
            assertThat(loginChangedState.formState.login).isEqualTo(A_USER_NAME_2)
        }
    }

    @Test
    fun `present - enter login and password`() = runTest {
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = {
                Result.success(aMatrixHomeServerDetails())
            },
        )
        createLoginPasswordPresenter(
            authenticationService = authenticationService,
        ).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(LoginPasswordEvent.SetLogin(A_USER_NAME))
            val loginState = awaitItem()
            assertThat(loginState.formState).isEqualTo(LoginFormState(login = A_USER_NAME, password = ""))
            assertThat(loginState.submitEnabled).isFalse()
            initialState.eventSink.invoke(LoginPasswordEvent.SetPassword(A_PASSWORD))
            val loginAndPasswordState = awaitItem()
            assertThat(loginAndPasswordState.formState).isEqualTo(LoginFormState(login = A_USER_NAME, password = A_PASSWORD))
            assertThat(loginAndPasswordState.submitEnabled).isTrue()
        }
    }

    @Test
    fun `present - submit`() = runTest {
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = {
                Result.success(aMatrixHomeServerDetails())
            },
        )
        createLoginPasswordPresenter(
            authenticationService = authenticationService,
        ).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(LoginPasswordEvent.SetLogin(A_USER_NAME))
            initialState.eventSink.invoke(LoginPasswordEvent.SetPassword(A_PASSWORD))
            skipItems(1)
            val loginAndPasswordState = awaitItem()
            loginAndPasswordState.eventSink.invoke(LoginPasswordEvent.Submit)
            val submitState = awaitItem()
            assertThat(submitState.loginAction).isInstanceOf(AsyncData.Loading::class.java)
            val loggedInState = awaitItem()
            assertThat(loggedInState.loginAction).isEqualTo(AsyncData.Success(A_SESSION_ID))
        }
    }

    @Test
    fun `present - successful login saves the account provider to history`() = runTest {
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = {
                Result.success(aMatrixHomeServerDetails())
            },
        )
        val appPreferencesStore = InMemoryAppPreferencesStore()
        createLoginPasswordPresenter(
            authenticationService = authenticationService,
            appPreferencesStore = appPreferencesStore,
        ).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(LoginPasswordEvent.SetLogin(A_USER_NAME))
            initialState.eventSink.invoke(LoginPasswordEvent.SetPassword(A_PASSWORD))
            skipItems(1)
            val loginAndPasswordState = awaitItem()
            loginAndPasswordState.eventSink.invoke(LoginPasswordEvent.Submit)
            skipItems(1)
            val loggedInState = awaitItem()
            assertThat(loggedInState.loginAction).isEqualTo(AsyncData.Success(A_SESSION_ID))
            assertThat(appPreferencesStore.getHomeserverHistoryFlow().first())
                .containsExactly(matrixOrgAccountProvider.serverName)
        }
    }

    @Test
    fun `present - submit with error`() = runTest {
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = {
                Result.success(aMatrixHomeServerDetails())
            },
        )
        createLoginPasswordPresenter(
            authenticationService = authenticationService,
        ).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(LoginPasswordEvent.SetLogin(A_USER_NAME))
            initialState.eventSink.invoke(LoginPasswordEvent.SetPassword(A_PASSWORD))
            skipItems(1)
            val loginAndPasswordState = awaitItem()
            authenticationService.givenLoginError(AN_EXCEPTION)
            loginAndPasswordState.eventSink.invoke(LoginPasswordEvent.Submit)
            val submitState = awaitItem()
            assertThat(submitState.loginAction).isInstanceOf(AsyncData.Loading::class.java)
            val loggedInState = awaitItem()
            assertThat(loggedInState.loginAction).isEqualTo(AsyncData.Failure<SessionId>(AN_EXCEPTION))
        }
    }

    @Test
    fun `present - clear error`() = runTest {
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = {
                Result.success(aMatrixHomeServerDetails())
            },
        )
        createLoginPasswordPresenter(
            authenticationService = authenticationService,
        ).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(LoginPasswordEvent.SetLogin(A_USER_NAME))
            initialState.eventSink.invoke(LoginPasswordEvent.SetPassword(A_PASSWORD))
            skipItems(1)
            val loginAndPasswordState = awaitItem()
            authenticationService.givenLoginError(AN_EXCEPTION)
            loginAndPasswordState.eventSink.invoke(LoginPasswordEvent.Submit)
            val submitState = awaitItem()
            assertThat(submitState.loginAction).isInstanceOf(AsyncData.Loading::class.java)
            val loggedInState = awaitItem()
            // Check an error was returned
            assertThat(loggedInState.loginAction).isEqualTo(AsyncData.Failure<SessionId>(AN_EXCEPTION))
            // Assert the error is then cleared
            loggedInState.eventSink(LoginPasswordEvent.ClearError)
            val clearedState = awaitItem()
            assertThat(clearedState.loginAction).isEqualTo(AsyncData.Uninitialized)
        }
    }

    @Test
    fun `present - forgot password opens with the email login and closes`() = runTest {
        createLoginPasswordPresenter(
            initialLogin = AN_EMAIL,
        ).test {
            val initialState = awaitItem()
            assertThat(initialState.passwordReset).isNull()
            initialState.eventSink(LoginPasswordEvent.OpenPasswordReset)
            val resetState = awaitItem()
            assertThat(resetState.passwordReset).isEqualTo(NyutagPasswordResetState(email = AN_EMAIL))
            resetState.eventSink(LoginPasswordEvent.ClosePasswordReset)
            assertThat(awaitItem().passwordReset).isNull()
        }
    }

    @Test
    fun `present - forgot password does not take a matrix id as the email`() = runTest {
        createLoginPasswordPresenter(
            initialLogin = "@alice:nyutag.chat",
        ).test {
            awaitItem().eventSink(LoginPasswordEvent.OpenPasswordReset)
            assertThat(awaitItem().passwordReset?.email).isEmpty()
        }
    }

    @Test
    fun `present - send reset code moves to the code step`() = runTest {
        val nyutagInvites = FakeNyutagInvites()
        createLoginPasswordPresenter(
            nyutagInvites = nyutagInvites,
        ).test {
            awaitItem().eventSink(LoginPasswordEvent.OpenPasswordReset)
            val emailState = awaitItem()
            assertThat(emailState.passwordReset?.canSendCode).isFalse()
            emailState.eventSink(LoginPasswordEvent.SetResetEmail(" $AN_EMAIL "))
            awaitItem().eventSink(LoginPasswordEvent.SendResetCode)
            val codeState = consumeItemsUntilPredicate { it.passwordReset?.sid != null }.last().passwordReset
            assertThat(codeState?.sid).isEqualTo("reset-sid")
            assertThat(codeState?.resendInSeconds).isEqualTo(60)
            assertThat(codeState?.isBusy).isFalse()
            assertThat(nyutagInvites.passwordResetAddress).isEqualTo(AN_EMAIL)
            // The resend button counts down.
            assertThat(awaitItem().passwordReset?.resendInSeconds).isEqualTo(59)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - send reset code to an unknown email shows the error`() = runTest {
        val error = NyutagInviteException(NyutagInviteException.CONTACT_UNKNOWN, "unknown")
        val nyutagInvites = FakeNyutagInvites().apply {
            passwordResetStartResult = Result.failure(error)
        }
        createLoginPasswordPresenter(
            initialLogin = AN_EMAIL,
            nyutagInvites = nyutagInvites,
        ).test {
            awaitItem().eventSink(LoginPasswordEvent.OpenPasswordReset)
            awaitItem().eventSink(LoginPasswordEvent.SendResetCode)
            val failedState = consumeItemsUntilPredicate { it.passwordReset?.error != null }.last().passwordReset
            assertThat(failedState?.error).isEqualTo(error)
            assertThat(failedState?.sid).isNull()
            assertThat(failedState?.isBusy).isFalse()
        }
    }

    @Test
    fun `present - password reset logs in with the new password`() = runTest {
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = {
                Result.success(aMatrixHomeServerDetails())
            },
        )
        val nyutagInvites = FakeNyutagInvites().apply {
            passwordResetStartResult = Result.success(NyutagVerification("reset-sid", 0))
        }
        createLoginPasswordPresenter(
            initialLogin = AN_EMAIL,
            authenticationService = authenticationService,
            nyutagInvites = nyutagInvites,
        ).test {
            val readyState = awaitResetReadyToSubmit()
            readyState.eventSink(LoginPasswordEvent.SubmitPasswordReset)
            val loggedInState = consumeItemsUntilPredicate { it.loginAction is AsyncData.Success }.last()
            assertThat(loggedInState.passwordReset).isNull()
            assertThat(loggedInState.formState).isEqualTo(LoginFormState(login = AN_EMAIL, password = A_NEW_PASSWORD))
            assertThat(loggedInState.loginAction).isEqualTo(AsyncData.Success(A_SESSION_ID))
            assertThat(nyutagInvites.passwordResetSid).isEqualTo("reset-sid")
        }
    }

    @Test
    fun `present - password reset with a wrong code stays on the code step`() = runTest {
        val error = NyutagInviteException(NyutagInviteException.VERIFY_WRONG, "wrong")
        val nyutagInvites = FakeNyutagInvites().apply {
            passwordResetStartResult = Result.success(NyutagVerification("reset-sid", 0))
            passwordResetResult = Result.failure(error)
        }
        createLoginPasswordPresenter(
            initialLogin = AN_EMAIL,
            nyutagInvites = nyutagInvites,
        ).test {
            awaitResetReadyToSubmit().eventSink(LoginPasswordEvent.SubmitPasswordReset)
            val failedState = consumeItemsUntilPredicate { it.passwordReset?.error != null }.last()
            assertThat(failedState.passwordReset?.error).isEqualTo(error)
            assertThat(failedState.passwordReset?.sid).isEqualTo("reset-sid")
            assertThat(failedState.loginAction).isEqualTo(AsyncData.Uninitialized)
        }
    }

    @Test
    fun `present - password reset with an expired code goes back to the email step`() = runTest {
        val error = NyutagInviteException(NyutagInviteException.VERIFY_EXPIRED, "expired")
        val nyutagInvites = FakeNyutagInvites().apply {
            passwordResetStartResult = Result.success(NyutagVerification("reset-sid", 0))
            passwordResetResult = Result.failure(error)
        }
        createLoginPasswordPresenter(
            initialLogin = AN_EMAIL,
            nyutagInvites = nyutagInvites,
        ).test {
            awaitResetReadyToSubmit().eventSink(LoginPasswordEvent.SubmitPasswordReset)
            val failedState = consumeItemsUntilPredicate { it.passwordReset?.error != null }.last()
            assertThat(failedState.passwordReset?.error).isEqualTo(error)
            assertThat(failedState.passwordReset?.sid).isNull()
            assertThat(failedState.passwordReset?.email).isEqualTo(AN_EMAIL)
            assertThat(failedState.loginAction).isEqualTo(AsyncData.Uninitialized)
        }
    }

    private suspend fun TurbineTestContext<LoginPasswordState>.awaitResetReadyToSubmit(): LoginPasswordState {
        awaitItem().eventSink(LoginPasswordEvent.OpenPasswordReset)
        awaitItem().eventSink(LoginPasswordEvent.SendResetCode)
        val codeState = consumeItemsUntilPredicate { it.passwordReset?.sid != null }.last()
        codeState.eventSink(LoginPasswordEvent.SetResetCode("123456"))
        codeState.eventSink(LoginPasswordEvent.SetResetPassword(A_NEW_PASSWORD))
        return consumeItemsUntilPredicate { it.passwordReset?.canSubmit == true }.last()
    }

    private fun createLoginPasswordPresenter(
        initialLogin: String = "",
        authenticationService: FakeMatrixAuthenticationService = FakeMatrixAuthenticationService(),
        accountProviderDataSource: AccountProviderDataSource = anAccountProviderDataSource(),
        appPreferencesStore: AppPreferencesStore = InMemoryAppPreferencesStore(),
        nyutagInvites: FakeNyutagInvites = FakeNyutagInvites(),
    ): LoginPasswordPresenter = LoginPasswordPresenter(
        initialLogin = initialLogin,
        authenticationService = authenticationService,
        accountProviderDataSource = accountProviderDataSource,
        saveAccountProviderToHistory = SaveAccountProviderToHistory(accountProviderDataSource, appPreferencesStore),
        nyutagInvites = nyutagInvites,
    )
}

private const val AN_EMAIL = "alice@example.com"
private const val A_NEW_PASSWORD = "new-password"

/*
 * Нютаг — регистрация: приглашение + подтверждённый телефон или почта, затем обычный вход паролем.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.invitesignup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import dev.zacsweers.metro.Inject
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.accountprovider.SaveAccountProviderToHistory
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.nyutag.NyutagInvites
import io.element.android.libraries.matrix.api.nyutag.NyutagSignUpConfig
import io.element.android.libraries.matrix.api.nyutag.NyutagVerification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@Inject
class InviteSignUpPresenter(
    private val invites: NyutagInvites,
    private val authenticationService: MatrixAuthenticationService,
    private val accountProviderDataSource: AccountProviderDataSource,
    private val saveAccountProviderToHistory: SaveAccountProviderToHistory,
) : Presenter<InviteSignUpState> {
    @Composable
    override fun present(): InviteSignUpState {
        val scope = rememberCoroutineScope()
        val form = rememberSaveable { mutableStateOf(InviteSignUpForm()) }
        val config: MutableState<AsyncData<NyutagSignUpConfig>> = remember { mutableStateOf(AsyncData.Uninitialized) }
        val verification: MutableState<AsyncData<NyutagVerification>> = remember { mutableStateOf(AsyncData.Uninitialized) }
        val resendIn = remember { mutableIntStateOf(0) }
        val action: MutableState<AsyncData<SessionId>> = remember { mutableStateOf(AsyncData.Uninitialized) }

        LaunchedEffect(Unit) {
            config.value = AsyncData.Loading()
            invites.signUpConfig().fold(
                onSuccess = { config.value = AsyncData.Success(it) },
                onFailure = { config.value = AsyncData.Failure(it) },
            )
        }
        LaunchedEffect(resendIn.intValue) {
            if (resendIn.intValue > 0) {
                delay(1_000)
                resendIn.intValue -= 1
            }
        }

        fun state() = InviteSignUpState(config.value, form.value, verification.value, resendIn.intValue, action.value) {}

        fun resetVerification() {
            verification.value = AsyncData.Uninitialized
            resendIn.intValue = 0
            form.value = form.value.copy(verifyCode = "")
        }

        fun handleEvent(event: InviteSignUpEvent) {
            when (event) {
                is InviteSignUpEvent.SetCode -> form.value = form.value.copy(code = event.code)
                is InviteSignUpEvent.SetMedium -> if (event.medium != form.value.medium) {
                    form.value = form.value.copy(medium = event.medium, contact = "")
                    resetVerification()
                }
                is InviteSignUpEvent.SetContact -> if (event.contact != form.value.contact) {
                    form.value = form.value.copy(contact = event.contact)
                    if (verification.value !is AsyncData.Uninitialized) resetVerification()
                }
                is InviteSignUpEvent.SetVerifyCode -> form.value = form.value.copy(verifyCode = event.verifyCode.filter { it.isDigit() }.take(6))
                is InviteSignUpEvent.SetUsername -> form.value = form.value.copy(username = event.username)
                is InviteSignUpEvent.SetPassword -> form.value = form.value.copy(password = event.password)
                InviteSignUpEvent.SendCode -> if (state().canSendCode) scope.sendCode(form.value, verification, resendIn)
                InviteSignUpEvent.Submit -> if (state().submitEnabled && action.value !is AsyncData.Loading) {
                    scope.submit(form.value, verification.value.dataOrNull()?.sid, action)
                }
                InviteSignUpEvent.ClearError -> {
                    if (action.value is AsyncData.Failure) action.value = AsyncData.Uninitialized
                    if (verification.value is AsyncData.Failure) verification.value = AsyncData.Uninitialized
                }
            }
        }

        return InviteSignUpState(
            config = config.value,
            form = form.value,
            verification = verification.value,
            resendInSeconds = resendIn.intValue,
            action = action.value,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.sendCode(
        form: InviteSignUpForm,
        verification: MutableState<AsyncData<NyutagVerification>>,
        resendIn: androidx.compose.runtime.MutableIntState,
    ) = launch {
        verification.value = AsyncData.Loading()
        invites.startVerification(form.medium, form.contact.trim(), form.code.trim()).fold(
            onSuccess = {
                Timber.i("Nyutag: ${form.medium.wire} verification code sent")
                verification.value = AsyncData.Success(it)
                resendIn.intValue = it.resendAfterSeconds
            },
            onFailure = {
                Timber.w("Nyutag: verification not started: ${(it as? NyutagInviteException)?.errcode ?: it.javaClass.simpleName}")
                verification.value = AsyncData.Failure(it)
            },
        )
    }

    private fun CoroutineScope.submit(form: InviteSignUpForm, sid: String?, action: MutableState<AsyncData<SessionId>>) = launch {
        action.value = AsyncData.Loading()
        Timber.i("Nyutag: sign-up started, contact=${sid != null}")
        val username = form.username.trim().lowercase().removePrefix("@").substringBefore(":")
        if (sid != null) {
            invites.checkVerification(sid, form.verifyCode).onFailure {
                action.value = AsyncData.Failure(it)
                return@launch
            }
        }
        invites.register(form.code.trim(), username, form.password, sid).onFailure {
            Timber.w("Nyutag: sign-up rejected: ${(it as? NyutagInviteException)?.errcode ?: it.javaClass.simpleName}")
            action.value = AsyncData.Failure(it)
            return@launch
        }
        Timber.i("Nyutag: sign-up registered, signing in")
        // Аккаунт уже создан: при сбое входа ниже человек войдёт обычной кнопкой «Войти» тем же именем и паролем.
        val loginResult = authenticationService.setHomeserver(accountProviderDataSource.flow.value.serverNameOrBaseUrl())
            .fold(
                onSuccess = { authenticationService.login(username, form.password) },
                onFailure = { Result.failure(it) },
            )
        loginResult
            .onSuccess { sessionId ->
                saveAccountProviderToHistory()
                action.value = AsyncData.Success(sessionId)
            }
            .onFailure {
                Timber.w(it, "Nyutag: sign-in after registration failed")
                action.value = AsyncData.Failure(NyutagInviteException(LOGIN_AFTER_REGISTER, it.message ?: "login failed"))
            }
    }

    companion object {
        const val LOGIN_AFTER_REGISTER = "NYUTAG_LOGIN_AFTER_REGISTER"
    }
}

/*
 * Нютаг — регистрация по шагам: код приглашения, подтверждённая почта, телефон, имя и ник, пароль; затем обычный вход паролем.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.invitesignup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.accountprovider.SaveAccountProviderToHistory
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.nyutag.NyutagContactMedium
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.nyutag.NyutagInvites
import io.element.android.libraries.matrix.api.nyutag.NyutagSignUpConfig
import io.element.android.libraries.matrix.api.nyutag.NyutagVerification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@Inject
class InviteSignUpPresenter(
    private val invites: NyutagInvites,
    private val authenticationService: MatrixAuthenticationService,
    private val accountProviderDataSource: AccountProviderDataSource,
    private val saveAccountProviderToHistory: SaveAccountProviderToHistory,
    private val matrixClientProvider: MatrixClientProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : Presenter<InviteSignUpState> {
    /** Код из ссылки-приглашения: подставляется в первый шаг и сразу проверяется. */
    var initialCode: String = ""

    @Composable
    override fun present(): InviteSignUpState {
        val scope = rememberCoroutineScope()
        var form by rememberSaveable { mutableStateOf(InviteSignUpForm(code = NyutagInviteCode.format(initialCode))) }
        var step by rememberSaveable { mutableStateOf(SignUpStep.Code) }
        val config: MutableState<NyutagSignUpConfig?> = remember { mutableStateOf(null) }
        var isCodeValid by rememberSaveable { mutableStateOf(false) }
        var inviter by rememberSaveable { mutableStateOf<String?>(null) }
        var verification by remember { mutableStateOf<NyutagVerification?>(null) }
        var isContactVerified by remember { mutableStateOf(false) }
        // Номер, который сервер признал годным и свободным, в виде +79…
        var checkedPhone by rememberSaveable { mutableStateOf<String?>(null) }
        val resendIn = remember { mutableIntStateOf(0) }
        // Аккаунт уже создан, осталось войти (если вход после регистрации сорвался).
        var registeredUserId by rememberSaveable { mutableStateOf<String?>(null) }
        var isBusy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<Throwable?>(null) }
        var job by remember { mutableStateOf<Job?>(null) }

        val steps = config.value.steps()

        suspend fun loadConfig() {
            if (config.value != null) return
            invites.signUpConfig().onSuccess {
                config.value = it
                if (it.phoneAvailable || it.emailAvailable) {
                    form = form.copy(medium = if (it.phoneAvailable) NyutagContactMedium.Phone else NyutagContactMedium.Email)
                }
            }
        }

        LaunchedEffect(Unit) { loadConfig() }
        LaunchedEffect(resendIn.intValue) {
            if (resendIn.intValue > 0) {
                delay(1_000)
                resendIn.intValue -= 1
            }
        }

        fun state() = InviteSignUpState(
            steps = steps,
            step = step,
            form = form,
            isCodeValid = isCodeValid,
            inviter = inviter,
            availableMedia = config.value.media(),
            canSkipContact = config.value?.let { !it.requireContact && !it.requireEmail } == true,
            verification = verification,
            resendInSeconds = resendIn.intValue,
            isBusy = isBusy,
            error = error,
            eventSink = {},
        )

        fun advance() {
            val current = config.value.steps()
            val index = current.indexOf(step)
            if (index + 1 < current.size) {
                error = null
                step = current[index + 1]
            }
        }

        fun launchBusy(block: suspend CoroutineScope.() -> Unit) {
            job?.cancel()
            isBusy = true
            // Новая попытка — старая ошибка больше не к месту.
            error = null
            val newJob = scope.launch(block = block)
            job = newJob
            newJob.invokeOnCompletion { if (job === newJob) isBusy = false }
        }

        /** Шаг «Контакт» появляется только по настройкам сервера — без них дальше не идём. */
        suspend fun advanceFromCode() {
            loadConfig()
            if (config.value == null) {
                error = NyutagInviteException(NyutagInviteException.NETWORK, "No sign-up configuration")
                return
            }
            // Шаги могли измениться после загрузки настроек.
            val current = config.value.steps()
            step = current[current.indexOf(SignUpStep.Code) + 1]
            error = null
        }

        fun checkCode(advanceOnSuccess: Boolean) {
            val code = form.code
            launchBusy {
                invites.checkInvite(code).fold(
                    onSuccess = {
                        if (form.code != code) return@fold
                        inviter = it
                        isCodeValid = true
                        if (advanceOnSuccess) advanceFromCode()
                    },
                    onFailure = { if (form.code == code) error = it },
                )
            }
        }

        // Код пришёл из ссылки-приглашения — проверяем сразу, как при вводе вручную.
        LaunchedEffect(Unit) {
            if (form.code.length == NyutagInviteCode.LENGTH && !isCodeValid) checkCode(advanceOnSuccess = false)
        }

        fun sendVerification() = launchBusy {
            invites.startVerification(form.medium, form.contact.trim(), form.code).fold(
                onSuccess = {
                    Timber.i("Nyutag: ${form.medium.wire} verification code sent")
                    verification = it
                    resendIn.intValue = it.resendAfterSeconds
                },
                onFailure = { error = it },
            )
        }

        fun checkVerification() {
            val sid = verification?.sid ?: return
            launchBusy {
                invites.checkVerification(sid, form.verifyCode).fold(
                    onSuccess = {
                        isContactVerified = true
                        advance()
                    },
                    onFailure = { error = it },
                )
            }
        }

        fun checkPhone() {
            val phone = form.phone
            launchBusy {
                invites.checkPhone(phone.trim(), form.code).fold(
                    onSuccess = {
                        if (form.phone != phone) return@fold
                        checkedPhone = it
                        advance()
                    },
                    onFailure = { if (form.phone == phone) error = it },
                )
            }
        }

        fun checkUsername() {
            val username = state().username
            launchBusy {
                invites.checkUsername(username).fold(
                    onSuccess = { if (state().username == username) advance() },
                    onFailure = {
                        if (state().username != username) return@fold
                        error = it
                        if (!form.isEditingUsername && (it as? NyutagInviteException)?.errcode != NyutagInviteException.NETWORK) {
                            // Ник из имени занят или не подходит — даём поправить его самому.
                            form = form.copy(username = username, isEditingUsername = true)
                        }
                    },
                )
            }
        }

        fun createAccount() {
            val username = state().username
            val password = form.password
            val displayName = form.displayName.trim()
            launchBusy {
                if (registeredUserId == null) {
                    val sid = verification?.sid?.takeIf { isContactVerified }
                    val phone = checkedPhone?.takeIf { SignUpStep.Phone in config.value.steps() }
                    Timber.i("Nyutag: sign-up started, contact=${sid != null}, phone=${phone != null}")
                    invites.register(form.code, username, password, sid, phone).fold(
                        onSuccess = { registeredUserId = it },
                        onFailure = {
                            Timber.w("Nyutag: sign-up rejected: ${(it as? NyutagInviteException)?.errcode ?: it.javaClass.simpleName}")
                            error = it
                            return@launchBusy
                        },
                    )
                }
                // Аккаунт создан: дальше обычный вход тем же паролем, как с экрана «Войти».
                authenticationService.setHomeserver(accountProviderDataSource.flow.value.serverNameOrBaseUrl())
                    .fold(
                        onSuccess = { authenticationService.login(username, password) },
                        onFailure = { Result.failure(it) },
                    )
                    .onSuccess { sessionId ->
                        saveAccountProviderToHistory()
                        // Экран входа закроется сразу после входа, поэтому имя ставим в области приложения.
                        appCoroutineScope.launch {
                            matrixClientProvider.getOrRestore(sessionId)
                                .mapCatching { client -> client.setDisplayName(displayName).getOrThrow() }
                                .onFailure { Timber.w(it, "Nyutag: display name not set after registration") }
                        }
                    }
                    .onFailure {
                        Timber.w(it, "Nyutag: sign-in after registration failed")
                        error = NyutagInviteException(LOGIN_AFTER_REGISTER, it.message ?: "login failed")
                    }
            }
        }

        fun handleEvent(event: InviteSignUpEvent) {
            when (event) {
                is InviteSignUpEvent.SetCode -> {
                    val code = NyutagInviteCode.format(event.code)
                    if (code == form.code) return
                    form = form.copy(code = code)
                    isCodeValid = false
                    inviter = null
                    error = null
                    job?.cancel()
                    // Как в Telegram: код проверяется сам, как только введён целиком.
                    if (code.length == NyutagInviteCode.LENGTH) checkCode(advanceOnSuccess = false)
                }
                is InviteSignUpEvent.SetMedium -> if (event.medium != form.medium) {
                    form = form.copy(medium = event.medium, contact = "", verifyCode = "")
                    verification = null
                    isContactVerified = false
                    resendIn.intValue = 0
                    error = null
                }
                is InviteSignUpEvent.SetContact -> if (event.contact != form.contact) {
                    form = form.copy(contact = event.contact, verifyCode = "")
                    verification = null
                    isContactVerified = false
                    resendIn.intValue = 0
                    error = null
                }
                is InviteSignUpEvent.SetVerifyCode -> {
                    val digits = event.verifyCode.filter { it.isDigit() }.take(InviteSignUpState.VERIFY_CODE_LENGTH)
                    if (digits == form.verifyCode) return
                    form = form.copy(verifyCode = digits)
                    error = null
                    if (digits.length == InviteSignUpState.VERIFY_CODE_LENGTH && !isBusy) checkVerification()
                }
                is InviteSignUpEvent.SetPhone -> if (event.phone != form.phone) {
                    form = form.copy(phone = event.phone)
                    checkedPhone = null
                    error = null
                }
                is InviteSignUpEvent.SetDisplayName -> {
                    form = form.copy(displayName = event.displayName)
                    error = null
                }
                is InviteSignUpEvent.SetUsername -> {
                    form = form.copy(username = event.username)
                    error = null
                }
                is InviteSignUpEvent.SetPassword -> {
                    form = form.copy(password = event.password)
                    error = null
                }
                is InviteSignUpEvent.SetPasswordRepeat -> {
                    form = form.copy(passwordRepeat = event.password)
                    error = null
                }
                InviteSignUpEvent.EditUsername -> {
                    form = form.copy(username = state().username, isEditingUsername = true)
                    error = null
                }
                InviteSignUpEvent.ResendCode -> if (resendIn.intValue == 0 && !isBusy) {
                    form = form.copy(verifyCode = "")
                    sendVerification()
                }
                InviteSignUpEvent.SkipContact -> if (step == SignUpStep.Contact && state().canSkipContact) advance()
                InviteSignUpEvent.Back -> {
                    val current = config.value.steps()
                    val index = current.indexOf(step)
                    if (index > 0) {
                        job?.cancel()
                        isBusy = false
                        error = null
                        step = current[index - 1]
                    }
                }
                InviteSignUpEvent.Next -> if (state().canContinue) {
                    when (step) {
                        SignUpStep.Code -> if (isCodeValid) launchBusy { advanceFromCode() } else checkCode(advanceOnSuccess = true)
                        SignUpStep.Contact -> if (verification == null) sendVerification() else checkVerification()
                        SignUpStep.Phone -> if (checkedPhone != null) advance() else checkPhone()
                        SignUpStep.Name -> checkUsername()
                        SignUpStep.Password -> createAccount()
                    }
                }
            }
        }

        return state().copy(eventSink = ::handleEvent)
    }

    companion object {
        const val LOGIN_AFTER_REGISTER = "NYUTAG_LOGIN_AFTER_REGISTER"
    }
}

private fun NyutagSignUpConfig?.steps(): List<SignUpStep> = listOfNotNull(
    SignUpStep.Code,
    SignUpStep.Contact.takeIf { this != null && (phoneAvailable || emailAvailable) },
    // Телефон без SMS — свой шаг: номер вводится без кода, по нему человека находят друзья.
    SignUpStep.Phone.takeIf { this != null && requirePhone && phoneUnverified },
    SignUpStep.Name,
    SignUpStep.Password,
)

private fun NyutagSignUpConfig?.media(): List<NyutagContactMedium> = listOfNotNull(
    // Почта обязательна — на шаге «Контакт» только она, телефон спрашивается отдельно.
    NyutagContactMedium.Phone.takeIf { this?.phoneAvailable == true && !requireEmail },
    NyutagContactMedium.Email.takeIf { this?.emailAvailable == true },
)

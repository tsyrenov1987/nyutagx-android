/*
 * Нютаг — регистрация в одну форму: имя и ник, телефон, почта, пароль (и код приглашения, если он нужен);
 * затем код из письма, если сервер требует подтверждённую почту; затем обычный вход паролем.
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
    /** Код из ссылки-приглашения: подставляется в форму и сразу проверяется. */
    var initialCode: String = ""

    @Composable
    override fun present(): InviteSignUpState {
        val scope = rememberCoroutineScope()
        var form by rememberSaveable { mutableStateOf(InviteSignUpForm(code = NyutagInviteCode.format(initialCode))) }
        var step by rememberSaveable { mutableStateOf(SignUpStep.Form) }
        val config: MutableState<NyutagSignUpConfig?> = remember { mutableStateOf(null) }
        var isCodeValid by rememberSaveable { mutableStateOf(false) }
        var inviter by rememberSaveable { mutableStateOf<String?>(null) }
        var verification by remember { mutableStateOf<NyutagVerification?>(null) }
        var isContactVerified by remember { mutableStateOf(false) }
        // Номер, который сервер признал годным и свободным, в виде +79…
        var checkedPhone by rememberSaveable { mutableStateOf<String?>(null) }
        // Ник, который сервер признал свободным.
        var checkedUsername by rememberSaveable { mutableStateOf<String?>(null) }
        val resendIn = remember { mutableIntStateOf(0) }
        // Аккаунт уже создан, осталось войти (если вход после регистрации сорвался).
        var registeredUserId by rememberSaveable { mutableStateOf<String?>(null) }
        var isBusy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<Throwable?>(null) }
        var errorField by remember { mutableStateOf<SignUpField?>(null) }
        var job by remember { mutableStateOf<Job?>(null) }

        suspend fun loadConfig(): Boolean {
            if (config.value != null) return true
            invites.signUpConfig().onSuccess { config.value = it }
            return config.value != null
        }

        LaunchedEffect(Unit) { loadConfig() }
        LaunchedEffect(resendIn.intValue) {
            if (resendIn.intValue > 0) {
                delay(1_000)
                resendIn.intValue -= 1
            }
        }

        fun state() = InviteSignUpState(
            step = step,
            form = form,
            showsCode = config.value.showsCode(initialCode),
            isCodeValid = isCodeValid,
            inviter = inviter,
            showsPhone = config.value.showsPhone(),
            contactMedium = config.value.contactMedium(),
            isContactOptional = config.value?.let { !it.requireContact && !it.requireEmail } == true,
            verification = verification,
            resendInSeconds = resendIn.intValue,
            isBusy = isBusy,
            error = error,
            errorField = errorField,
            eventSink = {},
        )

        fun fail(field: SignUpField, throwable: Throwable) {
            error = throwable
            errorField = field
        }

        fun launchBusy(block: suspend CoroutineScope.() -> Unit) {
            job?.cancel()
            isBusy = true
            // Новая попытка — старая ошибка больше не к месту.
            error = null
            errorField = null
            val newJob = scope.launch(block = block)
            job = newJob
            newJob.invokeOnCompletion { if (job === newJob) isBusy = false }
        }

        fun checkCode() {
            val code = form.code
            launchBusy {
                invites.checkInvite(code).fold(
                    onSuccess = {
                        if (form.code != code) return@fold
                        inviter = it
                        isCodeValid = true
                    },
                    onFailure = { if (form.code == code) fail(SignUpField.Code, it) },
                )
            }
        }

        // Код пришёл из ссылки-приглашения — проверяем сразу, как при вводе вручную.
        LaunchedEffect(Unit) {
            if (form.code.length == NyutagInviteCode.LENGTH && !isCodeValid) checkCode()
        }

        /** Код приглашения, ник, телефон — по очереди; первая ошибка останавливает и подсвечивает своё поле. */
        suspend fun checkForm(): Boolean {
            if (!loadConfig()) {
                fail(SignUpField.Password, NyutagInviteException(NyutagInviteException.NETWORK, "No sign-up configuration"))
                return false
            }
            if (config.value.showsCode(initialCode) && !isCodeValid) {
                val code = form.code
                invites.checkInvite(code).fold(
                    onSuccess = {
                        inviter = it
                        isCodeValid = true
                    },
                    onFailure = {
                        fail(SignUpField.Code, it)
                        return false
                    },
                )
            }
            val username = state().username
            if (checkedUsername != username) {
                invites.checkUsername(username).onFailure {
                    fail(if (form.isEditingUsername) SignUpField.Username else SignUpField.Name, it)
                    if (!form.isEditingUsername && (it as? NyutagInviteException)?.errcode != NyutagInviteException.NETWORK) {
                        // Ник из имени занят или не подходит — даём поправить его самому.
                        form = form.copy(username = username, isEditingUsername = true)
                        errorField = SignUpField.Username
                    }
                    return false
                }
                checkedUsername = username
            }
            if (config.value.showsPhone() && checkedPhone == null) {
                invites.checkPhone(form.phone.trim(), form.code).fold(
                    onSuccess = { checkedPhone = it },
                    onFailure = {
                        fail(SignUpField.Phone, it)
                        return false
                    },
                )
            }
            return true
        }

        suspend fun sendVerification(): Boolean {
            val medium = config.value.contactMedium() ?: return false
            return invites.startVerification(medium, form.contact.trim(), form.code).fold(
                onSuccess = {
                    Timber.i("Nyutag: ${medium.wire} verification code sent")
                    verification = it
                    resendIn.intValue = it.resendAfterSeconds
                    true
                },
                onFailure = {
                    fail(SignUpField.Contact, it)
                    false
                },
            )
        }

        suspend fun createAccount() {
            val username = state().username
            val password = form.password
            val displayName = form.displayName.trim()
            if (registeredUserId == null) {
                val sid = verification?.sid?.takeIf { isContactVerified }
                val phone = checkedPhone?.takeIf { config.value.showsPhone() }
                Timber.i("Nyutag: sign-up started, contact=${sid != null}, phone=${phone != null}")
                invites.register(form.code, username, password, sid, phone).fold(
                    onSuccess = { registeredUserId = it },
                    onFailure = {
                        Timber.w("Nyutag: sign-up rejected: ${(it as? NyutagInviteException)?.errcode ?: it.javaClass.simpleName}")
                        fail(registerErrorField(it, step), it)
                        return
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
                    fail(
                        if (step == SignUpStep.Verify) SignUpField.VerifyCode else SignUpField.Password,
                        NyutagInviteException(LOGIN_AFTER_REGISTER, it.message ?: "login failed"),
                    )
                }
        }

        /** Кнопка «Создать аккаунт»: проверки, затем код из письма (если нужен) или сразу регистрация. */
        fun submitForm() = launchBusy {
            if (!checkForm()) return@launchBusy
            val needsContact = config.value.contactMedium() != null && form.contact.isNotBlank() && !isContactVerified
            if (needsContact) {
                if (sendVerification()) {
                    step = SignUpStep.Verify
                }
                return@launchBusy
            }
            createAccount()
        }

        fun checkVerification() {
            val sid = verification?.sid ?: return
            launchBusy {
                invites.checkVerification(sid, form.verifyCode).fold(
                    onSuccess = {
                        isContactVerified = true
                        createAccount()
                    },
                    onFailure = { fail(SignUpField.VerifyCode, it) },
                )
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
                    errorField = null
                    job?.cancel()
                    // Как в Telegram: код проверяется сам, как только введён целиком.
                    if (code.length == NyutagInviteCode.LENGTH) checkCode()
                }
                is InviteSignUpEvent.SetContact -> if (event.contact != form.contact) {
                    form = form.copy(contact = event.contact, verifyCode = "")
                    verification = null
                    isContactVerified = false
                    resendIn.intValue = 0
                    error = null
                    errorField = null
                }
                is InviteSignUpEvent.SetVerifyCode -> {
                    val digits = event.verifyCode.filter { it.isDigit() }.take(InviteSignUpState.VERIFY_CODE_LENGTH)
                    if (digits == form.verifyCode) return
                    form = form.copy(verifyCode = digits)
                    error = null
                    errorField = null
                    if (digits.length == InviteSignUpState.VERIFY_CODE_LENGTH && !isBusy) checkVerification()
                }
                is InviteSignUpEvent.SetPhone -> if (event.phone != form.phone) {
                    form = form.copy(phone = event.phone)
                    checkedPhone = null
                    error = null
                    errorField = null
                }
                is InviteSignUpEvent.SetDisplayName -> {
                    form = form.copy(displayName = event.displayName)
                    error = null
                    errorField = null
                }
                is InviteSignUpEvent.SetUsername -> {
                    form = form.copy(username = event.username)
                    error = null
                    errorField = null
                }
                is InviteSignUpEvent.SetPassword -> {
                    form = form.copy(password = event.password)
                    error = null
                    errorField = null
                }
                InviteSignUpEvent.EditUsername -> {
                    form = form.copy(username = state().username, isEditingUsername = true)
                    error = null
                    errorField = null
                }
                InviteSignUpEvent.ResendCode -> if (step == SignUpStep.Verify && resendIn.intValue == 0 && !isBusy) {
                    form = form.copy(verifyCode = "")
                    launchBusy { sendVerification() }
                }
                InviteSignUpEvent.Back -> if (step == SignUpStep.Verify) {
                    job?.cancel()
                    isBusy = false
                    error = null
                    errorField = null
                    step = SignUpStep.Form
                }
                InviteSignUpEvent.Next -> if (state().canContinue) {
                    when (step) {
                        SignUpStep.Form -> submitForm()
                        SignUpStep.Verify -> checkVerification()
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

/** Код спрашиваем, только если сервер требует приглашение или код пришёл по ссылке (чтобы запомнить, кто пригласил). */
private fun NyutagSignUpConfig?.showsCode(initialCode: String): Boolean = this == null || requireInvite || initialCode.isNotBlank()

/** Телефон без SMS — своё поле: номер вводится без кода, по нему человека находят друзья. */
private fun NyutagSignUpConfig?.showsPhone(): Boolean = this != null && requirePhone && phoneUnverified

/** Контакт с кодом: почта обязательна — только она (телефон спрашивается отдельно), иначе SMS, если настроены. */
private fun NyutagSignUpConfig?.contactMedium(): NyutagContactMedium? = when {
    this == null -> null
    emailAvailable && (requireEmail || !phoneAvailable) -> NyutagContactMedium.Email
    phoneAvailable -> NyutagContactMedium.Phone
    else -> null
}

/** Под каким полем показать отказ сервера при регистрации. */
private fun registerErrorField(error: Throwable, step: SignUpStep): SignUpField = when ((error as? NyutagInviteException)?.errcode) {
    NyutagInviteException.UNKNOWN_CODE, NyutagInviteException.CODE_USED, NyutagInviteException.CODE_EXPIRED, NyutagInviteException.BAD_CODE ->
        SignUpField.Code
    NyutagInviteException.USER_IN_USE, NyutagInviteException.INVALID_USERNAME -> SignUpField.Username
    NyutagInviteException.WEAK_PASSWORD -> SignUpField.Password
    NyutagInviteException.CONTACT_IN_USE, NyutagInviteException.BAD_CONTACT -> SignUpField.Contact
    else -> if (step == SignUpStep.Verify) SignUpField.VerifyCode else SignUpField.Password
}

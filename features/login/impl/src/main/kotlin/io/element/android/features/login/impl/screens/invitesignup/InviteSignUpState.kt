/*
 * Нютаг — регистрация в одну форму (имя, телефон, почта, пароль) и, если сервер требует почту, код из письма.
 * Код приглашения спрашивается только когда он обязателен или пришёл по ссылке.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.invitesignup

import android.os.Parcelable
import io.element.android.libraries.matrix.api.nyutag.NyutagContactMedium
import io.element.android.libraries.matrix.api.nyutag.NyutagVerification
import kotlinx.parcelize.Parcelize

enum class SignUpStep {
    /** Все поля на одном экране. */
    Form,

    /** Код из письма (или SMS) для подтверждения контакта. */
    Verify,
}

/** Поле формы, под которым показывается ошибка. */
enum class SignUpField {
    Code,
    Name,
    Username,
    Phone,
    Contact,
    Password,
    VerifyCode,
}

data class InviteSignUpState(
    val step: SignUpStep,
    val form: InviteSignUpForm,
    /** Поле кода приглашения на форме (сервер требует код или он пришёл по ссылке). */
    val showsCode: Boolean,
    /** Код проверен сервером; [inviter] — Matrix ID пригласившего. */
    val isCodeValid: Boolean,
    val inviter: String?,
    /** Поле телефона на форме (пока без SMS: номер нужен, чтобы знакомые находили человека). */
    val showsPhone: Boolean,
    /** Контакт с кодом подтверждения: почта или SMS; null — сервер этого не настроил. */
    val contactMedium: NyutagContactMedium?,
    /** Контакт можно не указывать. */
    val isContactOptional: Boolean,
    val verification: NyutagVerification?,
    val resendInSeconds: Int,
    val isBusy: Boolean,
    /** Ошибка — показывается под полем [errorField], без всплывающих окон. */
    val error: Throwable?,
    val errorField: SignUpField?,
    val eventSink: (InviteSignUpEvent) -> Unit,
) {
    /** Ник для входа: придуманный самим человеком или из имени. */
    val username: String
        get() = if (form.isEditingUsername) NyutagUsername.sanitize(form.username) else NyutagUsername.suggest(form.displayName)

    val canContinue: Boolean
        get() = !isBusy && when (step) {
            SignUpStep.Form -> (!showsCode || form.code.length == NyutagInviteCode.LENGTH) &&
                form.displayName.isNotBlank() && username.isNotEmpty() &&
                (!showsPhone || form.phone.count { it.isDigit() } >= MIN_PHONE_DIGITS) &&
                (contactMedium == null || isContactOptional || form.contact.isNotBlank()) &&
                form.password.length >= MIN_PASSWORD_LENGTH
            SignUpStep.Verify -> form.verifyCode.length == VERIFY_CODE_LENGTH
        }

    fun errorFor(field: SignUpField): Throwable? = error?.takeIf { errorField == field }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val VERIFY_CODE_LENGTH = 6
        const val MIN_PHONE_DIGITS = 10
    }
}

@Parcelize
data class InviteSignUpForm(
    val code: String = "",
    val contact: String = "",
    val verifyCode: String = "",
    val phone: String = "",
    val displayName: String = "",
    val username: String = "",
    val isEditingUsername: Boolean = false,
    val password: String = "",
) : Parcelable

sealed interface InviteSignUpEvent {
    data class SetCode(val code: String) : InviteSignUpEvent
    data class SetContact(val contact: String) : InviteSignUpEvent
    data class SetVerifyCode(val verifyCode: String) : InviteSignUpEvent
    data class SetPhone(val phone: String) : InviteSignUpEvent
    data class SetDisplayName(val displayName: String) : InviteSignUpEvent
    data class SetUsername(val username: String) : InviteSignUpEvent
    data class SetPassword(val password: String) : InviteSignUpEvent
    data object EditUsername : InviteSignUpEvent
    data object ResendCode : InviteSignUpEvent
    data object Next : InviteSignUpEvent
    data object Back : InviteSignUpEvent
}

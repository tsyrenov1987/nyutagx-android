/*
 * Нютаг — регистрация по приглашению по шагам, как на iOS: код → почта (если сервер её включил) → телефон (если обязателен) → имя → пароль.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.invitesignup

import android.os.Parcelable
import io.element.android.libraries.matrix.api.nyutag.NyutagContactMedium
import io.element.android.libraries.matrix.api.nyutag.NyutagVerification
import kotlinx.parcelize.Parcelize

enum class SignUpStep {
    Code,
    Contact,
    Phone,
    Name,
    Password,
}

data class InviteSignUpState(
    val steps: List<SignUpStep>,
    val step: SignUpStep,
    val form: InviteSignUpForm,
    /** Код проверен сервером; [inviter] — Matrix ID пригласившего. */
    val isCodeValid: Boolean,
    val inviter: String?,
    val availableMedia: List<NyutagContactMedium>,
    val canSkipContact: Boolean,
    val verification: NyutagVerification?,
    val resendInSeconds: Int,
    val isBusy: Boolean,
    /** Ошибка текущего шага — показывается под полем, без всплывающих окон. */
    val error: Throwable?,
    val eventSink: (InviteSignUpEvent) -> Unit,
) {
    val stepNumber: Int get() = steps.indexOf(step) + 1

    /** Ник для входа: придуманный самим человеком или из имени. */
    val username: String
        get() = if (form.isEditingUsername) NyutagUsername.sanitize(form.username) else NyutagUsername.suggest(form.displayName)

    val canContinue: Boolean
        get() = !isBusy && when (step) {
            SignUpStep.Code -> form.code.length == NyutagInviteCode.LENGTH
            SignUpStep.Contact -> if (verification == null) form.contact.isNotBlank() else form.verifyCode.length == VERIFY_CODE_LENGTH
            SignUpStep.Phone -> form.phone.count { it.isDigit() } >= MIN_PHONE_DIGITS
            SignUpStep.Name -> form.displayName.isNotBlank() && username.isNotEmpty()
            SignUpStep.Password -> form.password.length >= MIN_PASSWORD_LENGTH && form.password == form.passwordRepeat
        }

    val showsPasswordMismatch: Boolean
        get() = form.passwordRepeat.isNotEmpty() && form.passwordRepeat.length >= form.password.length && form.password != form.passwordRepeat

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val VERIFY_CODE_LENGTH = 6
        const val MIN_PHONE_DIGITS = 10
    }
}

@Parcelize
data class InviteSignUpForm(
    val code: String = "",
    val medium: NyutagContactMedium = NyutagContactMedium.Phone,
    val contact: String = "",
    val verifyCode: String = "",
    /** Телефон на своём шаге (пока без SMS-подтверждения). */
    val phone: String = "",
    val displayName: String = "",
    val username: String = "",
    val isEditingUsername: Boolean = false,
    val password: String = "",
    val passwordRepeat: String = "",
) : Parcelable

sealed interface InviteSignUpEvent {
    data class SetCode(val code: String) : InviteSignUpEvent
    data class SetMedium(val medium: NyutagContactMedium) : InviteSignUpEvent
    data class SetContact(val contact: String) : InviteSignUpEvent
    data class SetVerifyCode(val verifyCode: String) : InviteSignUpEvent
    data class SetPhone(val phone: String) : InviteSignUpEvent
    data class SetDisplayName(val displayName: String) : InviteSignUpEvent
    data class SetUsername(val username: String) : InviteSignUpEvent
    data class SetPassword(val password: String) : InviteSignUpEvent
    data class SetPasswordRepeat(val password: String) : InviteSignUpEvent
    data object EditUsername : InviteSignUpEvent
    data object ResendCode : InviteSignUpEvent
    data object SkipContact : InviteSignUpEvent
    data object Next : InviteSignUpEvent
    data object Back : InviteSignUpEvent
}

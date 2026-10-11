/*
 * Нютаг — регистрация по приглашению с подтверждением телефона или почты.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.invitesignup

import android.os.Parcelable
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagContactMedium
import io.element.android.libraries.matrix.api.nyutag.NyutagSignUpConfig
import io.element.android.libraries.matrix.api.nyutag.NyutagVerification
import kotlinx.parcelize.Parcelize

data class InviteSignUpState(
    val config: AsyncData<NyutagSignUpConfig>,
    val form: InviteSignUpForm,
    val verification: AsyncData<NyutagVerification>,
    val resendInSeconds: Int,
    val action: AsyncData<SessionId>,
    val eventSink: (InviteSignUpEvent) -> Unit,
) {
    val requireInvite: Boolean get() = config.dataOrNull()?.requireInvite ?: true
    val requireContact: Boolean get() = config.dataOrNull()?.requireContact ?: false
    val codeSent: Boolean get() = verification is AsyncData.Success

    val canSendCode: Boolean
        get() = form.contact.isNotBlank() &&
            verification !is AsyncData.Loading &&
            resendInSeconds == 0 &&
            (!requireInvite || form.code.isNotBlank())

    val submitEnabled: Boolean
        get() = action !is AsyncData.Failure &&
            (!requireInvite || form.code.isNotBlank()) &&
            form.username.isNotBlank() &&
            form.password.length >= MIN_PASSWORD_LENGTH &&
            when {
                codeSent -> form.verifyCode.length == VERIFY_CODE_LENGTH
                // Номер/почту ввели, но код не запросили — сначала «Получить код».
                form.contact.isNotBlank() -> false
                else -> !requireContact
            }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val VERIFY_CODE_LENGTH = 6
    }
}

@Parcelize
data class InviteSignUpForm(
    val code: String = "",
    val medium: NyutagContactMedium = NyutagContactMedium.Phone,
    val contact: String = "",
    val verifyCode: String = "",
    val username: String = "",
    val password: String = "",
) : Parcelable

sealed interface InviteSignUpEvent {
    data class SetCode(val code: String) : InviteSignUpEvent
    data class SetMedium(val medium: NyutagContactMedium) : InviteSignUpEvent
    data class SetContact(val contact: String) : InviteSignUpEvent
    data class SetVerifyCode(val verifyCode: String) : InviteSignUpEvent
    data class SetUsername(val username: String) : InviteSignUpEvent
    data class SetPassword(val password: String) : InviteSignUpEvent
    data object SendCode : InviteSignUpEvent
    data object Submit : InviteSignUpEvent
    data object ClearError : InviteSignUpEvent
}

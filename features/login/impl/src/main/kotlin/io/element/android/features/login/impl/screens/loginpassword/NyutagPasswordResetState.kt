/*
 * Нютаг — «Забыли пароль?» на экране входа: код на почту аккаунта, новый пароль, вход.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.loginpassword

/** Шаг почты, пока [sid] == null; дальше — код из письма и новый пароль. */
data class NyutagPasswordResetState(
    val email: String,
    val sid: String? = null,
    val code: String = "",
    val password: String = "",
    val resendInSeconds: Int = 0,
    val isBusy: Boolean = false,
    val error: Throwable? = null,
) {
    val canSendCode: Boolean
        get() = !isBusy && email.isNotBlank()

    val canSubmit: Boolean
        get() = !isBusy && sid != null && code.length == CODE_LENGTH && password.length >= MIN_PASSWORD_LENGTH

    companion object {
        const val CODE_LENGTH = 6
        const val MIN_PASSWORD_LENGTH = 8
    }
}

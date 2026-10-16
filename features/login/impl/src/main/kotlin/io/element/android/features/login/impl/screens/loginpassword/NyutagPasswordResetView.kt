/*
 * Нютаг — «Забыли пароль?» на экране входа: код на почту аккаунта, новый пароль, вход.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.loginpassword

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.form.textFieldState
import io.element.android.libraries.designsystem.modifiers.bringIntoViewOnImeVisible
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.PasswordVisibilityToggle
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NyutagPasswordResetView(
    state: NyutagPasswordResetState,
    eventSink: (LoginPasswordEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val close = { eventSink(LoginPasswordEvent.ClosePasswordReset) }
    BackHandler(onBack = close)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton(onClick = close) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(state = rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            if (state.sid == null) {
                EmailStep(state, eventSink)
            } else {
                CodeStep(state, eventSink)
            }
        }
    }
}

@Composable
private fun EmailStep(state: NyutagPasswordResetState, eventSink: (LoginPasswordEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        IconTitleSubtitleMolecule(
            modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
            iconStyle = BigIcon.Style.Default(CompoundIcons.LockSolid()),
            title = stringResource(R.string.nyutag_password_reset_title),
            subTitle = stringResource(R.string.nyutag_password_reset_subtitle),
        )
        Spacer(Modifier.height(40.dp))
        var email by textFieldState(stateValue = state.email)
        val error = state.error?.let { stringResource(passwordResetErrorRes(it)) }
        TextField(
            label = stringResource(R.string.nyutag_contact_email),
            value = email,
            enabled = !state.isBusy,
            placeholder = stringResource(R.string.nyutag_contact_email_hint),
            onValueChange = {
                email = it.oneLine()
                eventSink(LoginPasswordEvent.SetResetEmail(email))
            },
            supportingText = error,
            validity = if (error != null) TextFieldValidity.Invalid else TextFieldValidity.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { eventSink(LoginPasswordEvent.SendResetCode) }),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rememberAutoFocus())
                .bringIntoViewOnImeVisible()
                .semantics { contentType = ContentType.EmailAddress },
        )
        Spacer(Modifier.height(32.dp))
        Button(
            text = stringResource(R.string.nyutag_contact_send_code),
            showProgress = state.isBusy,
            enabled = state.canSendCode,
            onClick = { eventSink(LoginPasswordEvent.SendResetCode) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CodeStep(state: NyutagPasswordResetState, eventSink: (LoginPasswordEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        IconTitleSubtitleMolecule(
            modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
            iconStyle = BigIcon.Style.Default(CompoundIcons.EmailSolid()),
            title = stringResource(R.string.nyutag_contact_code_email),
            subTitle = stringResource(R.string.nyutag_signup_verify_subtitle, state.email.trim()),
        )
        Spacer(Modifier.height(40.dp))
        val error = state.error?.let { stringResource(passwordResetErrorRes(it)) }
        val isPasswordError = (state.error as? NyutagInviteException)?.errcode == NyutagInviteException.WEAK_PASSWORD
        val passwordFocus = remember { FocusRequester() }
        var code by textFieldState(stateValue = state.code)
        TextField(
            value = code,
            enabled = !state.isBusy,
            placeholder = "000000",
            onValueChange = {
                code = it.filter { c -> c.isDigit() }.take(NyutagPasswordResetState.CODE_LENGTH)
                eventSink(LoginPasswordEvent.SetResetCode(code))
            },
            supportingText = error.takeUnless { isPasswordError },
            validity = if (error != null && !isPasswordError) TextFieldValidity.Invalid else TextFieldValidity.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rememberAutoFocus())
                .bringIntoViewOnImeVisible()
                .semantics { contentType = ContentType.SmsOtpCode },
        )
        Spacer(Modifier.height(20.dp))
        var password by textFieldState(stateValue = state.password)
        var passwordVisible by remember { mutableStateOf(false) }
        TextField(
            value = password,
            enabled = !state.isBusy,
            placeholder = stringResource(R.string.nyutag_password_reset_new),
            onValueChange = {
                password = it.oneLine()
                eventSink(LoginPasswordEvent.SetResetPassword(password))
            },
            supportingText = error.takeIf { isPasswordError },
            validity = if (isPasswordError) TextFieldValidity.Invalid else TextFieldValidity.None,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                PasswordVisibilityToggle(
                    visible = passwordVisible,
                    onToggle = { passwordVisible = !passwordVisible },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { eventSink(LoginPasswordEvent.SubmitPasswordReset) }),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocus)
                .bringIntoViewOnImeVisible()
                .semantics { contentType = ContentType.NewPassword },
        )
        Spacer(Modifier.height(32.dp))
        Button(
            text = stringResource(R.string.nyutag_password_reset_submit),
            showProgress = state.isBusy,
            enabled = state.canSubmit,
            onClick = { eventSink(LoginPasswordEvent.SubmitPasswordReset) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(
                text = if (state.resendInSeconds > 0) {
                    stringResource(R.string.nyutag_contact_resend_in, state.resendInSeconds)
                } else {
                    stringResource(R.string.nyutag_contact_resend)
                },
                size = ButtonSize.Medium,
                enabled = state.resendInSeconds == 0 && !state.isBusy,
                onClick = { eventSink(LoginPasswordEvent.SendResetCode) },
            )
        }
    }
}

@Composable
private fun rememberAutoFocus(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { tryOrNull { focusRequester.requestFocus() } }
    return focusRequester
}

private fun String.oneLine(): String = filterNot { it == '\n' || it == '\r' }

@StringRes
private fun passwordResetErrorRes(error: Throwable): Int = when ((error as? NyutagInviteException)?.errcode) {
    NyutagInviteException.CONTACT_UNKNOWN -> R.string.nyutag_password_reset_error_unknown
    NyutagInviteException.BAD_CONTACT -> R.string.nyutag_contact_error_bad
    NyutagInviteException.WEAK_PASSWORD -> R.string.nyutag_invite_error_weak_password
    NyutagInviteException.VERIFY_WRONG -> R.string.nyutag_contact_error_wrong_code
    NyutagInviteException.VERIFY_EXPIRED, NyutagInviteException.UNKNOWN_SESSION -> R.string.nyutag_contact_error_code_expired
    NyutagInviteException.VERIFY_ATTEMPTS -> R.string.nyutag_contact_error_attempts
    NyutagInviteException.LIMIT_EXCEEDED -> R.string.nyutag_invite_error_limit
    NyutagInviteException.NETWORK -> R.string.nyutag_invite_error_network
    NyutagInviteException.CHANNEL_UNAVAILABLE -> R.string.nyutag_contact_error_channel_unavailable
    NyutagInviteException.DELIVERY_FAILED -> R.string.nyutag_contact_error_delivery
    else -> CommonStrings.error_unknown
}

internal class NyutagPasswordResetStateProvider : PreviewParameterProvider<NyutagPasswordResetState> {
    override val values: Sequence<NyutagPasswordResetState> = sequenceOf(
        NyutagPasswordResetState(email = ""),
        NyutagPasswordResetState(email = "dorzho@example.com", error = NyutagInviteException(NyutagInviteException.CONTACT_UNKNOWN, "")),
        NyutagPasswordResetState(email = "dorzho@example.com", sid = "sid", code = "123456", password = "12345678", resendInSeconds = 42),
    )
}

@PreviewsDayNight
@Composable
internal fun NyutagPasswordResetViewPreview(@PreviewParameter(NyutagPasswordResetStateProvider::class) state: NyutagPasswordResetState) = ElementPreview {
    NyutagPasswordResetView(state = state, eventSink = {})
}

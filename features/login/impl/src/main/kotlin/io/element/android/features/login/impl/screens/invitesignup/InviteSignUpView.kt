/*
 * Нютаг — регистрация по коду приглашения.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.invitesignup

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
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.form.textFieldState
import io.element.android.libraries.designsystem.modifiers.bringIntoViewOnImeVisible
import io.element.android.libraries.designsystem.modifiers.onTabOrEnterKeyFocusNext
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.PasswordVisibilityToggle
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.SegmentedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagContactMedium
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.nyutag.NyutagSignUpConfig
import io.element.android.libraries.matrix.api.nyutag.NyutagVerification
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSignUpView(
    state: InviteSignUpState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)
    val isLoading = state.action is AsyncData.Loading
    val focusManager = LocalFocusManager.current

    fun submit() {
        // Проверка формы — в презентере: Compose может держать первую ссылку ::submit со старым state.
        focusManager.clearFocus(force = true)
        state.eventSink(InviteSignUpEvent.Submit)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = {}, navigationIcon = { BackButton(onClick = onBackClick) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
                iconStyle = BigIcon.Style.Default(CompoundIcons.UserAdd()),
                title = stringResource(if (state.requireInvite) R.string.nyutag_invite_signup_title else R.string.nyutag_signup_title),
                subTitle = stringResource(if (state.requireInvite) R.string.nyutag_invite_signup_subtitle else R.string.nyutag_signup_subtitle),
            )
            Spacer(Modifier.height(32.dp))
            InviteSignUpForm(state = state, isLoading = isLoading, onSubmit = ::submit)
            Spacer(Modifier.height(24.dp))
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                ButtonColumnMolecule {
                    Button(
                        text = stringResource(R.string.nyutag_invite_submit),
                        showProgress = isLoading,
                        onClick = ::submit,
                        enabled = state.submitEnabled || isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
            val error = state.action.errorOrNull() ?: state.verification.errorOrNull()
            if (error != null) {
                ErrorDialog(
                    title = stringResource(CommonStrings.dialog_title_error),
                    content = stringResource(inviteErrorRes(error)),
                    onSubmit = { state.eventSink(InviteSignUpEvent.ClearError) },
                )
            }
        }
    }
}

@Composable
private fun InviteSignUpForm(
    state: InviteSignUpState,
    isLoading: Boolean,
    onSubmit: () -> Unit,
) {
    var code by textFieldState(stateValue = state.form.code)
    var contact by textFieldState(stateValue = state.form.contact)
    var verifyCode by textFieldState(stateValue = state.form.verifyCode)
    var username by textFieldState(stateValue = state.form.username)
    var password by textFieldState(stateValue = state.form.password)
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

    Column {
        if (state.requireInvite) {
        TextField(
            label = stringResource(R.string.nyutag_invite_code_label),
            value = code,
            enabled = !isLoading,
            placeholder = "XXXX-XXXX",
            onValueChange = {
                code = it.oneLine()
                state.eventSink(InviteSignUpEvent.SetCode(code))
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = next,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewOnImeVisible()
                .onTabOrEnterKeyFocusNext(focusManager),
        )
        Spacer(Modifier.height(20.dp))
        }
        ContactBlock(
            state = state,
            isLoading = isLoading,
            contact = contact,
            onContactChange = {
                contact = it.oneLine()
                state.eventSink(InviteSignUpEvent.SetContact(contact))
            },
            verifyCode = verifyCode,
            onVerifyCodeChange = {
                verifyCode = it.filter { ch -> ch.isDigit() }.take(InviteSignUpState.VERIFY_CODE_LENGTH)
                state.eventSink(InviteSignUpEvent.SetVerifyCode(verifyCode))
            },
        )
        Spacer(Modifier.height(20.dp))
        TextField(
            label = stringResource(R.string.nyutag_invite_username_label),
            value = username,
            enabled = !isLoading,
            placeholder = stringResource(R.string.nyutag_invite_username_hint),
            onValueChange = {
                username = it.oneLine().lowercase()
                state.eventSink(InviteSignUpEvent.SetUsername(username))
            },
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = next,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewOnImeVisible()
                .onTabOrEnterKeyFocusNext(focusManager)
                .semantics { contentType = ContentType.NewUsername },
        )
        Spacer(Modifier.height(20.dp))
        if (isLoading) passwordVisible = false
        TextField(
            label = stringResource(R.string.nyutag_invite_password_label),
            value = password,
            enabled = !isLoading,
            onValueChange = {
                password = it.oneLine()
                state.eventSink(InviteSignUpEvent.SetPassword(password))
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                PasswordVisibilityToggle(visible = passwordVisible, onToggle = { passwordVisible = !passwordVisible })
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewOnImeVisible()
                .semantics { contentType = ContentType.NewPassword },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactBlock(
    state: InviteSignUpState,
    isLoading: Boolean,
    contact: String,
    onContactChange: (String) -> Unit,
    verifyCode: String,
    onVerifyCodeChange: (String) -> Unit,
) {
    val isPhone = state.form.medium == NyutagContactMedium.Phone
    Text(
        text = stringResource(if (state.requireContact) R.string.nyutag_contact_title else R.string.nyutag_contact_title_optional),
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textPrimary,
    )
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        NyutagContactMedium.entries.forEachIndexed { index, medium ->
            SegmentedButton(
                index = index,
                count = NyutagContactMedium.entries.size,
                selected = state.form.medium == medium,
                onClick = { state.eventSink(InviteSignUpEvent.SetMedium(medium)) },
                text = stringResource(if (medium == NyutagContactMedium.Phone) R.string.nyutag_contact_phone else R.string.nyutag_contact_email),
                enabled = !isLoading,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    TextField(
        value = contact,
        enabled = !isLoading,
        placeholder = stringResource(if (isPhone) R.string.nyutag_contact_phone_hint else R.string.nyutag_contact_email_hint),
        onValueChange = onContactChange,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = if (isPhone) KeyboardType.Phone else KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnImeVisible()
            .semantics { contentType = if (isPhone) ContentType.PhoneNumber else ContentType.EmailAddress },
    )
    TextButton(
        text = when {
            state.verification is AsyncData.Loading -> stringResource(R.string.nyutag_contact_sending)
            state.resendInSeconds > 0 -> stringResource(R.string.nyutag_contact_resend_in, state.resendInSeconds)
            state.codeSent -> stringResource(R.string.nyutag_contact_resend)
            else -> stringResource(R.string.nyutag_contact_send_code)
        },
        onClick = { state.eventSink(InviteSignUpEvent.SendCode) },
        enabled = state.canSendCode && !isLoading,
    )
    if (state.codeSent) {
        Spacer(Modifier.height(4.dp))
        TextField(
            label = stringResource(if (isPhone) R.string.nyutag_contact_code_sms else R.string.nyutag_contact_code_email),
            value = verifyCode,
            enabled = !isLoading,
            placeholder = "000000",
            onValueChange = onVerifyCodeChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewOnImeVisible()
                .semantics { contentType = ContentType.SmsOtpCode },
        )
    }
}

private fun String.oneLine(): String = filterNot { it == '\n' || it == '\r' }

@StringRes
private fun inviteErrorRes(error: Throwable): Int = when ((error as? NyutagInviteException)?.errcode) {
    NyutagInviteException.UNKNOWN_CODE -> R.string.nyutag_invite_error_unknown_code
    NyutagInviteException.CODE_USED -> R.string.nyutag_invite_error_used
    NyutagInviteException.CODE_EXPIRED -> R.string.nyutag_invite_error_expired
    NyutagInviteException.BAD_CODE -> R.string.nyutag_invite_error_bad_code
    NyutagInviteException.USER_IN_USE -> R.string.nyutag_invite_error_user_in_use
    NyutagInviteException.INVALID_USERNAME -> R.string.nyutag_invite_error_invalid_username
    NyutagInviteException.WEAK_PASSWORD -> R.string.nyutag_invite_error_weak_password
    NyutagInviteException.LIMIT_EXCEEDED -> R.string.nyutag_invite_error_limit
    NyutagInviteException.NETWORK -> R.string.nyutag_invite_error_network
    NyutagInviteException.BAD_CONTACT -> R.string.nyutag_contact_error_bad
    NyutagInviteException.CONTACT_IN_USE -> R.string.nyutag_contact_error_in_use
    NyutagInviteException.CONTACT_REQUIRED -> R.string.nyutag_contact_error_required
    NyutagInviteException.CONTACT_NOT_VERIFIED, NyutagInviteException.UNKNOWN_SESSION -> R.string.nyutag_contact_error_not_verified
    NyutagInviteException.CHANNEL_UNAVAILABLE -> R.string.nyutag_contact_error_channel_unavailable
    NyutagInviteException.DELIVERY_FAILED -> R.string.nyutag_contact_error_delivery
    NyutagInviteException.VERIFY_WRONG -> R.string.nyutag_contact_error_wrong_code
    NyutagInviteException.VERIFY_EXPIRED -> R.string.nyutag_contact_error_code_expired
    NyutagInviteException.VERIFY_ATTEMPTS -> R.string.nyutag_contact_error_attempts
    InviteSignUpPresenter.LOGIN_AFTER_REGISTER -> R.string.nyutag_invite_error_login_after_register
    else -> R.string.nyutag_invite_error_generic
}

internal class InviteSignUpStateProvider : PreviewParameterProvider<InviteSignUpState> {
    private val config = AsyncData.Success(NyutagSignUpConfig(requireInvite = true, requireContact = true, emailAvailable = true, phoneAvailable = true))
    override val values = sequenceOf(
        aState(),
        aState(InviteSignUpForm(code = "7KQ2-M9XA", contact = "+7 900 000-00-00")),
        aState(
            InviteSignUpForm(code = "7KQ2-M9XA", contact = "+7 900 000-00-00", verifyCode = "123456", username = "dorzho", password = "12345678"),
            verification = AsyncData.Success(NyutagVerification("sid", 60)),
            resendIn = 42,
        ),
        aState(action = AsyncData.Failure(NyutagInviteException(NyutagInviteException.CODE_USED, ""))),
    )

    private fun aState(
        form: InviteSignUpForm = InviteSignUpForm(),
        verification: AsyncData<NyutagVerification> = AsyncData.Uninitialized,
        resendIn: Int = 0,
        action: AsyncData<SessionId> = AsyncData.Uninitialized,
    ) = InviteSignUpState(config = config, form = form, verification = verification, resendInSeconds = resendIn, action = action, eventSink = {})
}

@PreviewsDayNight
@Composable
internal fun InviteSignUpViewPreview(@PreviewParameter(InviteSignUpStateProvider::class) state: InviteSignUpState) = ElementPreview {
    InviteSignUpView(state = state, onBackClick = {})
}

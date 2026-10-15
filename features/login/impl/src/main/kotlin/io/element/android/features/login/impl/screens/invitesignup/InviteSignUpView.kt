/*
 * Нютаг — регистрация: одна форма (имя, телефон, почта, пароль; код приглашения, если нужен) и, если сервер
 * требует подтверждённую почту, второй экран с кодом из письма.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.invitesignup

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.form.textFieldState
import io.element.android.libraries.designsystem.modifiers.bringIntoViewOnImeVisible
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.PasswordVisibilityToggle
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.nyutag.NyutagContactMedium
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.nyutag.NyutagVerification
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun InviteSignUpView(
    state: InviteSignUpState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun back() = if (state.step == SignUpStep.Form) onBackClick() else state.eventSink(InviteSignUpEvent.Back)
    BackHandler(onBack = ::back)
    val next = { state.eventSink(InviteSignUpEvent.Next) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton(onClick = ::back) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn())
                        .togetherWith(slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "signUpStep",
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    when (step) {
                        SignUpStep.Form -> FormStep(state, onDone = next)
                        SignUpStep.Verify -> VerifyStep(state)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            Button(
                text = stringResource(
                    when {
                        state.isBusy -> R.string.nyutag_signup_creating
                        state.step == SignUpStep.Form -> R.string.nyutag_invite_submit
                        else -> CommonStrings.action_continue
                    }
                ),
                showProgress = state.isBusy,
                enabled = state.canContinue || state.isBusy,
                onClick = next,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun StepHeader(icon: ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ElementTheme.colors.bgAccentSubtle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ElementTheme.colors.iconAccentPrimary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = ElementTheme.typography.fontHeadingMdBold,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(24.dp))
}

/** Поле получает фокус, как только экран открылся: клавиатура уже под рукой. */
@Composable
private fun rememberAutoFocus(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    return focusRequester
}

@Composable
private fun errorText(state: InviteSignUpState, field: SignUpField): String? = state.errorFor(field)?.let { stringResource(inviteErrorRes(it)) }

private fun validity(errorText: String?) = if (errorText != null) TextFieldValidity.Invalid else TextFieldValidity.None

// Форма

@Composable
private fun FormStep(state: InviteSignUpState, onDone: () -> Unit) {
    StepHeader(
        icon = CompoundIcons.UserProfileSolid(),
        title = stringResource(R.string.nyutag_signup_title),
        subtitle = stringResource(R.string.nyutag_signup_form_subtitle),
    )
    val enabled = !state.isBusy
    val nameFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val contactFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val afterName = when {
        state.showsPhone -> phoneFocus
        state.contactMedium != null -> contactFocus
        else -> passwordFocus
    }
    val afterPhone = if (state.contactMedium != null) contactFocus else passwordFocus

    if (state.showsCode) {
        CodeField(state, enabled = enabled, onNext = { runCatching { nameFocus.requestFocus() } })
        Spacer(Modifier.height(16.dp))
    }

    // Имя
    val nameError = errorText(state, SignUpField.Name)
    var displayName by textFieldState(stateValue = state.form.displayName)
    TextField(
        label = stringResource(R.string.nyutag_signup_name_label),
        value = displayName,
        enabled = enabled,
        onValueChange = {
            displayName = it.oneLine()
            state.eventSink(InviteSignUpEvent.SetDisplayName(displayName))
        },
        supportingText = nameError ?: stringResource(R.string.nyutag_signup_name_subtitle),
        validity = validity(nameError),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { runCatching { (if (state.form.isEditingUsername) usernameFocus else afterName).requestFocus() } }),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(nameFocus)
            .bringIntoViewOnImeVisible()
            .semantics { contentType = ContentType.PersonFullName },
    )
    if (!state.showsCode) {
        LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }
    }
    Spacer(Modifier.height(4.dp))
    UsernameLine(state, enabled = enabled, focusRequester = usernameFocus, onNext = { runCatching { afterName.requestFocus() } })
    Spacer(Modifier.height(12.dp))

    // Телефон
    if (state.showsPhone) {
        val phoneError = errorText(state, SignUpField.Phone)
        var phone by textFieldState(stateValue = state.form.phone)
        TextField(
            label = stringResource(R.string.nyutag_contact_phone),
            value = phone,
            enabled = enabled,
            placeholder = stringResource(R.string.nyutag_contact_phone_hint),
            onValueChange = {
                phone = it.oneLine()
                state.eventSink(InviteSignUpEvent.SetPhone(phone))
            },
            supportingText = phoneError ?: stringResource(R.string.nyutag_signup_phone_subtitle),
            validity = validity(phoneError),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { runCatching { afterPhone.requestFocus() } }),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(phoneFocus)
                .bringIntoViewOnImeVisible()
                .semantics { contentType = ContentType.PhoneNumber },
        )
        Spacer(Modifier.height(12.dp))
    }

    // Почта (или SMS-телефон), с кодом подтверждения на следующем экране
    val medium = state.contactMedium
    if (medium != null) {
        val isPhone = medium == NyutagContactMedium.Phone
        val contactError = errorText(state, SignUpField.Contact)
        var contact by textFieldState(stateValue = state.form.contact)
        val label = stringResource(if (isPhone) R.string.nyutag_contact_phone else R.string.nyutag_contact_email)
        TextField(
            label = if (state.isContactOptional) label + " " + stringResource(R.string.nyutag_signup_optional) else label,
            value = contact,
            enabled = enabled,
            placeholder = stringResource(if (isPhone) R.string.nyutag_contact_phone_hint else R.string.nyutag_contact_email_hint),
            onValueChange = {
                contact = it.oneLine()
                state.eventSink(InviteSignUpEvent.SetContact(contact))
            },
            supportingText = contactError ?: stringResource(R.string.nyutag_signup_contact_subtitle),
            validity = validity(contactError),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = if (isPhone) KeyboardType.Phone else KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { runCatching { passwordFocus.requestFocus() } }),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(contactFocus)
                .bringIntoViewOnImeVisible()
                .semantics { contentType = if (isPhone) ContentType.PhoneNumber else ContentType.EmailAddress },
        )
        Spacer(Modifier.height(12.dp))
    }

    // Пароль — одно поле с «глазом», без повтора
    var visible by remember { mutableStateOf(false) }
    if (state.isBusy) visible = false
    val passwordError = errorText(state, SignUpField.Password)
    var password by textFieldState(stateValue = state.form.password)
    TextField(
        label = stringResource(R.string.nyutag_invite_password_label),
        value = password,
        enabled = enabled,
        onValueChange = {
            password = it.oneLine()
            state.eventSink(InviteSignUpEvent.SetPassword(password))
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { PasswordVisibilityToggle(visible = visible, onToggle = { visible = !visible }) },
        supportingText = passwordError ?: stringResource(R.string.nyutag_signup_password_subtitle),
        validity = validity(passwordError),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(passwordFocus)
            .bringIntoViewOnImeVisible()
            .semantics { contentType = ContentType.NewPassword },
    )
}

/** Ник для входа: строкой под именем, по кнопке — отдельное поле. */
@Composable
private fun UsernameLine(state: InviteSignUpState, enabled: Boolean, focusRequester: FocusRequester, onNext: () -> Unit) {
    val usernameError = errorText(state, SignUpField.Username)
    if (state.form.isEditingUsername) {
        var username by textFieldState(stateValue = state.form.username)
        TextField(
            label = stringResource(R.string.nyutag_invite_username_label),
            value = username,
            enabled = enabled,
            placeholder = stringResource(R.string.nyutag_invite_username_hint),
            leadingIcon = { Text("@", style = ElementTheme.typography.fontBodyLgRegular, color = ElementTheme.colors.textSecondary) },
            onValueChange = {
                username = it.oneLine().lowercase()
                state.eventSink(InviteSignUpEvent.SetUsername(username))
            },
            supportingText = usernameError ?: stringResource(R.string.nyutag_invite_username_hint),
            validity = validity(usernameError),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .bringIntoViewOnImeVisible()
                .semantics { contentType = ContentType.NewUsername },
        )
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    } else if (state.username.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.nyutag_signup_username_footer, "@" + state.username),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(
                text = stringResource(R.string.nyutag_signup_username_edit),
                size = ButtonSize.Small,
                enabled = enabled,
                onClick = { state.eventSink(InviteSignUpEvent.EditUsername) },
            )
        }
    }
}

/** Код приглашения `XXXX-XXXX`: дефис вставляется сам, проверяется, как только введён целиком. */
@Composable
private fun CodeField(state: InviteSignUpState, enabled: Boolean, onNext: () -> Unit) {
    Text(
        text = stringResource(R.string.nyutag_invite_code_label),
        style = ElementTheme.typography.fontBodyMdMedium,
        color = ElementTheme.colors.textSecondary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    // Своё значение с курсором в конце: курсор не должен перескакивать через вставленный дефис.
    var value by remember { mutableStateOf(TextFieldValue(state.form.code, TextRange(state.form.code.length))) }
    fun update(input: TextFieldValue) {
        val code = NyutagInviteCode.format(input.text)
        value = if (code == input.text) input else TextFieldValue(code, TextRange(code.length))
        state.eventSink(InviteSignUpEvent.SetCode(code))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = ::update,
            enabled = enabled,
            singleLine = true,
            textStyle = ElementTheme.typography.fontHeadingMdBold.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                color = ElementTheme.colors.textPrimary,
            ),
            cursorBrush = SolidColor(ElementTheme.colors.bgActionPrimaryRest),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "XXXX-XXXX",
                            style = ElementTheme.typography.fontHeadingMdBold.copy(fontFamily = FontFamily.Monospace, letterSpacing = 3.sp),
                            color = ElementTheme.colors.textDisabled,
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .focusRequester(rememberAutoFocus())
                .bringIntoViewOnImeVisible(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        ) {
            when {
                state.isBusy && !state.isCodeValid -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                state.isCodeValid -> Icon(
                    imageVector = CompoundIcons.CheckCircleSolid(),
                    contentDescription = null,
                    tint = ElementTheme.colors.iconSuccessPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
    val codeError = errorText(state, SignUpField.Code)
    when {
        codeError != null -> Text(
            text = codeError,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textCriticalPrimary,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
        state.isCodeValid && state.inviter != null -> Text(
            text = stringResource(R.string.nyutag_signup_invited_by, state.inviter),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSuccessPrimary,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
    }
}

// Код из письма

@Composable
private fun VerifyStep(state: InviteSignUpState) {
    val isPhone = state.contactMedium == NyutagContactMedium.Phone
    StepHeader(
        icon = CompoundIcons.EmailSolid(),
        title = stringResource(if (isPhone) R.string.nyutag_contact_code_sms else R.string.nyutag_contact_code_email),
        subtitle = stringResource(R.string.nyutag_signup_verify_subtitle, state.form.contact.trim()),
    )
    var code by textFieldState(stateValue = state.form.verifyCode)
    val codeError = errorText(state, SignUpField.VerifyCode)
    TextField(
        value = code,
        enabled = !state.isBusy,
        placeholder = "000000",
        onValueChange = {
            code = it.filter { c -> c.isDigit() }.take(InviteSignUpState.VERIFY_CODE_LENGTH)
            state.eventSink(InviteSignUpEvent.SetVerifyCode(code))
        },
        supportingText = codeError,
        validity = validity(codeError),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { state.eventSink(InviteSignUpEvent.Next) }),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(rememberAutoFocus())
            .bringIntoViewOnImeVisible()
            .semantics { contentType = ContentType.SmsOtpCode },
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
            onClick = { state.eventSink(InviteSignUpEvent.ResendCode) },
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
    NyutagInviteException.EMAIL_REQUIRED -> R.string.nyutag_signup_error_email_required
    NyutagInviteException.PHONE_REQUIRED -> R.string.nyutag_signup_error_phone_required
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
    private val code = "7KQ2-M9XA"

    override val values: Sequence<InviteSignUpState> = sequenceOf(
        aState(),
        aState(InviteSignUpForm(displayName = "Доржо Цыренов", phone = "+7 900 000-00-00", contact = "dorzho@example.com", password = "12345678")),
        aState(
            InviteSignUpForm(code = code, displayName = "Доржо"),
            showsCode = true,
            isCodeValid = true,
            inviter = "@dorzho:nyutag.chat",
        ),
        aState(
            InviteSignUpForm(code = code),
            showsCode = true,
            error = NyutagInviteException(NyutagInviteException.CODE_USED, ""),
            errorField = SignUpField.Code,
        ),
        aState(
            InviteSignUpForm(displayName = "Доржо", username = "dorzho", isEditingUsername = true),
            error = NyutagInviteException(NyutagInviteException.USER_IN_USE, ""),
            errorField = SignUpField.Username,
        ),
        aState(
            InviteSignUpForm(displayName = "Доржо", contact = "dorzho@example.com", password = "12345678"),
            step = SignUpStep.Verify,
            verification = NyutagVerification("sid", 60),
            resendInSeconds = 42,
        ),
        aState(InviteSignUpForm(displayName = "Доржо", contact = "dorzho@example.com", password = "12345678"), isBusy = true),
    )

    private fun aState(
        form: InviteSignUpForm = InviteSignUpForm(),
        step: SignUpStep = SignUpStep.Form,
        showsCode: Boolean = false,
        isCodeValid: Boolean = false,
        inviter: String? = null,
        verification: NyutagVerification? = null,
        resendInSeconds: Int = 0,
        isBusy: Boolean = false,
        error: Throwable? = null,
        errorField: SignUpField? = null,
    ) = InviteSignUpState(
        step = step,
        form = form,
        showsCode = showsCode,
        isCodeValid = isCodeValid,
        inviter = inviter,
        showsPhone = true,
        contactMedium = NyutagContactMedium.Email,
        isContactOptional = false,
        verification = verification,
        resendInSeconds = resendInSeconds,
        isBusy = isBusy,
        error = error,
        errorField = errorField,
        eventSink = {},
    )
}

@PreviewsDayNight
@Composable
internal fun InviteSignUpViewPreview(@PreviewParameter(InviteSignUpStateProvider::class) state: InviteSignUpState) = ElementPreview {
    InviteSignUpView(state = state, onBackClick = {})
}

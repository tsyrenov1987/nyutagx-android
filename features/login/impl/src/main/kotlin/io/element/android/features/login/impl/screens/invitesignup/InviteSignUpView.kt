/*
 * Нютаг — регистрация по приглашению по шагам, как на iOS (NyutagSignUpScreen.swift):
 * один вопрос на экран, прогресс сверху, кнопка «Продолжить» снизу, ошибка — под полем.
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
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
import io.element.android.libraries.designsystem.theme.components.SegmentedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.nyutag.NyutagContactMedium
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.nyutag.NyutagVerification
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.launch

@Composable
fun InviteSignUpView(
    state: InviteSignUpState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun back() = if (state.stepNumber <= 1) onBackClick() else state.eventSink(InviteSignUpEvent.Back)
    BackHandler(onBack = ::back)
    val next = { state.eventSink(InviteSignUpEvent.Next) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nyutag_signup_step, state.stepNumber, state.steps.size),
                        style = ElementTheme.typography.fontBodyLgMedium,
                        color = ElementTheme.colors.textSecondary,
                    )
                },
                navigationIcon = { BackButton(onClick = ::back) },
                actions = {
                    if (state.step == SignUpStep.Contact && state.canSkipContact) {
                        TextButton(
                            text = stringResource(CommonStrings.action_skip),
                            size = ButtonSize.Medium,
                            onClick = { state.eventSink(InviteSignUpEvent.SkipContact) },
                        )
                    }
                },
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
            StepProgress(count = state.steps.size, done = state.stepNumber)
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
                    Spacer(Modifier.height(28.dp))
                    when (step) {
                        SignUpStep.Code -> CodeStep(state, onDone = next)
                        SignUpStep.Contact -> ContactStep(state, onDone = next)
                        SignUpStep.Phone -> PhoneStep(state, onDone = next)
                        SignUpStep.Name -> NameStep(state, onDone = next)
                        SignUpStep.Password -> PasswordStep(state, onDone = next)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            Button(
                text = stringResource(
                    when {
                        state.step == SignUpStep.Password && state.isBusy -> R.string.nyutag_signup_creating
                        state.step == SignUpStep.Password -> R.string.nyutag_invite_submit
                        state.step == SignUpStep.Contact && state.verification == null -> R.string.nyutag_contact_send_code
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

/** Полоска шагов под шапкой: пройденные — синие, впереди — серые. */
@Composable
private fun StepProgress(count: Int, done: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (index < done) ElementTheme.colors.bgActionPrimaryRest else ElementTheme.colors.bgSubtleSecondary),
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
                .size(84.dp)
                .clip(CircleShape)
                .background(ElementTheme.colors.bgAccentSubtle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ElementTheme.colors.iconAccentPrimary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = ElementTheme.typography.fontHeadingMdBold,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(28.dp))
}

/** Поле получает фокус, как только шаг открылся: клавиатура уже под рукой. */
@Composable
private fun rememberAutoFocus(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    return focusRequester
}

@Composable
private fun CodeStep(state: InviteSignUpState, onDone: () -> Unit) {
    StepHeader(
        icon = CompoundIcons.EmailSolid(),
        title = stringResource(R.string.nyutag_signup_code_title),
        subtitle = stringResource(R.string.nyutag_invite_signup_subtitle),
    )
    // Своё значение с курсором в конце: дефис вставляется сам, и курсор не должен перескакивать через него.
    var value by remember { mutableStateOf(TextFieldValue(state.form.code, TextRange(state.form.code.length))) }
    fun update(input: TextFieldValue) {
        val code = NyutagInviteCode.format(input.text)
        value = if (code == input.text) input else TextFieldValue(code, TextRange(code.length))
        state.eventSink(InviteSignUpEvent.SetCode(code))
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focusRequester = rememberAutoFocus()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = ::update,
            singleLine = true,
            textStyle = ElementTheme.typography.fontHeadingLgBold.copy(
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
            keyboardActions = KeyboardActions(onNext = { onDone() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "XXXX-XXXX",
                            style = ElementTheme.typography.fontHeadingLgBold.copy(fontFamily = FontFamily.Monospace, letterSpacing = 3.sp),
                            color = ElementTheme.colors.textDisabled,
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .focusRequester(focusRequester)
                .bringIntoViewOnImeVisible(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        ) {
            when {
                state.isBusy -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                state.isCodeValid -> Icon(
                    imageVector = CompoundIcons.CheckCircleSolid(),
                    contentDescription = null,
                    tint = ElementTheme.colors.iconSuccessPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            state.error != null -> ErrorText(state.error)
            state.isCodeValid && state.inviter != null -> Text(
                text = stringResource(R.string.nyutag_signup_invited_by, state.inviter),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textSuccessPrimary,
                textAlign = TextAlign.Center,
            )
            value.text.isEmpty() -> TextButton(
                text = stringResource(R.string.nyutag_signup_paste),
                size = ButtonSize.Medium,
                onClick = {
                    scope.launch {
                        val clip = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                        val text = clip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (text.isNotBlank()) update(TextFieldValue(text))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactStep(state: InviteSignUpState, onDone: () -> Unit) {
    val isPhone = state.form.medium == NyutagContactMedium.Phone
    StepHeader(
        icon = if (isPhone) CompoundIcons.Mobile() else CompoundIcons.Email(),
        // Один способ (сейчас только почта) — заголовок называет его, а не «Телефон или e-mail».
        title = stringResource(
            when {
                state.availableMedia.size > 1 -> R.string.nyutag_contact_title
                isPhone -> R.string.nyutag_contact_phone
                else -> R.string.nyutag_contact_email
            }
        ),
        subtitle = stringResource(R.string.nyutag_signup_contact_subtitle),
    )
    if (state.availableMedia.size > 1) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            state.availableMedia.forEachIndexed { index, medium ->
                SegmentedButton(
                    index = index,
                    count = state.availableMedia.size,
                    selected = state.form.medium == medium,
                    onClick = { state.eventSink(InviteSignUpEvent.SetMedium(medium)) },
                    text = stringResource(if (medium == NyutagContactMedium.Phone) R.string.nyutag_contact_phone else R.string.nyutag_contact_email),
                    enabled = !state.isBusy,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    val codeSent = state.verification != null
    var contact by textFieldState(stateValue = state.form.contact)
    TextField(
        label = stringResource(if (isPhone) R.string.nyutag_contact_phone else R.string.nyutag_contact_email),
        value = contact,
        placeholder = stringResource(if (isPhone) R.string.nyutag_contact_phone_hint else R.string.nyutag_contact_email_hint),
        onValueChange = {
            contact = it.oneLine()
            state.eventSink(InviteSignUpEvent.SetContact(contact))
        },
        supportingText = state.error?.takeIf { !codeSent }?.let { stringResource(inviteErrorRes(it)) },
        validity = if (state.error != null && !codeSent) TextFieldValidity.Invalid else TextFieldValidity.None,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = if (isPhone) KeyboardType.Phone else KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(rememberAutoFocus())
            .bringIntoViewOnImeVisible()
            .semantics { contentType = if (isPhone) ContentType.PhoneNumber else ContentType.EmailAddress },
    )
    if (codeSent) {
        Spacer(Modifier.height(16.dp))
        var verifyCode by textFieldState(stateValue = state.form.verifyCode)
        val codeFocus = remember { FocusRequester() }
        LaunchedEffect(state.verification) { runCatching { codeFocus.requestFocus() } }
        TextField(
            label = stringResource(if (isPhone) R.string.nyutag_contact_code_sms else R.string.nyutag_contact_code_email),
            value = verifyCode,
            placeholder = "000000",
            onValueChange = {
                verifyCode = it.filter { ch -> ch.isDigit() }.take(InviteSignUpState.VERIFY_CODE_LENGTH)
                state.eventSink(InviteSignUpEvent.SetVerifyCode(verifyCode))
            },
            supportingText = state.error?.let { stringResource(inviteErrorRes(it)) },
            validity = if (state.error != null) TextFieldValidity.Invalid else TextFieldValidity.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(codeFocus)
                .bringIntoViewOnImeVisible()
                .semantics { contentType = ContentType.SmsOtpCode },
        )
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

@Composable
private fun PhoneStep(state: InviteSignUpState, onDone: () -> Unit) {
    StepHeader(
        icon = CompoundIcons.Mobile(),
        title = stringResource(R.string.nyutag_contact_phone),
        subtitle = stringResource(R.string.nyutag_signup_phone_subtitle),
    )
    var phone by textFieldState(stateValue = state.form.phone)
    TextField(
        label = stringResource(R.string.nyutag_contact_phone),
        value = phone,
        placeholder = stringResource(R.string.nyutag_contact_phone_hint),
        onValueChange = {
            phone = it.oneLine()
            state.eventSink(InviteSignUpEvent.SetPhone(phone))
        },
        supportingText = state.error?.let { stringResource(inviteErrorRes(it)) },
        validity = if (state.error != null) TextFieldValidity.Invalid else TextFieldValidity.None,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(rememberAutoFocus())
            .bringIntoViewOnImeVisible()
            .semantics { contentType = ContentType.PhoneNumber },
    )
}

@Composable
private fun NameStep(state: InviteSignUpState, onDone: () -> Unit) {
    StepHeader(
        icon = CompoundIcons.UserProfileSolid(),
        title = stringResource(R.string.nyutag_signup_name_title),
        subtitle = stringResource(R.string.nyutag_signup_name_subtitle),
    )
    val editing = state.form.isEditingUsername
    val errorText = state.error?.let { stringResource(inviteErrorRes(it)) }
    var displayName by textFieldState(stateValue = state.form.displayName)
    TextField(
        label = stringResource(R.string.nyutag_signup_name_label),
        value = displayName,
        onValueChange = {
            displayName = it.oneLine()
            state.eventSink(InviteSignUpEvent.SetDisplayName(displayName))
        },
        supportingText = errorText?.takeIf { !editing },
        validity = if (errorText != null && !editing) TextFieldValidity.Invalid else TextFieldValidity.None,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = if (editing) ImeAction.Next else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(rememberAutoFocus())
            .bringIntoViewOnImeVisible()
            .semantics { contentType = ContentType.PersonFullName },
    )
    Spacer(Modifier.height(12.dp))
    if (editing) {
        var username by textFieldState(stateValue = state.form.username)
        TextField(
            label = stringResource(R.string.nyutag_invite_username_label),
            value = username,
            placeholder = stringResource(R.string.nyutag_invite_username_hint),
            leadingIcon = { Text("@", style = ElementTheme.typography.fontBodyLgRegular, color = ElementTheme.colors.textSecondary) },
            onValueChange = {
                username = it.oneLine().lowercase()
                state.eventSink(InviteSignUpEvent.SetUsername(username))
            },
            supportingText = errorText ?: stringResource(R.string.nyutag_invite_username_hint),
            validity = if (errorText != null) TextFieldValidity.Invalid else TextFieldValidity.None,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewOnImeVisible()
                .semantics { contentType = ContentType.NewUsername },
        )
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
                onClick = { state.eventSink(InviteSignUpEvent.EditUsername) },
            )
        }
    }
}

@Composable
private fun PasswordStep(state: InviteSignUpState, onDone: () -> Unit) {
    StepHeader(
        icon = CompoundIcons.LockSolid(),
        title = stringResource(R.string.nyutag_signup_password_title),
        subtitle = stringResource(R.string.nyutag_signup_password_subtitle),
    )
    var visible by remember { mutableStateOf(false) }
    if (state.isBusy) visible = false
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    val repeatFocus = remember { FocusRequester() }
    var password by textFieldState(stateValue = state.form.password)
    TextField(
        label = stringResource(R.string.nyutag_invite_password_label),
        value = password,
        enabled = !state.isBusy,
        onValueChange = {
            password = it.oneLine()
            state.eventSink(InviteSignUpEvent.SetPassword(password))
        },
        visualTransformation = transformation,
        trailingIcon = { PasswordVisibilityToggle(visible = visible, onToggle = { visible = !visible }) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { runCatching { repeatFocus.requestFocus() } }),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(rememberAutoFocus())
            .bringIntoViewOnImeVisible()
            .semantics { contentType = ContentType.NewPassword },
    )
    Spacer(Modifier.height(16.dp))
    var passwordRepeat by textFieldState(stateValue = state.form.passwordRepeat)
    val errorText = state.error?.let { stringResource(inviteErrorRes(it)) }
        ?: stringResource(R.string.nyutag_signup_password_mismatch).takeIf { state.showsPasswordMismatch }
    TextField(
        label = stringResource(R.string.nyutag_signup_password_repeat),
        value = passwordRepeat,
        enabled = !state.isBusy,
        onValueChange = {
            passwordRepeat = it.oneLine()
            state.eventSink(InviteSignUpEvent.SetPasswordRepeat(passwordRepeat))
        },
        visualTransformation = transformation,
        supportingText = errorText,
        validity = if (errorText != null) TextFieldValidity.Invalid else TextFieldValidity.None,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(repeatFocus)
            .bringIntoViewOnImeVisible()
            .semantics { contentType = ContentType.NewPassword },
    )
}

@Composable
private fun ErrorText(error: Throwable) {
    Text(
        text = stringResource(inviteErrorRes(error)),
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textCriticalPrimary,
        textAlign = TextAlign.Center,
    )
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
    private val allSteps = listOf(SignUpStep.Code, SignUpStep.Contact, SignUpStep.Phone, SignUpStep.Name, SignUpStep.Password)
    private val code = "7KQ2-M9XA"
    override val values = sequenceOf(
        aState(SignUpStep.Code),
        aState(SignUpStep.Code, InviteSignUpForm(code = code), isCodeValid = true, inviter = "@dorzho:nyutag.chat"),
        aState(SignUpStep.Code, InviteSignUpForm(code = code), error = NyutagInviteException(NyutagInviteException.CODE_USED, "")),
        aState(SignUpStep.Contact, InviteSignUpForm(code = code, contact = "+7 900 000-00-00")),
        aState(
            SignUpStep.Contact,
            InviteSignUpForm(code = code, contact = "+7 900 000-00-00", verifyCode = "123"),
            verification = NyutagVerification("sid", 60),
            resendIn = 42,
        ),
        aState(SignUpStep.Phone, InviteSignUpForm(code = code, phone = "+7 900 000-00-00")),
        aState(SignUpStep.Name, InviteSignUpForm(code = code, displayName = "Доржо Цыренов")),
        aState(
            SignUpStep.Name,
            InviteSignUpForm(code = code, displayName = "Доржо", username = "dorzho", isEditingUsername = true),
            error = NyutagInviteException(NyutagInviteException.USER_IN_USE, ""),
        ),
        aState(SignUpStep.Password, InviteSignUpForm(code = code, displayName = "Доржо", password = "12345678", passwordRepeat = "12345679")),
        aState(SignUpStep.Password, InviteSignUpForm(code = code, displayName = "Доржо", password = "12345678", passwordRepeat = "12345678"), isBusy = true),
    )

    private fun aState(
        step: SignUpStep,
        form: InviteSignUpForm = InviteSignUpForm(),
        isCodeValid: Boolean = false,
        inviter: String? = null,
        verification: NyutagVerification? = null,
        resendIn: Int = 0,
        isBusy: Boolean = false,
        error: Throwable? = null,
    ) = InviteSignUpState(
        steps = allSteps,
        step = step,
        form = form,
        isCodeValid = isCodeValid,
        inviter = inviter,
        availableMedia = NyutagContactMedium.entries,
        canSkipContact = false,
        verification = verification,
        resendInSeconds = resendIn,
        isBusy = isBusy,
        error = error,
        eventSink = {},
    )
}

@PreviewsDayNight
@Composable
internal fun InviteSignUpViewPreview(@PreviewParameter(InviteSignUpStateProvider::class) state: InviteSignUpState) = ElementPreview {
    InviteSignUpView(state = state, onBackClick = {})
}

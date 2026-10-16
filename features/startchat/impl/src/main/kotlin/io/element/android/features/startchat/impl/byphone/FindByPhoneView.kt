/*
 * Нютаг — экран «По номеру телефона»: номер вручную или из телефонной книги → человек в Нютаге или приглашение (QR, SMS, «Поделиться»).
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.startchat.impl.byphone

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.startchat.impl.R
import io.element.android.libraries.androidutils.system.startSharePlainTextIntent
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.nyutag.NyutagInvite
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.nyutag.nyutagInviteLink
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import io.element.android.libraries.qrcode.QrCodeImage
import io.element.android.libraries.ui.strings.CommonStrings
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindByPhoneView(
    state: FindByPhoneState,
    onBackClick: () -> Unit,
    onOpenDM: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val pickContact = rememberLauncherForActivityResult(PickPhoneContract()) { uri ->
        val phone = uri?.let { context.readPickedPhone(it) }
        when {
            !phone.isNullOrBlank() -> state.eventSink(FindByPhoneEvent.PickedPhone(phone))
            uri != null -> Toast.makeText(context, R.string.nyutag_by_phone_pick_failed, Toast.LENGTH_SHORT).show()
        }
    }
    fun search() {
        keyboard?.hide()
        state.eventSink(FindByPhoneEvent.Search)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.nyutag_by_phone_title),
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.nyutag_by_phone_subtitle),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
            TextField(
                label = stringResource(R.string.nyutag_by_phone_label),
                value = state.phone,
                placeholder = "+7 900 000-00-00",
                onValueChange = { state.eventSink(FindByPhoneEvent.SetPhone(it.filterNot { ch -> ch == '\n' })) },
                trailingIcon = {
                    IconButton(onClick = { runCatching { pickContact.launch(Unit) } }) {
                        Icon(
                            imageVector = CompoundIcons.UserProfile(),
                            contentDescription = stringResource(R.string.nyutag_by_phone_pick_contact),
                        )
                    }
                },
                supportingText = state.search.errorOrNull()?.let { stringResource(errorRes(it)) },
                validity = if (state.search is AsyncData.Failure) TextFieldValidity.Invalid else TextFieldValidity.None,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Phone, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (state.canSearch) search() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                text = stringResource(R.string.nyutag_by_phone_search),
                onClick = ::search,
                enabled = state.canSearch,
                showProgress = state.search.isLoading(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            when (val search = state.search) {
                is AsyncData.Success -> {
                    val user = search.data
                    if (user != null) FoundUser(user, onWrite = { state.eventSink(FindByPhoneEvent.StartDM(user)) }) else NotFound(state)
                }
                else -> Unit
            }
            if (state.hasOwnPhone == false) {
                Spacer(Modifier.height(32.dp))
                OwnPhoneCard(state)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    AsyncActionView(
        async = state.startDmAction,
        progressDialog = {
            AsyncActionViewDefaults.ProgressDialog(progressText = stringResource(CommonStrings.common_starting_chat))
        },
        onSuccess = onOpenDM,
        errorMessage = { stringResource(R.string.screen_start_chat_error_starting_chat) },
        onErrorDismiss = { state.eventSink(FindByPhoneEvent.CancelStartDM) },
    )
}

@Composable
private fun FoundUser(user: MatrixUser, onWrite: () -> Unit) {
    Text(
        text = stringResource(R.string.nyutag_by_phone_found),
        style = ElementTheme.typography.fontBodyMdMedium,
        color = ElementTheme.colors.textSuccessPrimary,
    )
    Spacer(Modifier.height(8.dp))
    MatrixUserRow(matrixUser = user)
    Spacer(Modifier.height(8.dp))
    Button(
        text = stringResource(R.string.nyutag_by_phone_write),
        onClick = onWrite,
        leadingIcon = IconSource.Vector(CompoundIcons.ChatSolid()),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NotFound(state: FindByPhoneState) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.nyutag_by_phone_not_found_title),
        style = ElementTheme.typography.fontHeadingSmMedium,
        color = ElementTheme.colors.textPrimary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.nyutag_by_phone_not_found_subtitle),
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(16.dp))
    when (val invite = state.invite) {
        is AsyncData.Success -> InviteCard(
            invite = invite.data,
            onSms = { context.sendInviteSms(state.phone, inviteText(context, invite.data)) },
            onShare = { context.shareInvite(inviteText(context, invite.data)) },
        )
        else -> {
            invite.errorOrNull()?.let {
                Text(
                    text = stringResource(errorRes(it)),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textCriticalPrimary,
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                text = stringResource(R.string.nyutag_by_phone_invite),
                onClick = { state.eventSink(FindByPhoneEvent.CreateInvite) },
                showProgress = invite.isLoading(),
                leadingIcon = IconSource.Vector(CompoundIcons.UserAdd()),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InviteCard(invite: NyutagInvite, onSms: () -> Unit, onShare: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // QR — на белом при любой теме: тёмный фон сканеры читают хуже.
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(12.dp),
        ) {
            QrCodeImage(data = nyutagInviteLink(invite.code), forceMaxBrightness = false, modifier = Modifier.size(200.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.nyutag_by_phone_qr_hint),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.nyutag_by_phone_code, invite.code),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                text = stringResource(R.string.nyutag_by_phone_sms),
                onClick = onSms,
                size = ButtonSize.Medium,
                modifier = Modifier.weight(1f),
            )
            Button(
                text = stringResource(R.string.nyutag_by_phone_share),
                onClick = onShare,
                size = ButtonSize.Medium,
                leadingIcon = IconSource.Vector(CompoundIcons.ShareAndroid()),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OwnPhoneCard(state: FindByPhoneState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nyutag_by_phone_own_title),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.nyutag_by_phone_own_subtitle),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            label = stringResource(R.string.nyutag_by_phone_own_label),
            value = state.ownPhone,
            placeholder = "+7 900 000-00-00",
            onValueChange = { state.eventSink(FindByPhoneEvent.SetOwnPhone(it.filterNot { ch -> ch == '\n' })) },
            supportingText = state.ownPhoneSave.errorOrNull()?.let { stringResource(errorRes(it)) },
            validity = if (state.ownPhoneSave is AsyncAction.Failure) TextFieldValidity.Invalid else TextFieldValidity.None,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (state.canSaveOwnPhone) state.eventSink(FindByPhoneEvent.SaveOwnPhone) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            text = stringResource(CommonStrings.action_save),
            onClick = { state.eventSink(FindByPhoneEvent.SaveOwnPhone) },
            enabled = state.canSaveOwnPhone,
            showProgress = state.ownPhoneSave.isLoading(),
            size = ButtonSize.Medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun inviteText(context: Context, invite: NyutagInvite): String =
    context.getString(R.string.nyutag_by_phone_invite_text, invite.code, nyutagInviteLink(invite.code))

private fun Context.sendInviteSms(phone: String, text: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone.filter { it.isDigit() || it == '+' })))
        .putExtra("sms_body", text)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.nyutag_by_phone_no_sms_app, Toast.LENGTH_SHORT).show()
    }
}

private fun Context.shareInvite(text: String) = startSharePlainTextIntent(
    activityResultLauncher = null,
    chooserTitle = getString(R.string.nyutag_by_phone_share),
    text = text,
)

/** Выбор номера в телефонной книге: системное окно отдаёт один номер, разрешение на все контакты не нужно. */
private class PickPhoneContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}

private fun Context.readPickedPhone(uri: Uri): String? {
    // Обычный случай: системное окно отдаёт строку данных, её читаем напрямую.
    readPhoneColumn(uri, null, null)?.let { return it }
    // Оболочки с «безопасным доступом к контактам» (HONOR/Huawei) отдают ссылку без номера
    // или без прав на чтение — тогда ищем ту же строку через общий справочник телефонов.
    val id = uri.lastPathSegment?.toLongOrNull() ?: return null
    val phones = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    return readPhoneColumn(phones, "${ContactsContract.CommonDataKinds.Phone._ID} = ?", arrayOf(id.toString()))
        ?: readPhoneColumn(phones, "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id.toString()))
}

private fun Context.readPhoneColumn(uri: Uri, selection: String?, args: Array<String>?): String? = runCatching {
    contentResolver.query(uri, null, selection, args, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER).takeIf { it >= 0 }
            ?: cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER).takeIf { it >= 0 }
            ?: return@use null
        cursor.getString(index)
    }
}.onFailure { Timber.w(it, "readPickedPhone %s", uri) }.getOrNull()?.takeIf { it.isNotBlank() }

@StringRes
private fun errorRes(error: Throwable): Int = when ((error as? NyutagInviteException)?.errcode) {
    NyutagInviteException.NETWORK -> R.string.nyutag_by_phone_error_network
    NyutagInviteException.LIMIT_EXCEEDED -> R.string.nyutag_by_phone_error_limit
    NyutagInviteException.INVITE_LIMIT -> R.string.nyutag_by_phone_error_invite_limit
    NyutagInviteException.BAD_CONTACT -> R.string.nyutag_by_phone_error_bad_phone
    NyutagInviteException.CONTACT_IN_USE -> R.string.nyutag_by_phone_error_phone_in_use
    else -> R.string.nyutag_by_phone_error_generic
}

internal class FindByPhoneStateProvider : PreviewParameterProvider<FindByPhoneState> {
    override val values = sequenceOf(
        aState(),
        aState(phone = "+7 900 000-00-00", search = AsyncData.Success(MatrixUser(UserId("@dorzho:nyutag.chat"), "Доржо"))),
        aState(phone = "+7 900 000-00-01", search = AsyncData.Success(null)),
        aState(phone = "+7 900 000-00-01", search = AsyncData.Success(null), invite = AsyncData.Success(NyutagInvite("7KQ2-M9XA", 0L))),
        aState(phone = "+7 900", search = AsyncData.Failure(NyutagInviteException(NyutagInviteException.NETWORK, "")), hasOwnPhone = false),
    )

    private fun aState(
        phone: String = "",
        search: AsyncData<MatrixUser?> = AsyncData.Uninitialized,
        invite: AsyncData<NyutagInvite> = AsyncData.Uninitialized,
        hasOwnPhone: Boolean? = true,
    ) = FindByPhoneState(
        phone = phone,
        search = search,
        invite = invite,
        hasOwnPhone = hasOwnPhone,
        ownPhone = "",
        ownPhoneSave = AsyncAction.Uninitialized,
        startDmAction = AsyncAction.Uninitialized,
        eventSink = {},
    )
}

@PreviewsDayNight
@Composable
internal fun FindByPhoneViewPreview(@PreviewParameter(FindByPhoneStateProvider::class) state: FindByPhoneState) = ElementPreview {
    FindByPhoneView(state = state, onBackClick = {}, onOpenDM = {})
}

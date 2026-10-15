/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.features.securebackup.impl.R
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun SecureBackupRootView(
    state: SecureBackupRootState,
    onBackClick: () -> Unit,
    onSetupClick: () -> Unit,
    onChangeClick: () -> Unit,
    onDisableClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onLearnMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(id = CommonStrings.common_encryption),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        // Нютаг: описание и переключатель «Хранилище ключей» убраны — человеку нужен только ключ восстановления.
        // Setup recovery
        when (state.recoveryState) {
            RecoveryState.UNKNOWN,
            RecoveryState.WAITING_FOR_SYNC -> Unit
            RecoveryState.DISABLED -> {
                ListItem(
                    content = {
                        Text(
                            text = stringResource(id = R.string.screen_chat_backup_recovery_action_setup),
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(id = R.string.screen_chat_backup_recovery_action_setup_description, state.appName),
                        )
                    },
                    trailingContent = ListItemContent.Badge,
                    enabled = state.isKeyStorageEnabled,
                    alwaysClickable = true,
                    onClick = {
                        if (state.isKeyStorageEnabled) {
                            onSetupClick()
                        } else {
                            state.eventSink.invoke(SecureBackupRootEvent.DisplayKeyStorageDisabledError)
                        }
                    },
                )
            }
            RecoveryState.ENABLED -> {
                ListItem(
                    content = {
                        Text(
                            text = stringResource(id = R.string.screen_chat_backup_recovery_action_change),
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(id = R.string.screen_chat_backup_recovery_action_change_description),
                        )
                    },
                    enabled = state.isKeyStorageEnabled,
                    alwaysClickable = true,
                    onClick = {
                        if (state.isKeyStorageEnabled) {
                            onChangeClick()
                        } else {
                            state.eventSink.invoke(SecureBackupRootEvent.DisplayKeyStorageDisabledError)
                        }
                    },
                )
            }
            RecoveryState.INCOMPLETE ->
                ListItem(
                    content = {
                        Text(
                            text = stringResource(id = R.string.screen_chat_backup_recovery_action_confirm),
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(id = R.string.screen_chat_backup_recovery_action_confirm_description),
                        )
                    },
                    trailingContent = ListItemContent.Badge,
                    enabled = state.isKeyStorageEnabled,
                    alwaysClickable = true,
                    onClick = {
                        if (state.isKeyStorageEnabled) {
                            onConfirmRecoveryKeyClick()
                        } else {
                            state.eventSink.invoke(SecureBackupRootEvent.DisplayKeyStorageDisabledError)
                        }
                    },
                )
        }
    }

    AsyncActionView(
        async = state.enableAction,
        progressDialog = { },
        onSuccess = { },
        onErrorDismiss = { state.eventSink.invoke(SecureBackupRootEvent.DismissDialog) }
    )
    if (state.displayKeyStorageDisabledError) {
        ErrorDialog(
            title = null,
            content = stringResource(id = R.string.screen_chat_backup_key_storage_disabled_error),
            onSubmit = { state.eventSink.invoke(SecureBackupRootEvent.DismissDialog) },
        )
    }
}

@PreviewsDayNight
@Composable
internal fun SecureBackupRootViewPreview(
    @PreviewParameter(SecureBackupRootStatePreviewParam::class) state: SecureBackupRootState
) = ElementPreview {
    SecureBackupRootView(
        state = state,
        onBackClick = {},
        onSetupClick = {},
        onChangeClick = {},
        onDisableClick = {},
        onConfirmRecoveryKeyClick = {},
        onLearnMoreClick = {},
    )
}

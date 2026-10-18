/*
 * Нютаг (форк Element X) — экран «Дополнительно».
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.features.preferences.impl.account.PreferencesSignOutItems
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun AdvancedSettingsView(
    canDeactivateAccount: Boolean,
    onBackClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeactivateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(id = CommonStrings.nyutag_more_settings),
    ) {
        PreferencesSignOutItems(
            canDeactivateAccount = canDeactivateAccount,
            onSignOutClick = onSignOutClick,
            onDeactivateClick = onDeactivateClick,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun AdvancedSettingsViewPreview() = ElementPreview {
    AdvancedSettingsView(
        canDeactivateAccount = true,
        onBackClick = {},
        onSignOutClick = {},
        onDeactivateClick = {},
    )
}

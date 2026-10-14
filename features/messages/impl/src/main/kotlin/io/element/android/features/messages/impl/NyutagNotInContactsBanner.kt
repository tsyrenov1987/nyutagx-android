/*
 * Нютаг — надпись «Вне контактов» в личном чате: незнакомый написал первым, мы ещё не ответили.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton

@Composable
internal fun NyutagNotInContactsBanner(
    canCheckContacts: Boolean,
    onContactsPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onContactsPermissionGranted()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = CompoundIcons.UserProfile(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
        )
        Text(
            text = stringResource(R.string.nyutag_not_in_contacts),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (canCheckContacts) {
            TextButton(
                text = stringResource(R.string.nyutag_not_in_contacts_check),
                size = ButtonSize.Small,
                onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
            )
        }
    }
}

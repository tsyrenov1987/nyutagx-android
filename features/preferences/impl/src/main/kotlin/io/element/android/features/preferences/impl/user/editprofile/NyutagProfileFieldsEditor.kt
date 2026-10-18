/*
 * Нютаг (форк Element X) — правка полей профиля «о себе» на экране своего профиля.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.user.editprofile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.dialogs.ListOption
import io.element.android.libraries.designsystem.components.dialogs.SingleSelectionDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.matrix.api.nyutag.NyutagEastYear
import io.element.android.libraries.matrix.api.nyutag.NyutagProfileInfo
import io.element.android.libraries.matrix.ui.nyutag.nyutagEastYearLabel
import kotlinx.collections.immutable.toImmutableList
import java.util.Calendar
import io.element.android.libraries.matrix.ui.R as MatrixUiR

private const val FIRST_YEAR = 1920

@Composable
internal fun NyutagProfileFieldsEditor(
    info: NyutagProfileInfo,
    onChange: (NyutagProfileInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showYearPicker by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(24.dp))
        TextField(
            label = stringResource(MatrixUiR.string.nyutag_profile_about),
            value = info.about,
            placeholder = stringResource(R.string.nyutag_profile_about_placeholder),
            minLines = 2,
            maxLines = 6,
            onValueChange = { onChange(info.copy(about = it.take(NyutagProfileInfo.MAX_ABOUT))) },
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            label = stringResource(MatrixUiR.string.nyutag_profile_toonto),
            value = info.toonto,
            placeholder = stringResource(R.string.nyutag_profile_toonto_placeholder),
            singleLine = true,
            onValueChange = { onChange(info.copy(toonto = it.take(NyutagProfileInfo.MAX_SHORT))) },
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            label = stringResource(MatrixUiR.string.nyutag_profile_ug),
            value = info.ug,
            placeholder = stringResource(R.string.nyutag_profile_ug_placeholder),
            singleLine = true,
            onValueChange = { onChange(info.copy(ug = it.take(NyutagProfileInfo.MAX_SHORT))) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        val year = NyutagEastYear.fromKey(info.eastYear)
        ListItem(
            supportingContent = {
                Text(year?.let { nyutagEastYearLabel(it) } ?: stringResource(R.string.nyutag_profile_east_year_none))
            },
            onClick = { showYearPicker = true },
        ) {
            Text(stringResource(MatrixUiR.string.nyutag_profile_east_year))
        }
    }
    if (showYearPicker) {
        NyutagEastYearPicker(
            onPick = {
                showYearPicker = false
                onChange(info.copy(eastYear = it?.key.orEmpty()))
            },
            onDismiss = { showYearPicker = false },
        )
    }
}

/** Выбор по году рождения: человек помнит год, а не «стихию и животное». Первый пункт — «не указывать». */
@Composable
private fun NyutagEastYearPicker(
    onPick: (NyutagEastYear?) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val years = remember(currentYear) { (currentYear downTo FIRST_YEAR).toList() }
    val noneTitle = stringResource(R.string.nyutag_profile_east_year_none)
    val options = listOf(ListOption(noneTitle)) + years.map { ListOption(title = it.toString(), subtitle = nyutagEastYearLabel(NyutagEastYear.ofYear(it))) }
    SingleSelectionDialog(
        title = stringResource(R.string.nyutag_profile_east_year_pick),
        subtitle = stringResource(R.string.nyutag_profile_east_year_hint),
        options = options.toImmutableList(),
        onSelectOption = { index -> onPick(years.getOrNull(index - 1)?.let { NyutagEastYear.ofYear(it) }) },
        onDismissRequest = onDismiss,
    )
}

@PreviewsDayNight
@Composable
internal fun NyutagProfileFieldsEditorPreview() = ElementPreview {
    NyutagProfileFieldsEditor(
        info = NyutagProfileInfo(about = "Люблю горы", toonto = "Хори", ug = "Хуасай", eastYear = "metal-horse"),
        onChange = {},
    )
}

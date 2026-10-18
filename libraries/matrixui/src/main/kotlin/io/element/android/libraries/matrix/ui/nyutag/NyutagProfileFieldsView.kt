/*
 * Нютаг (форк Element X) — показ полей профиля «о себе» под аватаркой.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.ui.nyutag

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.nyutag.NyutagEastYear
import io.element.android.libraries.matrix.api.nyutag.NyutagProfileInfo
import io.element.android.libraries.matrix.ui.R

/** Название года: «Огненная Лошадь», по-бурятски «Улаан морин». */
@Composable
fun nyutagEastYearLabel(year: NyutagEastYear): String {
    val feminine = stringArrayResource(R.array.nyutag_east_year_animal_genders)[year.animal.ordinal] == "f"
    val elements = stringArrayResource(if (feminine) R.array.nyutag_east_year_elements_f else R.array.nyutag_east_year_elements_m)
    val animal = stringArrayResource(R.array.nyutag_east_year_animals)[year.animal.ordinal]
    return stringResource(R.string.nyutag_east_year_format, elements[year.element.ordinal], animal)
}

/** Заполненные поля профиля подписью и значением; пустые не показываются. */
@Composable
fun NyutagProfileFieldsView(
    info: NyutagProfileInfo,
    modifier: Modifier = Modifier,
) {
    if (info.isEmpty) return
    val year = NyutagEastYear.fromKey(info.eastYear)
    Column(modifier = modifier.fillMaxWidth()) {
        NyutagProfileField(stringResource(R.string.nyutag_profile_about), info.about)
        NyutagProfileField(stringResource(R.string.nyutag_profile_toonto), info.toonto)
        NyutagProfileField(stringResource(R.string.nyutag_profile_ug), info.ug)
        if (year != null) NyutagProfileField(stringResource(R.string.nyutag_profile_east_year), nyutagEastYearLabel(year))
    }
}

@Composable
private fun NyutagProfileField(label: String, value: String) {
    if (value.isBlank()) return
    Text(
        text = label,
        style = ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textSecondary,
    )
    Text(
        text = value,
        style = ElementTheme.typography.fontBodyLgRegular,
        color = ElementTheme.colors.textPrimary,
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@PreviewsDayNight
@Composable
internal fun NyutagProfileFieldsViewPreview() = ElementPreview {
    NyutagProfileFieldsView(
        NyutagProfileInfo(about = "Люблю горы и хурдэ", toonto = "Хори", ug = "Хуасай", eastYear = NyutagEastYear.ofYear(1990).key),
    )
}

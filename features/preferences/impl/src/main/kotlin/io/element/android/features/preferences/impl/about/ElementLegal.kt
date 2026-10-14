/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2021-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import androidx.annotation.StringRes
import io.element.android.features.preferences.impl.BuildConfig
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private const val COPYRIGHT_URL = BuildConfig.URL_COPYRIGHT
private const val USE_POLICY_URL = BuildConfig.URL_ACCEPTABLE_USE
private const val PRIVACY_URL = BuildConfig.URL_PRIVACY

/** Нютаг: исходники этой сборки (AGPL-3.0) — ссылка обязана быть доступна каждому пользователю. */
private const val NYUTAG_SOURCE_URL = "https://github.com/tsyrenov1987/nyutagx-android"

sealed class ElementLegal(
    @StringRes val titleRes: Int,
    val url: String,
) {
    data object Copyright : ElementLegal(CommonStrings.common_copyright, COPYRIGHT_URL)
    data object AcceptableUsePolicy : ElementLegal(CommonStrings.common_acceptable_use_policy, USE_POLICY_URL)
    data object PrivacyPolicy : ElementLegal(CommonStrings.common_privacy_policy, PRIVACY_URL)
    data object NyutagSourceCode : ElementLegal(CommonStrings.nyutag_source_code, NYUTAG_SOURCE_URL)
}

fun getAllLegals(): ImmutableList<ElementLegal> {
    return persistentListOf(
        ElementLegal.Copyright,
        ElementLegal.AcceptableUsePolicy,
        ElementLegal.PrivacyPolicy,
        ElementLegal.NyutagSourceCode,
    )
}

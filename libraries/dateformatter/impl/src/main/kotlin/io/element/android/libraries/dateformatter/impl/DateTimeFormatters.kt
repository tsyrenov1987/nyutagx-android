/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.dateformatter.impl

import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class DateTimeFormatters(
    private val locale: Locale,
) {
    // Нютаг: в ICU Android нет данных bxr — бурятские даты собираем сами.
    internal val isBuryat = NyutagBuryatDates.isBuryat(locale)

    val onlyTimeFormatter: DateTimeFormatter by lazy {
        if (isBuryat) return@lazy NyutagBuryatDates.onlyTime
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }

    val dateWithMonthAndYearFormatter: DateTimeFormatter by lazy {
        if (isBuryat) return@lazy NyutagBuryatDates.monthAndYear
        val pattern = bestDateTimePattern("MMMM YYYY")
        DateTimeFormatter.ofPattern(pattern, locale)
    }

    val dateWithMonthFormatter: DateTimeFormatter by lazy {
        if (isBuryat) return@lazy NyutagBuryatDates.dayAndMonth
        val pattern = bestDateTimePattern("d MMM")
        DateTimeFormatter.ofPattern(pattern, locale)
    }

    val dateWithDayFormatter: DateTimeFormatter by lazy {
        if (isBuryat) return@lazy NyutagBuryatDates.weekday
        val pattern = bestDateTimePattern("EEEE")
        DateTimeFormatter.ofPattern(pattern, locale)
    }

    val dateWithYearFormatter: DateTimeFormatter by lazy {
        if (isBuryat) return@lazy NyutagBuryatDates.numericDate
        val pattern = bestDateTimePattern("dd.MM.yyyy")
        DateTimeFormatter.ofPattern(pattern, locale)
    }

    val dateWithFullFormatFormatter: DateTimeFormatter by lazy {
        if (isBuryat) return@lazy NyutagBuryatDates.fullDate
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    }

    val dateWithFullFormatNoYearFormatter: DateTimeFormatter by lazy {
        if (isBuryat) return@lazy NyutagBuryatDates.fullDateNoYear
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEE d MMMM") ?: "EEEE d MMMM"
        DateTimeFormatter.ofPattern(pattern, locale)
    }

    private fun bestDateTimePattern(pattern: String): String {
        return DateFormat.getBestDateTimePattern(locale, pattern) ?: pattern
    }
}

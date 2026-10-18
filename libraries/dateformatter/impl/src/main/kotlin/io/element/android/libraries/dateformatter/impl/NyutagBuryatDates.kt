/*
 * Нютаг — бурятские даты на Android. В ICU Android нет данных bxr, и вместо даты выходило «15 ├F3: M10┤».
 * Названия и шаблоны взяты у Apple (CLDR bxr): iPhone с бурятским языком пишет так же.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.dateformatter.impl

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Locale

internal object NyutagBuryatDates {
    fun isBuryat(locale: Locale): Boolean = locale.language == "bxr"

    private val months: Map<Long, String> = listOf(
        "нэгэдүгээр һара",
        "хоёрдугаар һара",
        "гурбадугаар һара",
        "дүрбэдүгээр һара",
        "табадугаар һара",
        "зургадугаар һара",
        "долодугаар һара",
        "наймадугаар һара",
        "юһэдүгээр һара",
        "арбадугаар һара",
        "арбан нэгэдүгээр һара",
        "арбан хоёрдугаар һара",
    ).withIndex().associate { (index, name) -> index + 1L to name }

    // ChronoField.DAY_OF_WEEK: 1 — понедельник, 7 — воскресенье.
    private val weekdays: Map<Long, String> = mapOf(
        1L to "дабаа",
        2L to "мигмар",
        3L to "һарба",
        4L to "пүрбэ",
        5L to "баасан",
        6L to "бимба",
        7L to "нима",
    )

    val onlyTime: DateTimeFormatter = formatter { appendPattern("HH:mm") }

    // «2026 оной арбадугаар һара»
    val monthAndYear: DateTimeFormatter = formatter {
        appendPattern("y 'оной' ")
        appendText(ChronoField.MONTH_OF_YEAR, months)
    }

    // «10 һарын 15»
    val dayAndMonth: DateTimeFormatter = formatter { appendPattern("MM 'һарын' d") }

    val weekday: DateTimeFormatter = formatter { appendText(ChronoField.DAY_OF_WEEK, weekdays) }

    val numericDate: DateTimeFormatter = formatter { appendPattern("dd.MM.y") }

    // «2026 арбадугаар һара 15»
    val fullDate: DateTimeFormatter = formatter {
        appendPattern("y ")
        appendText(ChronoField.MONTH_OF_YEAR, months)
        appendPattern(" d")
    }

    // «пүрбэ, 10 һарын 15»
    val fullDateNoYear: DateTimeFormatter = formatter {
        appendText(ChronoField.DAY_OF_WEEK, weekdays)
        appendPattern(", MM 'һарын' d")
    }

    /** «Мүнөө» / «Үсэгэлдэр» / «Үглөөдэр»; для остальных дней — null. */
    fun relativeDay(timestamp: Long, now: Long, timeZone: TimeZone): String? {
        val zone = timeZone.toJavaZoneId()
        val days = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate(),
        )
        return when (days) {
            0L -> "Мүнөө"
            -1L -> "Үсэгэлдэр"
            1L -> "Үглөөдэр"
            else -> null
        }
    }

    private fun formatter(build: DateTimeFormatterBuilder.() -> Unit): DateTimeFormatter =
        DateTimeFormatterBuilder().apply(build).toFormatter(Locale.ROOT)
}

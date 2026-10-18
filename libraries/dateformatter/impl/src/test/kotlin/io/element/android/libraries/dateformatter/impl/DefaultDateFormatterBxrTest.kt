/*
 * Нютаг — бурятские даты: в ICU Android нет данных bxr, раньше выходило «15 ├F3: M10┤».
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.dateformatter.impl

import android.os.Build
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.time.Instant

@Config(qualifiers = "b+bxr", sdk = [Build.VERSION_CODES.TIRAMISU])
class DefaultDateFormatterBxrTest : RobolectricTest() {
    @Test
    fun `test epoch`() {
        val formatter = createFormatter("1980-04-06T18:35:24.00Z")
        assertThat(formatter.format(0L, DateFormatterMode.Month)).isEqualTo("1970 оной нэгэдүгээр һара")
        assertThat(formatter.format(0L, DateFormatterMode.Day)).isEqualTo("1970 нэгэдүгээр һара 1")
        assertThat(formatter.format(0L, DateFormatterMode.TimeOrDate)).isEqualTo("01.01.1970")
        assertThat(formatter.format(0L, DateFormatterMode.TimeOnly)).isEqualTo("00:00")
    }

    @Test
    fun `test now`() {
        val now = "1980-04-06T18:35:24.00Z"
        val ts = Instant.parse(now).toEpochMilliseconds()
        val formatter = createFormatter(now)
        assertThat(formatter.format(ts, DateFormatterMode.Day)).isEqualTo("Нима, 04 һарын 6")
        assertThat(formatter.format(ts, DateFormatterMode.Day, true)).isEqualTo("Мүнөө")
        assertThat(formatter.format(ts, DateFormatterMode.TimeOrDate)).isEqualTo("18:35")
    }

    @Test
    fun `test yesterday and earlier this year`() {
        val formatter = createFormatter("1980-10-16T18:35:24.00Z")
        val yesterday = Instant.parse("1980-10-15T09:41:00.00Z").toEpochMilliseconds()
        assertThat(formatter.format(yesterday, DateFormatterMode.TimeOrDate)).isEqualTo("10 һарын 15")
        assertThat(formatter.format(yesterday, DateFormatterMode.TimeOrDate, true)).isEqualTo("Үсэгэлдэр")
        assertThat(formatter.format(yesterday, DateFormatterMode.Day, true)).isEqualTo("Үсэгэлдэр")
        val lastWeek = Instant.parse("1980-10-13T09:41:00.00Z").toEpochMilliseconds()
        assertThat(formatter.format(lastWeek, DateFormatterMode.Day, true)).isEqualTo("Дабаа")
    }
}

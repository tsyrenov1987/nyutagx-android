/*
 * Нютаг (форк Element X) — тесты полей профиля и восточного года.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NyutagProfileInfoTest {
    @Test
    fun `east year follows the 60-year cycle`() {
        assertThat(NyutagEastYear.ofYear(2026).key).isEqualTo("fire-horse")
        assertThat(NyutagEastYear.ofYear(1984).key).isEqualTo("wood-rat")
        assertThat(NyutagEastYear.ofYear(1990).key).isEqualTo("metal-horse")
        assertThat(NyutagEastYear.ofYear(2000).key).isEqualTo("metal-dragon")
        assertThat(NyutagEastYear.ofYear(1987).key).isEqualTo("fire-rabbit")
        assertThat(NyutagEastYear.ofYear(1924)).isEqualTo(NyutagEastYear.ofYear(1984))
        assertThat((1984 until 2044).map { NyutagEastYear.ofYear(it).key }.toSet()).hasSize(60)
    }

    @Test
    fun `east year key round trips and rejects garbage`() {
        (1984 until 2044).forEach {
            val year = NyutagEastYear.ofYear(it)
            assertThat(NyutagEastYear.fromKey(year.key)).isEqualTo(year)
        }
        assertThat(NyutagEastYear.fromKey(null)).isNull()
        assertThat(NyutagEastYear.fromKey("")).isNull()
        assertThat(NyutagEastYear.fromKey("fire")).isNull()
        assertThat(NyutagEastYear.fromKey("fire-cat")).isNull()
        assertThat(NyutagEastYear.fromKey("fire-horse-x")).isNull()
    }

    @Test
    fun `fields are trimmed, capped and unknown year dropped`() {
        val info = NyutagProfileInfo.fromFields(
            mapOf(
                NyutagProfileInfo.KEY_ABOUT to "  " + "а".repeat(600),
                NyutagProfileInfo.KEY_TOONTO to " Хори ",
                NyutagProfileInfo.KEY_EAST_YEAR to "fire-cat",
                "displayname" to "Сэсэг",
            )
        )
        assertThat(info.about).hasLength(NyutagProfileInfo.MAX_ABOUT)
        assertThat(info.toonto).isEqualTo("Хори")
        assertThat(info.ug).isEmpty()
        assertThat(info.eastYear).isEmpty()
        assertThat(NyutagProfileInfo().isEmpty).isTrue()
        assertThat(NyutagProfileInfo(eastYear = "fire-cat").isEmpty).isTrue()
        assertThat(NyutagProfileInfo(ug = "Хуасай").isEmpty).isFalse()
    }
}

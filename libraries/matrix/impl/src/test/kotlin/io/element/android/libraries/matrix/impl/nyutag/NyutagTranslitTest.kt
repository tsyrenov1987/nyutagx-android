/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NyutagTranslitTest {
    @Test
    fun `russian and buryat letters become latin`() {
        assertThat("Тест: новое закрытое".nyutagLatinized()).isEqualTo("test: novoe zakrytoe")
        assertThat("Үндэһэн Хэлэн Өдөр".nyutagLatinized()).isEqualTo("undehen khelen odor")
        assertThat("Щука и ёжик".nyutagLatinized()).isEqualTo("shchuka i ezhik")
    }

    @Test
    fun `latin text is only lowercased`() {
        assertThat("Nyutag Chat 2026".nyutagLatinized()).isEqualTo("nyutag chat 2026")
    }
}

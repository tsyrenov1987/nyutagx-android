/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.ui

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.impl.ui.nyutagCallLanguageTag
import org.junit.Test

class NyutagCallLanguageTagTest {
    @Test
    fun `buryat falls back to russian, other languages are kept`() {
        assertThat(nyutagCallLanguageTag("bxr")).isEqualTo("ru-RU")
        assertThat(nyutagCallLanguageTag("bxr-RU")).isEqualTo("ru-RU")
        assertThat(nyutagCallLanguageTag("ru-RU")).isEqualTo("ru-RU")
        assertThat(nyutagCallLanguageTag("en-GB")).isEqualTo("en-GB")
        assertThat(nyutagCallLanguageTag(null)).isNull()
    }
}

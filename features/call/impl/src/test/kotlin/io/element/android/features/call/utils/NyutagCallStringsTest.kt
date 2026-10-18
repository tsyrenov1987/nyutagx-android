/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.utils

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.impl.utils.NyutagCallStrings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

class NyutagCallStringsTest {
    @Test
    fun `only the Russian strings of Element Call are replaced`() {
        assertThat(NyutagCallStrings.isRussianStrings("/element-call/assets/ru-app-GuuWXZzS.json")).isTrue()
        assertThat(NyutagCallStrings.isRussianStrings("/element-call/assets/en-app-DpQMyCEJ.json")).isFalse()
        assertThat(NyutagCallStrings.isRussianStrings("/element-call/assets/ru-app-GuuWXZzS.js")).isFalse()
        assertThat(NyutagCallStrings.isRussianStrings(null)).isFalse()
    }

    @Test
    fun `translation goes over Russian and untranslated strings stay Russian`() {
        val merged = Json.parseToJsonElement(
            NyutagCallStrings.merge(
                base = """{"hangup_button_label":"Завершить звонок","video_tile":{"volume":"Громкость","expand":"Развернуть"},"register_heading":"Создать"}""",
                overrides = """{"hangup_button_label":"Дуудалга дүүргэхэ","video_tile":{"volume":"Дуунай шангань"},"layout_switch_label":"Харагдал"}""",
            )
        ).jsonObject
        assertThat(merged["hangup_button_label"]!!.jsonPrimitive.content).isEqualTo("Дуудалга дүүргэхэ")
        assertThat(merged["video_tile"]!!.jsonObject["volume"]!!.jsonPrimitive.content).isEqualTo("Дуунай шангань")
        assertThat(merged["video_tile"]!!.jsonObject["expand"]!!.jsonPrimitive.content).isEqualTo("Развернуть")
        assertThat(merged["register_heading"]!!.jsonPrimitive.content).isEqualTo("Создать")
        assertThat(merged["layout_switch_label"]!!.jsonPrimitive.content).isEqualTo("Харагдал")
    }

    @Test
    fun `the shipped Buryat translation is valid and covers the call buttons`() {
        val strings = Json.parseToJsonElement(File("src/main/assets/${NyutagCallStrings.BURYAT_ASSET}").readText()).jsonObject
        assertThat(strings["hangup_button_label"]!!.jsonPrimitive.content).isEqualTo("Дуудалга дүүргэхэ")
        assertThat(strings["mute_microphone_button_label"]!!.jsonPrimitive.content).isEqualTo("Микрофон унтарааха")
    }
}

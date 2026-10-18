/*
 * Нютаг — бурятские строки для Element Call.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.impl.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Нютаг: бурятского в Element Call нет. При бурятском интерфейсе звонок открывается по-русски (`lang=ru-RU`),
 * а русские строки, которые он грузит, подменяются бурятским переводом — чего в переводе нет, остаётся по-русски.
 */
internal object NyutagCallStrings {
    const val BURYAT_ASSET = "nyutag/element-call-bxr.json"

    private val russianStringsPath = Regex("/assets/ru-app-[^/]+\\.json$")

    fun isRussianStrings(path: String?): Boolean = path != null && russianStringsPath.containsMatchIn(path)

    fun merge(base: String, overrides: String): String =
        Json.encodeToString(JsonObject.serializer(), merge(Json.parseToJsonElement(base).jsonObject, Json.parseToJsonElement(overrides).jsonObject))

    private fun merge(base: JsonObject, overrides: JsonObject): JsonObject = JsonObject(
        base + overrides.mapValues { (key, value) ->
            val existing: JsonElement? = base[key]
            if (existing is JsonObject && value is JsonObject) merge(existing, value) else value
        }
    )
}

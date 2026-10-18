/*
 * Нютаг (форк Element X) — исчезающее фото: один просмотр, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Метка лежит в content события картинки (формат общий с iOS): `{"chat.nyutag.view_once": true}`.
 * Получатель видит заглушку, открывает фото один раз (без скриншотов) и затем удаляет событие.
 */
object NyutagViewOnce {
    const val KEY = "chat.nyutag.view_once"
    const val EXTRA_CONTENT_JSON = "{\"$KEY\":true}"

    /** [originalJson] — расшифрованное событие из отладочных данных SDK; у локального эха его ещё нет. */
    fun isMarked(originalJson: String?): Boolean {
        if (originalJson.isNullOrEmpty()) return false
        return try {
            val content = (Json.parseToJsonElement(originalJson) as? JsonObject)?.get("content") as? JsonObject
            (content?.get(KEY) as? JsonPrimitive)?.booleanOrNull == true
        } catch (_: SerializationException) {
            false
        }
    }
}

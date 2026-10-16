/*
 * Нютаг — состав входов «Кильватера» без выпуска сборки.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ядро отдаёт `/_nyutag/v1/catalogue` = `{"version", "paths": ["kilvater://…"], "retired": ["host:port"]}`.
 * Приложение хранит последнюю удачную копию и поднимает туннель с неё; каталог из сборки остаётся запасным
 * (на случай, если все входы с сервера недоступны), только без выведенных `retired`.
 */
internal data class NyutagCatalogue(val version: Long, val paths: List<String>, val retired: Set<String>) {
    fun toJson(): String = JSONObject()
        .put("version", version)
        .put("paths", JSONArray(paths))
        .put("retired", JSONArray(retired.toList()))
        .toString()

    companion object {
        fun parse(json: String?): NyutagCatalogue? {
            val js = runCatching { JSONObject(json ?: return null) }.getOrNull() ?: return null
            val paths = js.optJSONArray("paths").strings().filter { it.startsWith("kilvater://") }
            if (paths.isEmpty()) return null
            return NyutagCatalogue(js.optLong("version"), paths, js.optJSONArray("retired").strings().toSet())
        }

        /** Текст для `Mobile.start`: сначала входы с сервера, потом из сборки — без повторов и выведенных. */
        fun compose(bundled: String?, fetched: NyutagCatalogue?): String? {
            val own = bundled.orEmpty().lines().map { it.trim() }.filter { it.startsWith("kilvater://") }
            if (fetched == null) return own.joinToString("\n").ifEmpty { null }
            val known = fetched.paths.map { hostPort(it) }.toSet()
            val spare = own.filter { hostPort(it) !in known && hostPort(it) !in fetched.retired }
            return (fetched.paths + spare).joinToString("\n")
        }

        /** `kilvater://ключ@host:port?…#имя` → `host:port`. */
        fun hostPort(line: String): String = line.substringAfter('@').substringBefore('?').substringBefore('#').substringBefore('/')

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).trim().ifEmpty { null } }
    }
}

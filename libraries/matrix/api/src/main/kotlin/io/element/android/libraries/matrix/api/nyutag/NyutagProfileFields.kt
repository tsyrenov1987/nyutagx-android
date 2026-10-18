/*
 * Нютаг (форк Element X) — поля профиля «о себе»: тоонто нютаг, уг гарбал, год по восточному календарю.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId

/**
 * Нютаг: дополнительные поля профиля (MSC4133, ядро Tuwunel их хранит и отдаёт в `GET /profile/{userId}`).
 * Формат общий с iOS: ключи [NyutagProfileInfo.KEY_ABOUT] и др., год — ключ цикла вида `fire-horse`.
 */
interface NyutagProfileFields {
    suspend fun get(sessionId: SessionId, userId: UserId): Result<NyutagProfileInfo>

    /** Записывает только изменившиеся поля; пустое поле удаляется с сервера. */
    suspend fun update(sessionId: SessionId, old: NyutagProfileInfo, new: NyutagProfileInfo): Result<Unit>
}

data class NyutagProfileInfo(
    val about: String = "",
    val toonto: String = "",
    val ug: String = "",
    val eastYear: String = "",
) {
    val isEmpty: Boolean get() = about.isBlank() && toonto.isBlank() && ug.isBlank() && NyutagEastYear.fromKey(eastYear) == null

    fun normalized() = NyutagProfileInfo(
        about = about.trim().take(MAX_ABOUT),
        toonto = toonto.trim().take(MAX_SHORT),
        ug = ug.trim().take(MAX_SHORT),
        eastYear = NyutagEastYear.fromKey(eastYear)?.key.orEmpty(),
    )

    fun toFields(): Map<String, String> = mapOf(KEY_ABOUT to about, KEY_TOONTO to toonto, KEY_UG to ug, KEY_EAST_YEAR to eastYear)

    companion object {
        const val KEY_ABOUT = "chat.nyutag.about"
        const val KEY_TOONTO = "chat.nyutag.toonto"
        const val KEY_UG = "chat.nyutag.ug"
        const val KEY_EAST_YEAR = "chat.nyutag.east_year"
        const val MAX_ABOUT = 500
        const val MAX_SHORT = 100

        fun fromFields(fields: Map<String, String?>) = NyutagProfileInfo(
            about = fields[KEY_ABOUT].orEmpty(),
            toonto = fields[KEY_TOONTO].orEmpty(),
            ug = fields[KEY_UG].orEmpty(),
            eastYear = fields[KEY_EAST_YEAR].orEmpty(),
        ).normalized()
    }
}

/** Год 60-летнего цикла: стихия (у бурят — цвет) и животное. Ключ `<стихия>-<животное>`, например `fire-horse` (2026). */
data class NyutagEastYear(val element: Element, val animal: Animal) {
    enum class Element(val id: String) {
        WOOD("wood"),
        FIRE("fire"),
        EARTH("earth"),
        METAL("metal"),
        WATER("water"),
    }

    enum class Animal(val id: String) {
        RAT("rat"),
        OX("ox"),
        TIGER("tiger"),
        RABBIT("rabbit"),
        DRAGON("dragon"),
        SNAKE("snake"),
        HORSE("horse"),
        SHEEP("sheep"),
        MONKEY("monkey"),
        ROOSTER("rooster"),
        DOG("dog"),
        PIG("pig"),
    }

    val key: String get() = "${element.id}-${animal.id}"

    companion object {
        /** Год считается по григорианскому году; кто родился до Сагаалгана (январь–февраль), выбирает предыдущий. */
        fun ofYear(year: Int): NyutagEastYear {
            val shift = Math.floorMod(year - 4, 60)
            return NyutagEastYear(Element.entries[(shift % 10) / 2], Animal.entries[shift % 12])
        }

        fun fromKey(key: String?): NyutagEastYear? {
            val (e, a) = key?.split("-")?.takeIf { it.size == 2 } ?: return null
            val element = Element.entries.firstOrNull { it.id == e } ?: return null
            val animal = Animal.entries.firstOrNull { it.id == a } ?: return null
            // Каждая стихия держит два года подряд, поэтому в цикле встречаются все 5 × 12 сочетаний.
            return NyutagEastYear(element, animal)
        }
    }
}

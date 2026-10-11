/*
 * Нютаг — кириллица латиницей для адреса группы/сообщества.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

// Та же таблица, что в iOS (String.nyutagLatinized): одно название — один адрес на обеих платформах.
private val TRANSLIT = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh",
    'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
    'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
    'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu",
    'я' to "ya", 'ө' to "o", 'ү' to "u", 'һ' to "h",
)

/** SDK выбрасывает не-латинские буквы, и адрес из русского или бурятского названия выходит пустым. */
internal fun String.nyutagLatinized(): String = lowercase().map { TRANSLIT[it] ?: it.toString() }.joinToString("")

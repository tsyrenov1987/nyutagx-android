/*
 * Нютаг — разбор ввода при регистрации, как на iOS (NyutagSignUpScreenModels.swift).
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.invitesignup

/** Ник для входа: из имени (транслитом) или придуманный самим человеком. */
internal object NyutagUsername {
    private const val MAX_LENGTH = 24

    fun suggest(displayName: String): String = sanitize(
        displayName.lowercase().map { ch ->
            transliteration[ch] ?: if (ch in " -._") "." else ch.toString()
        }.joinToString("")
    )

    /** Только то, что примет сервер: `a-z 0-9 . _ -`, без точек по краям и двойных точек, не длиннее 24. */
    fun sanitize(value: String): String {
        var result = value.lowercase().trim().removePrefix("@").substringBefore(":")
            .filter { it in 'a'..'z' || it in '0'..'9' || it in "._-" }
        while (result.contains("..")) {
            result = result.replace("..", ".")
        }
        return result.trim('.', '_', '-').take(MAX_LENGTH)
    }

    private val transliteration = mapOf(
        'а' to "a",
        'б' to "b",
        'в' to "v",
        'г' to "g",
        'д' to "d",
        'е' to "e",
        'ё' to "e",
        'ж' to "zh",
        'з' to "z",
        'и' to "i",
        'й' to "y",
        'к' to "k",
        'л' to "l",
        'м' to "m",
        'н' to "n",
        'о' to "o",
        'п' to "p",
        'р' to "r",
        'с' to "s",
        'т' to "t",
        'у' to "u",
        'ф' to "f",
        'х' to "kh",
        'ц' to "ts",
        'ч' to "ch",
        'ш' to "sh",
        'щ' to "sch",
        'ъ' to "",
        'ы' to "y",
        'ь' to "",
        'э' to "e",
        'ю' to "yu",
        'я' to "ya",
        'ө' to "o",
        'ү' to "u",
        'һ' to "h",
    )
}

/** Код приглашения в виде `XXXX-XXXX`: алфавит Crockford, похожие кириллические буквы — как на сервере. */
internal object NyutagInviteCode {
    const val LENGTH = 9

    fun format(input: String): String {
        val code = input.uppercase()
            .mapNotNull { ch -> (lookalikes[ch] ?: ch).takeIf { it in ALPHABET } }
            .take(8)
            .joinToString("")
        return if (code.length > 4) code.take(4) + "-" + code.drop(4) else code
    }

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val lookalikes = mapOf(
        'А' to 'A',
        'В' to 'B',
        'С' to 'C',
        'Е' to 'E',
        'Н' to 'H',
        'К' to 'K',
        'М' to 'M',
        'О' to '0',
        'Р' to 'P',
        'Т' to 'T',
        'Х' to 'X',
        'У' to 'Y',
        'З' to '3',
        'O' to '0',
        'I' to '1',
        'L' to '1',
        'Ь' to 'B',
    )
}

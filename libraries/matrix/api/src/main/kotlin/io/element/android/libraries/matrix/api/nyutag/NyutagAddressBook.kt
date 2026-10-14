/*
 * Нютаг — номера из записной книжки телефона (для надписи «Вне контактов»).
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

interface NyutagAddressBook {
    /** Разрешение на контакты выдано. */
    fun canRead(): Boolean

    /** Все номера книжки как записаны; пусто без разрешения. */
    suspend fun phones(): List<String>
}

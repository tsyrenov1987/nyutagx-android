/*
 * Нютаг — сверка записной книжки с сервером: кто из контактов уже в Нютаге.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.user.MatrixUser

/** Контакт из книжки, найденный в Нютаге: имя — как записано у человека, профиль — с сервера. */
data class NyutagMatchedContact(
    val name: String,
    val phone: String,
    val user: MatrixUser,
)

/**
 * Номера книжки уходят на сервер пачками, сервер отвечает только совпадениями и номера не хранит.
 * Итог запоминается на устройстве, чтобы список показывался сразу, а сверка шла не чаще раза в час.
 */
interface NyutagContactsSync {
    /** Последний итог сверки для [sessionId]; null — сверки ещё не было. */
    suspend fun cached(sessionId: SessionId): List<NyutagMatchedContact>?

    /** Сверяет книжку с сервером; без [force] — только если прошлая сверка старше часа (тогда отдаёт [cached]). */
    suspend fun sync(sessionId: SessionId, force: Boolean = false): Result<List<NyutagMatchedContact>>
}

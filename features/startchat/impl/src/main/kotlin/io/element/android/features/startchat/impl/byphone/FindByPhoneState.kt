/*
 * Нютаг — поиск человека по номеру телефона: нашёлся — пишем ему, нет — приглашаем ссылкой, QR или SMS.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.startchat.impl.byphone

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.nyutag.NyutagInvite
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.collections.immutable.ImmutableList

data class FindByPhoneState(
    val phone: String,
    /** Итог поиска по [phone]: человек в Нютаге или null — номера в Нютаге нет. */
    val search: AsyncData<MatrixUser?>,
    /** Приглашение для ненайденного номера: код выпускается по кнопке, один на номер. */
    val invite: AsyncData<NyutagInvite>,
    /** Указан ли свой номер; null — ещё не знаем. */
    val hasOwnPhone: Boolean?,
    val ownPhone: String,
    val ownPhoneSave: AsyncAction<Unit>,
    val startDmAction: AsyncAction<RoomId>,
    val canReadContacts: Boolean,
    /** Телефонная книга: сначала те, кто уже в Нютаге, потом остальные по имени. */
    val contacts: AsyncData<ImmutableList<FindByPhoneContact>>,
    val eventSink: (FindByPhoneEvent) -> Unit,
) {
    /** Книга, суженная по введённому: буквы — по имени, цифры — по номеру (8… и +7… считаются одним). */
    val visibleContacts: List<FindByPhoneContact>
        get() {
            val all = contacts.dataOrNull().orEmpty()
            val query = phone.trim()
            if (query.isEmpty()) return all
            val digits = phoneDigits(query)
            return all.filter { contact ->
                contact.name.contains(query, ignoreCase = true) || (digits.isNotEmpty() && phoneDigits(contact.phone).contains(digits))
            }
        }

    val canSearch: Boolean get() = phone.count { it.isDigit() } >= MIN_PHONE_DIGITS && !search.isLoading()
    val canSaveOwnPhone: Boolean get() = ownPhone.count { it.isDigit() } >= MIN_PHONE_DIGITS && !ownPhoneSave.isLoading()

    companion object {
        const val MIN_PHONE_DIGITS = 10

        /** Цифры номера; российское 8… приводится к 7…, чтобы «8 900…» и «+7 900…» совпадали. */
        fun phoneDigits(value: String): String = value.filter { it.isDigit() }.let { if (it.startsWith("8")) "7" + it.drop(1) else it }
    }
}

/** Запись телефонной книги на экране «По номеру»: [user] задан — человек уже в Нютаге. */
data class FindByPhoneContact(val name: String, val phone: String, val user: MatrixUser? = null)

sealed interface FindByPhoneEvent {
    data class SetPhone(val phone: String) : FindByPhoneEvent

    /** Номер из телефонной книги: ставим и сразу ищем. */
    data class PickedPhone(val phone: String) : FindByPhoneEvent
    data object Search : FindByPhoneEvent
    data object CreateInvite : FindByPhoneEvent
    data class StartDM(val user: MatrixUser) : FindByPhoneEvent
    data object CancelStartDM : FindByPhoneEvent

    /** Разрешение на контакты только что выдано — читаем книгу. */
    data object ContactsGranted : FindByPhoneEvent

    /** Тап по записи книги: в Нютаге — сразу чат, иначе номер в поле и поиск. */
    data class PickContact(val contact: FindByPhoneContact) : FindByPhoneEvent
    data class SetOwnPhone(val phone: String) : FindByPhoneEvent
    data object SaveOwnPhone : FindByPhoneEvent
}

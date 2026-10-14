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
    val eventSink: (FindByPhoneEvent) -> Unit,
) {
    val canSearch: Boolean get() = phone.count { it.isDigit() } >= MIN_PHONE_DIGITS && !search.isLoading()
    val canSaveOwnPhone: Boolean get() = ownPhone.count { it.isDigit() } >= MIN_PHONE_DIGITS && !ownPhoneSave.isLoading()

    companion object {
        const val MIN_PHONE_DIGITS = 10
    }
}

sealed interface FindByPhoneEvent {
    data class SetPhone(val phone: String) : FindByPhoneEvent

    /** Номер из телефонной книги: ставим и сразу ищем. */
    data class PickedPhone(val phone: String) : FindByPhoneEvent
    data object Search : FindByPhoneEvent
    data object CreateInvite : FindByPhoneEvent
    data class StartDM(val user: MatrixUser) : FindByPhoneEvent
    data object CancelStartDM : FindByPhoneEvent
    data class SetOwnPhone(val phone: String) : FindByPhoneEvent
    data object SaveOwnPhone : FindByPhoneEvent
}

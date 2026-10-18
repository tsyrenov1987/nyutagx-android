/*
 * Нютаг — вкладка «Контакты» на главной: телефонная книга, сверху те, кто уже в Нютаге.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.contacts

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.collections.immutable.ImmutableList

data class NyutagContactsState(
    val canReadContacts: Boolean,
    /** Книга с пометкой, кто в Нютаге; при сбое сверки с сервером — книга без пометок. */
    val contacts: AsyncData<ImmutableList<NyutagHomeContact>>,
    val startDmAction: AsyncAction<RoomId>,
    val eventSink: (NyutagContactsEvent) -> Unit,
) {
    val inNyutag: List<NyutagHomeContact> get() = contacts.dataOrNull().orEmpty().filter { it.user != null }
    val others: List<NyutagHomeContact> get() = contacts.dataOrNull().orEmpty().filter { it.user == null }
}

/** Запись книги: [user] задан — человек уже в Нютаге. */
data class NyutagHomeContact(val name: String, val phone: String, val user: MatrixUser? = null)

sealed interface NyutagContactsEvent {
    /** Вкладку открыли (или выдали разрешение) — читаем книгу и сверяем с сервером. */
    data class Load(val force: Boolean) : NyutagContactsEvent
    data class StartDM(val contact: NyutagHomeContact) : NyutagContactsEvent
    data object CancelStartDM : NyutagContactsEvent
}

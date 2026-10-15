/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.startchat.impl.root

import io.element.android.libraries.matrix.api.user.MatrixUser

sealed interface StartChatEvent {
    data class StartDM(val matrixUser: MatrixUser) : StartChatEvent
    data object CancelStartDM : StartChatEvent
    data class OpenSavedMessages(val name: String, val topic: String) : StartChatEvent

    /** Нютаг: разрешение на контакты получено или нажато «обновить» — сверить книжку с сервером. */
    data class SyncContacts(val force: Boolean = false) : StartChatEvent

    /** Нютаг: чат с человеком из записной книжки — без вопроса «начать чат?», как при поиске по номеру. */
    data class StartDMWithContact(val matrixUser: MatrixUser) : StartChatEvent
}

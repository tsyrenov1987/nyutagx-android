/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.startchat.impl.root

import io.element.android.features.startchat.impl.userlist.UserListState
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.nyutag.NyutagMatchedContact
import kotlinx.collections.immutable.ImmutableList

data class StartChatState(
    val applicationName: String,
    val userListState: UserListState,
    val startDmAction: AsyncAction<RoomId>,
    /** Нютаг: разрешение на контакты выдано — книжка сверяется с сервером. */
    val canReadContacts: Boolean,
    /** Нютаг: кто из записной книжки уже в Нютаге (Uninitialized — без разрешения). */
    val nyutagContacts: AsyncData<ImmutableList<NyutagMatchedContact>>,
    val eventSink: (StartChatEvent) -> Unit,
)

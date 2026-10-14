/*
 * Нютаг (форк Element X) — присутствие собеседника («в сети» / «был(а) …») в шапке личного чата.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId

/**
 * SDK умеет только отправлять своё присутствие, поэтому чужое читаем сами через туннель.
 */
fun interface NyutagPresenceService {
    suspend fun presence(sessionId: SessionId, userId: UserId): Result<NyutagPresence>
}

data class NyutagPresence(
    val isOnline: Boolean,
    /** Когда собеседник был активен в последний раз (мс с эпохи), если сервер это знает. */
    val lastActiveTimestamp: Long?,
)

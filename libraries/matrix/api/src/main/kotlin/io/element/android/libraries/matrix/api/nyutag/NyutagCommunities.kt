/*
 * Нютаг (форк Element X) — каталог сообществ.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId

/**
 * Нютаг: все сообщества сервера видны всем, как во ВКонтакте. Каталог — `publicRooms` с фильтром `m.space`,
 * через тот же туннель, что и SDK. Открытое — вступление сразу, закрытое — по заявке (knock).
 */
interface NyutagCommunities {
    suspend fun communities(sessionId: SessionId, searchTerm: String?, since: String? = null): Result<NyutagCommunityPage>
}

data class NyutagCommunity(
    val roomId: RoomId,
    val name: String?,
    val topic: String?,
    val alias: String?,
    val avatarUrl: String?,
    val membersCount: Int,
    val isOpen: Boolean,
)

data class NyutagCommunityPage(
    val communities: List<NyutagCommunity>,
    val nextBatch: String?,
)

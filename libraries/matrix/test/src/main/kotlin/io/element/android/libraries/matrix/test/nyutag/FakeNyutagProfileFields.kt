/*
 * Нютаг (форк Element X) — тестовая замена полей профиля.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.test.nyutag

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.nyutag.NyutagProfileFields
import io.element.android.libraries.matrix.api.nyutag.NyutagProfileInfo

class FakeNyutagProfileFields(
    var profiles: MutableMap<UserId, NyutagProfileInfo> = mutableMapOf(),
    var updateResult: Result<Unit> = Result.success(Unit),
    /** false — ядро не ответило: get возвращает ошибку. */
    var available: Boolean = true,
) : NyutagProfileFields {
    val updates = mutableListOf<Pair<NyutagProfileInfo, NyutagProfileInfo>>()

    override suspend fun get(sessionId: SessionId, userId: UserId): Result<NyutagProfileInfo> =
        if (available) Result.success(profiles[userId] ?: NyutagProfileInfo()) else Result.failure(IllegalStateException("Nyutag core is unavailable"))

    override suspend fun update(sessionId: SessionId, old: NyutagProfileInfo, new: NyutagProfileInfo): Result<Unit> {
        updates += old to new
        return updateResult.onSuccess { profiles[UserId(sessionId.value)] = new.normalized() }
    }
}

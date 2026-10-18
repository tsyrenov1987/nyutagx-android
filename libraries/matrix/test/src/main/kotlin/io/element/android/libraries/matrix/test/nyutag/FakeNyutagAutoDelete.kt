/*
 * Нютаг (форк Element X) — подделка таймера автоудаления для тестов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.test.nyutag

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagAutoDelete
import io.element.android.libraries.matrix.api.nyutag.NyutagAutoDeleteRule

class FakeNyutagAutoDelete(
    var rules: MutableMap<RoomId, NyutagAutoDeleteRule> = mutableMapOf(),
    /** false — ядро не ответило. */
    var available: Boolean = true,
) : NyutagAutoDelete {
    var getCount = 0
        private set

    override suspend fun get(sessionId: SessionId, roomId: RoomId): Result<NyutagAutoDeleteRule> {
        getCount++
        return if (available) Result.success(rules[roomId] ?: NyutagAutoDeleteRule()) else Result.failure(IllegalStateException("Nyutag core is unavailable"))
    }

    override suspend fun set(sessionId: SessionId, roomId: RoomId, ttlSeconds: Long, nowMs: Long): Result<NyutagAutoDeleteRule> {
        if (!available) return Result.failure(IllegalStateException("Nyutag core is unavailable"))
        val rule = NyutagAutoDeleteRule(ttlSeconds, if (ttlSeconds > 0) nowMs else 0)
        rules[roomId] = rule
        return Result.success(rule)
    }
}

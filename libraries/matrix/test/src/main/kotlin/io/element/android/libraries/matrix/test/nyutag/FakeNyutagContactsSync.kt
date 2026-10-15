/*
 * Нютаг — подделка сверки записной книжки для тестов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.test.nyutag

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagContactsSync
import io.element.android.libraries.matrix.api.nyutag.NyutagMatchedContact

class FakeNyutagContactsSync(
    var cachedResult: List<NyutagMatchedContact>? = null,
    var syncResult: Result<List<NyutagMatchedContact>> = Result.success(emptyList()),
) : NyutagContactsSync {
    var syncCalls = 0
        private set

    override suspend fun cached(sessionId: SessionId) = cachedResult

    override suspend fun sync(sessionId: SessionId, force: Boolean): Result<List<NyutagMatchedContact>> {
        syncCalls++
        return syncResult
    }
}

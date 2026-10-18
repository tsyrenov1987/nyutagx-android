/*
 * Нютаг (форк Element X Android).
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.push.impl.test

import io.element.android.libraries.matrix.api.nyutag.NyutagPushLoopback
import io.element.android.tests.testutils.lambda.lambdaError

class FakeNyutagPushLoopback(
    private val notifyResult: (String, String) -> Result<Boolean> = { _, _ -> lambdaError() },
) : NyutagPushLoopback {
    override suspend fun notify(appId: String, pushKey: String): Result<Boolean> = notifyResult(appId, pushKey)
}

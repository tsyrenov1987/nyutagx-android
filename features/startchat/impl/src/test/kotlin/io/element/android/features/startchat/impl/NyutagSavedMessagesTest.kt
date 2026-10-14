/*
 * Нютаг (форк Element X Android).
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.startchat.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.enterprise.test.FakeSessionEnterpriseService
import io.element.android.libraries.matrix.api.createroom.CreateRoomParameters
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NyutagSavedMessagesTest {
    @Test
    fun `stored and joined room is reused without creating a new one`() = runTest {
        val client = FakeMatrixClient(
            getAccountDataLambda = { Result.success("{\"room_id\":\"${A_ROOM_ID.value}\"}") },
            getJoinedRoomIdsResult = { Result.success(setOf(A_ROOM_ID)) },
        )
        client.createRoomResult = { error("must not create a room") }
        val result = DefaultNyutagSavedMessages(client, FakeSessionEnterpriseService()).roomId("Saved", "Notes")
        assertThat(result.getOrNull()).isEqualTo(A_ROOM_ID)
    }

    @Test
    fun `missing or left room is created once and remembered in account data`() = runTest {
        val setAccountData = lambdaRecorder<String, String, Result<Unit>> { _, _ -> Result.success(Unit) }
        var params: CreateRoomParameters? = null
        val client = FakeMatrixClient(
            getAccountDataLambda = { Result.success("{\"room_id\":\"${A_ROOM_ID.value}\"}") },
            getJoinedRoomIdsResult = { Result.success(emptySet()) },
            setAccountDataLambda = setAccountData,
        )
        client.createRoomResult = {
            params = it
            Result.success(A_ROOM_ID_2)
        }
        val result = DefaultNyutagSavedMessages(
            client,
            FakeSessionEnterpriseService(isEncryptionDisabledResult = { false }),
        ).roomId("Saved", "Notes")
        assertThat(result.getOrNull()).isEqualTo(A_ROOM_ID_2)
        assertThat(params?.name).isEqualTo("Saved")
        assertThat(params?.invite).isEmpty()
        assertThat(params?.isEncrypted).isTrue()
        setAccountData.assertions().isCalledOnce().with(
            value(NYUTAG_SAVED_MESSAGES_TYPE),
            value("{\"room_id\":\"${A_ROOM_ID_2.value}\"}"),
        )
    }
}

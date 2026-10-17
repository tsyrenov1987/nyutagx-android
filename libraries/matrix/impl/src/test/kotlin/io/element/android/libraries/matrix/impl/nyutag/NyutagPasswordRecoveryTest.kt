/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import io.element.android.libraries.matrix.test.verification.FakeSessionVerificationService
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NyutagPasswordRecoveryTest {
    private val password = "correct horse"
    private val enabledWith = mutableListOf<String?>()
    private val recoveredWith = mutableListOf<String>()

    private fun encryption(state: RecoveryState, recoverFails: Boolean = false): EncryptionService {
        val fake = FakeEncryptionService(enableRecoveryLambda = { _, passphrase ->
            enabledWith += passphrase
            Result.success("key")
        })
        fake.recoveryStateStateFlow.value = state
        if (recoverFails) fake.givenRecoverFailure(IllegalStateException("wrong passphrase"))
        return object : EncryptionService by fake {
            override suspend fun recover(recoveryKey: String): Result<Unit> {
                recoveredWith += recoveryKey
                return fake.recover(recoveryKey)
            }
        }
    }

    private fun client(encryption: EncryptionService, verified: SessionVerifiedStatus) = FakeMatrixClient(
        encryptionService = encryption,
        sessionVerificationService = FakeSessionVerificationService(initialSessionVerifiedStatus = verified),
    )

    @Test
    fun `fresh account with a verified device gets a key store locked by the password`() = runTest {
        NyutagPasswordRecovery.afterLogin(client(encryption(RecoveryState.DISABLED), SessionVerifiedStatus.Verified), password)
        assertThat(enabledWith).containsExactly(password)
        assertThat(recoveredWith).isEmpty()
    }

    @Test
    fun `unverified device never creates a key store, that would replace the identity`() = runTest {
        NyutagPasswordRecovery.afterLogin(client(encryption(RecoveryState.DISABLED), SessionVerifiedStatus.NotVerified), password)
        assertThat(enabledWith).isEmpty()
        assertThat(recoveredWith).isEmpty()
    }

    @Test
    fun `new device opens the existing key store with the password`() = runTest {
        NyutagPasswordRecovery.afterLogin(client(encryption(RecoveryState.INCOMPLETE), SessionVerifiedStatus.NotVerified), password)
        assertThat(recoveredWith).containsExactly(password)
        assertThat(enabledWith).isEmpty()
    }

    @Test
    fun `wrong password leaves the key store alone`() = runTest {
        NyutagPasswordRecovery.afterLogin(
            client(encryption(RecoveryState.INCOMPLETE, recoverFails = true), SessionVerifiedStatus.NotVerified),
            password,
        )
        assertThat(recoveredWith).containsExactly(password)
        assertThat(enabledWith).isEmpty()
    }

    @Test
    fun `enabled recovery is left as is`() = runTest {
        NyutagPasswordRecovery.afterLogin(client(encryption(RecoveryState.ENABLED), SessionVerifiedStatus.Verified), password)
        assertThat(enabledWith).isEmpty()
        assertThat(recoveredWith).isEmpty()
    }

    @Test
    fun `after an identity reset the empty key store is locked by the password`() = runTest {
        NyutagPasswordRecovery.afterIdentityReset(encryption(RecoveryState.DISABLED), password)
        assertThat(enabledWith).containsExactly(password)
    }
}

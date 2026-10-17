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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class NyutagPasswordRecoveryTest {
    private val password = "correct horse"
    private val enabledWith = mutableListOf<String?>()
    private val recoveredWith = mutableListOf<String>()

    private lateinit var fake: FakeEncryptionService

    private fun encryption(state: RecoveryState, recoverFails: Boolean = false): EncryptionService {
        fake = FakeEncryptionService(enableRecoveryLambda = { _, passphrase ->
            enabledWith += passphrase
            Result.success("key")
        })
        fake.recoveryStateStateFlow.value = state
        if (recoverFails) fake.givenRecoverFailure(IllegalStateException("wrong passphrase"))
        val fake = fake
        return object : EncryptionService by fake {
            override suspend fun recover(recoveryKey: String): Result<Unit> {
                recoveredWith += recoveryKey
                return fake.recover(recoveryKey)
            }
        }
    }

    private fun TestScope.client(encryption: EncryptionService, verified: SessionVerifiedStatus) = FakeMatrixClient(
        encryptionService = encryption,
        sessionVerificationService = FakeSessionVerificationService(initialSessionVerifiedStatus = verified),
        sessionCoroutineScope = backgroundScope,
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

    @Test
    fun `key store opened later with the old recovery key is re-locked under the password`() = runTest {
        val encryption = encryption(RecoveryState.INCOMPLETE, recoverFails = true)
        NyutagPasswordRecovery.afterLogin(client(encryption, SessionVerifiedStatus.NotVerified), password)
        assertThat(enabledWith).isEmpty()
        fake.recoveryStateStateFlow.value = RecoveryState.ENABLED
        settleBackground()
        assertThat(enabledWith).containsExactly(password)
    }

    @Test
    fun `identity reset after a wrong password locks the key store once, no second re-lock`() = runTest {
        val encryption = encryption(RecoveryState.INCOMPLETE, recoverFails = true)
        NyutagPasswordRecovery.afterLogin(client(encryption, SessionVerifiedStatus.NotVerified), password)
        fake.recoveryStateStateFlow.value = RecoveryState.DISABLED
        NyutagPasswordRecovery.afterIdentityReset(encryption, password)
        fake.recoveryStateStateFlow.value = RecoveryState.ENABLED
        settleBackground()
        assertThat(enabledWith).containsExactly(password)
    }

    /** Перезапирание живёт в backgroundScope, а advanceUntilIdle фоновые задачи без переднего плана не крутит. */
    private fun TestScope.settleBackground() {
        advanceTimeBy(1.seconds)
        runCurrent()
    }
}

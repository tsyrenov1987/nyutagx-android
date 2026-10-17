/*
 * Нютаг — ключи шифрования под паролем аккаунта, чтобы переписка переживала переустановку, как в Telegram.
 *
 * Element X держит ключи комнат в бэкапе на сервере, а ключ от бэкапа и ключи личности — в хранилище секретов,
 * запертом «ключом восстановления». Люди его не сохраняют и после переустановки остаются без переписки.
 * Здесь хранилище запирается паролем аккаунта: при регистрации заводим его, при входе на новом устройстве открываем
 * тем же паролем — человек просто входит и видит историю. Сервер и так получает пароль при входе, поэтому защита
 * от перехвата и от утечки базы остаётся, а от самого сервера — нет; для закрытого ядра Нютага это осознанный выбор.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object NyutagPasswordRecovery {
    /** Сколько ждём, пока SDK после первого синка поймёт, есть ли на сервере хранилище секретов. */
    private val SETTLE_TIMEOUT = 30.seconds

    /** Сколько ждём подтверждения устройства после открытия хранилища, чтобы экран «Подтвердите личность» не мигал. */
    private val VERIFIED_TIMEOUT = 10.seconds

    /**
     * Сразу после входа паролем, пока клиент уже синхронизируется, а экранов сессии ещё нет. Пароль в лог не попадает.
     * Хранилища нет — заводим его под паролем. Есть, но секретов на устройстве нет — открываем паролем.
     * Пароль не подходит (сменён через «Забыли пароль?» или хранилище заперто старым ключом восстановления) — остаётся
     * обычный путь Element X: другое устройство, ключ восстановления или сброс личности, см. [afterIdentityReset].
     */
    suspend fun afterLogin(client: MatrixClient, password: String) {
        val encryption = client.encryptionService
        when (encryption.settledState(SETTLE_TIMEOUT)) {
            null -> Timber.w("Nyutag recovery: state still unknown after $SETTLE_TIMEOUT")
            RecoveryState.ENABLED -> Timber.i("Nyutag recovery: already enabled on this device")
            RecoveryState.INCOMPLETE -> encryption.recover(password).fold(
                onSuccess = {
                    Timber.i("Nyutag recovery: key store opened with the account password")
                    client.waitUntilVerified()
                },
                onFailure = { Timber.w("Nyutag recovery: the account password does not open the key store (${it.javaClass.simpleName})") },
            )
            // Без подтверждённого устройства SDK завёл бы новую личность вместо той, что уже есть у аккаунта.
            RecoveryState.DISABLED -> if (client.sessionVerificationService.sessionVerifiedStatus.value == SessionVerifiedStatus.Verified) {
                encryption.enable(password)
            } else {
                Timber.w("Nyutag recovery: device is not verified, leaving the key store to the usual flow")
            }
            else -> Unit
        }
    }

    /** После сброса личности паролем хранилище пустое, а устройство подтверждено — заводим хранилище тем же паролем. */
    suspend fun afterIdentityReset(encryption: EncryptionService, password: String) {
        when (encryption.settledState(SETTLE_TIMEOUT)) {
            RecoveryState.DISABLED -> encryption.enable(password)
            else -> Timber.w("Nyutag recovery: key store not empty after the identity reset, leaving it")
        }
    }

    private suspend fun EncryptionService.settledState(timeout: Duration): RecoveryState? = withTimeoutOrNull(timeout) {
        recoveryStateStateFlow.first { it == RecoveryState.ENABLED || it == RecoveryState.DISABLED || it == RecoveryState.INCOMPLETE }
    }

    private suspend fun EncryptionService.enable(password: String) {
        enableRecovery(waitForBackupsToUpload = false, passphrase = password).fold(
            onSuccess = { Timber.i("Nyutag recovery: key store set up under the account password") },
            onFailure = { Timber.w("Nyutag recovery: setting up the key store failed (${it.javaClass.simpleName})") },
        )
    }

    private suspend fun MatrixClient.waitUntilVerified() {
        withTimeoutOrNull(VERIFIED_TIMEOUT) {
            sessionVerificationService.sessionVerifiedStatus.first { it == SessionVerifiedStatus.Verified }
        } ?: Timber.w("Nyutag recovery: device still not verified after $VERIFIED_TIMEOUT")
    }
}

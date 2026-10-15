/*
 * Нютаг — правила пуша для входящего звонка.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import io.element.android.libraries.matrix.api.core.SessionId

/**
 * Tuwunel не знает правила `.m.rule.rtc_notification` (MSC4075), поэтому событие «звонок» от Element Call
 * не превращается в пуш и собеседник не слышит входящий. Клиент сам ставит такое правило в аккаунт.
 */
fun interface NyutagCallPushRules {
    suspend fun ensure(sessionId: SessionId): Result<Unit>
}

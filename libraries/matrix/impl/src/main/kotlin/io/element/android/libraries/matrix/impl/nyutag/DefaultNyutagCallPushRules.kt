/*
 * Нютаг — правила пуша для входящего звонка (см. NyutagCallPushRules).
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagCallPushRules
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

@ContributesBinding(AppScope::class)
class DefaultNyutagCallPushRules(
    @ApplicationContext
    private val context: Context,
    private val sessionStore: SessionStore,
) : NyutagCallPushRules {
    override suspend fun ensure(sessionId: SessionId): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionStore.getSession(sessionId.value)?.accessToken
                ?: throw IOException("No session for ${sessionId.value}")
            for ((ruleId, eventType) in RULES) {
                val body = """{"conditions":[{"kind":"event_match","key":"type","pattern":"$eventType"}],""" +
                    """"actions":["notify",{"set_tweak":"sound","value":"default"}]}"""
                val response = NyutagHttp.request(
                    context,
                    "PUT",
                    "/_matrix/client/v3/pushrules/global/override/$ruleId",
                    jsonBody = body,
                    bearer = token,
                )
                if (response.status != 200) throw IOException("push rule $ruleId HTTP ${response.status}")
            }
            Timber.i("Nyutag call push rules: ok")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Nyutag call push rules: ${e.javaClass.simpleName} ${e.message}")
            Result.failure(e)
        }
    }

    private companion object {
        val RULES = listOf(
            "nyutag.rtc_notification" to "org.matrix.msc4075.rtc.notification",
            "nyutag.call_notify" to "org.matrix.msc4075.call.notify",
        )
    }
}

/*
 * Нютаг (форк Element X) — автоудаление сообщений: state-событие комнаты через ядро.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagAutoDelete
import io.element.android.libraries.matrix.api.nyutag.NyutagAutoDeleteRule
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder

@ContributesBinding(AppScope::class)
class DefaultNyutagAutoDelete(
    @ApplicationContext
    private val context: Context,
    private val sessionStore: SessionStore,
) : NyutagAutoDelete {
    override suspend fun get(sessionId: SessionId, roomId: RoomId): Result<NyutagAutoDeleteRule> = call {
        val response = NyutagHttp.request(context, "GET", statePath(roomId), bearer = token(sessionId))
        // 404 — таймер в этом чате ни разу не включали.
        if (response.status == 404) return@call NyutagAutoDeleteRule()
        if (response.status != 200) throw IOException("autodelete HTTP ${response.status}")
        parseRule(response.json())
    }

    override suspend fun set(sessionId: SessionId, roomId: RoomId, ttlSeconds: Long, nowMs: Long): Result<NyutagAutoDeleteRule> = call {
        val rule = NyutagAutoDeleteRule(ttlSeconds = ttlSeconds.coerceAtLeast(0), sinceMs = if (ttlSeconds > 0) nowMs else 0)
        val body = JSONObject().put("ttl", rule.ttlSeconds).put("since", rule.sinceMs).toString()
        val response = NyutagHttp.request(context, "PUT", statePath(roomId), jsonBody = body, bearer = token(sessionId))
        if (response.status != 200) throw IOException("autodelete set HTTP ${response.status}")
        rule
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Nyutag autodelete: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    private suspend fun token(sessionId: SessionId): String =
        sessionStore.getSession(sessionId.value)?.accessToken ?: throw IOException("No session for ${sessionId.value}")

    private fun statePath(roomId: RoomId) =
        "/_matrix/client/v3/rooms/" + encode(roomId.value) + "/state/" + NyutagAutoDeleteRule.EVENT_TYPE + "/"

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        internal fun parseRule(json: JSONObject) = NyutagAutoDeleteRule(
            ttlSeconds = json.optLong("ttl", 0).coerceAtLeast(0),
            sinceMs = json.optLong("since", 0).coerceAtLeast(0),
        )
    }
}

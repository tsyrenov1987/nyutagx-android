/*
 * Нютаг — подготовка исходящего звонка (см. NyutagCallStart).
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
import io.element.android.libraries.matrix.api.nyutag.NyutagCallStart
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

@ContributesBinding(AppScope::class)
class DefaultNyutagCallStart(
    @ApplicationContext
    private val context: Context,
    private val sessionStore: SessionStore,
) : NyutagCallStart {
    override suspend fun beforeStart(sessionId: SessionId, roomId: RoomId, isAudioCall: Boolean) = withContext(Dispatchers.IO) {
        try {
            val token = sessionStore.getSession(sessionId.value)?.accessToken
                ?: throw IOException("No session for ${sessionId.value}")
            val room = URLEncoder.encode(roomId.value, "UTF-8")
            var othersInCall = false
            val state = NyutagHttp.request(context, "GET", "/_matrix/client/v3/rooms/$room/state", bearer = token)
            if (state.status == 200) {
                val now = System.currentTimeMillis()
                val events = JSONArray(state.body)
                for (i in 0 until events.length()) {
                    val event = events.getJSONObject(i)
                    if (event.optString("type") != CALL_MEMBER_TYPE) continue
                    val content = event.optJSONObject("content") ?: continue
                    if (content.length() == 0) continue
                    val alive = event.optLong("origin_server_ts", now) + content.optLong("expires", 0) > now
                    if (event.optString("sender") == sessionId.value) {
                        val key = URLEncoder.encode(event.optString("state_key"), "UTF-8")
                        val cleared = NyutagHttp.request(context, "PUT", "/_matrix/client/v3/rooms/$room/state/$CALL_MEMBER_TYPE/$key", jsonBody = "{}", bearer = token)
                        Timber.i("Nyutag call: cleared own stale membership → HTTP ${cleared.status}")
                    } else if (alive) {
                        othersInCall = true
                    }
                }
            } else {
                Timber.w("Nyutag call: state HTTP ${state.status}")
            }
            if (othersInCall) {
                Timber.i("Nyutag call: joining an existing call, no ring")
                return@withContext
            }
            val body = JSONObject()
                .put("sender_ts", System.currentTimeMillis())
                .put("lifetime", RING_LIFETIME_MS)
                .put("m.call.intent", if (isAudioCall) "audio" else "video")
                .toString()
            val txn = "nyutag-ring-${UUID.randomUUID()}"
            val sent = NyutagHttp.request(context, "PUT", "/_matrix/client/v3/rooms/$room/send/$RING_EVENT_TYPE/$txn", jsonBody = body, bearer = token)
            Timber.i("Nyutag call: ring → HTTP ${sent.status}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Nyutag call: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    companion object {
        /** Незашифрованный сигнал «звоню»; содержимое — то, что сервер и так видит по `call.member`. */
        const val RING_EVENT_TYPE = "chat.nyutag.ring"
        const val RING_LIFETIME_MS = 90_000
        private const val CALL_MEMBER_TYPE = "org.matrix.msc3401.call.member"
    }
}

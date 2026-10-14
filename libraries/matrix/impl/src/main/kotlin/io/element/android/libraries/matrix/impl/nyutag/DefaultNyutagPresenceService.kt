/*
 * Нютаг (форк Element X) — присутствие собеседника.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.nyutag.NyutagPresence
import io.element.android.libraries.matrix.api.nyutag.NyutagPresenceService
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder

@ContributesBinding(AppScope::class)
class DefaultNyutagPresenceService(
    @ApplicationContext
    private val context: Context,
    private val sessionStore: SessionStore,
) : NyutagPresenceService {
    override suspend fun presence(sessionId: SessionId, userId: UserId): Result<NyutagPresence> = withContext(Dispatchers.IO) {
        try {
            val token = sessionStore.getSession(sessionId.value)?.accessToken
                ?: throw IOException("No session for ${sessionId.value}")
            val path = "/_matrix/client/v3/presence/${URLEncoder.encode(userId.value, "UTF-8")}/status"
            val response = NyutagHttp.request(context, "GET", path, bearer = token)
            if (response.status != 200) throw IOException("presence HTTP ${response.status}")
            val json = response.json()
            val now = System.currentTimeMillis()
            Result.success(
                NyutagPresence(
                    isOnline = json.optString("presence") == "online" && json.optBoolean("currently_active"),
                    lastActiveTimestamp = if (json.has("last_active_ago")) now - json.optLong("last_active_ago") else null,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Nyutag presence: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }
}

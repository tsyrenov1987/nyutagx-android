/*
 * Нютаг (форк Element X) — каталог сообществ.
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
import io.element.android.libraries.matrix.api.nyutag.NyutagCommunities
import io.element.android.libraries.matrix.api.nyutag.NyutagCommunity
import io.element.android.libraries.matrix.api.nyutag.NyutagCommunityPage
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

@ContributesBinding(AppScope::class)
class DefaultNyutagCommunities(
    @ApplicationContext
    private val context: Context,
    private val sessionStore: SessionStore,
) : NyutagCommunities {
    override suspend fun communities(sessionId: SessionId, searchTerm: String?, since: String?): Result<NyutagCommunityPage> = withContext(Dispatchers.IO) {
        try {
            val token = sessionStore.getSession(sessionId.value)?.accessToken
                ?: throw IOException("No session for ${sessionId.value}")
            val filter = JSONObject().put("room_types", JSONArray().put("m.space"))
            searchTerm?.trim()?.takeIf { it.isNotEmpty() }?.let { filter.put("generic_search_term", it) }
            val body = JSONObject().put("limit", PAGE_SIZE).put("filter", filter)
            since?.let { body.put("since", it) }
            val response = NyutagHttp.request(context, "POST", "/_matrix/client/v3/publicRooms", jsonBody = body.toString(), bearer = token)
            if (response.status != 200) throw IOException("publicRooms HTTP ${response.status}")
            val json = response.json()
            val chunk = json.optJSONArray("chunk") ?: JSONArray()
            val communities = (0 until chunk.length()).mapNotNull { chunk.optJSONObject(it)?.toCommunity() }
            Result.success(NyutagCommunityPage(communities, json.optString("next_batch").takeIf { it.isNotEmpty() }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Nyutag communities: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    private fun JSONObject.toCommunity(): NyutagCommunity? {
        val roomId = runCatching { RoomId(getString("room_id")) }.getOrNull() ?: return null
        return NyutagCommunity(
            roomId = roomId,
            name = optString("name").takeIf { it.isNotEmpty() },
            topic = optString("topic").takeIf { it.isNotEmpty() },
            alias = optString("canonical_alias").takeIf { it.isNotEmpty() },
            avatarUrl = optString("avatar_url").takeIf { it.isNotEmpty() },
            membersCount = optInt("num_joined_members"),
            isOpen = optString("join_rule", "public") == "public",
        )
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}

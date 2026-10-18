/*
 * Нютаг (форк Element X) — поля профиля «о себе» (MSC4133).
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
import io.element.android.libraries.matrix.api.nyutag.NyutagProfileFields
import io.element.android.libraries.matrix.api.nyutag.NyutagProfileInfo
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder

@ContributesBinding(AppScope::class)
class DefaultNyutagProfileFields(
    @ApplicationContext
    private val context: Context,
    private val sessionStore: SessionStore,
) : NyutagProfileFields {
    override suspend fun get(sessionId: SessionId, userId: UserId): Result<NyutagProfileInfo> = call {
        val response = NyutagHttp.request(context, "GET", profilePath(userId), bearer = token(sessionId))
        // 404 — профиля ещё нет на сервере, это не ошибка: полей просто нет.
        if (response.status == 404) return@call NyutagProfileInfo()
        if (response.status != 200) throw IOException("profile HTTP ${response.status}")
        val json = response.json()
        NyutagProfileInfo.fromFields(PROFILE_KEYS.associateWith { json.optString(it).takeIf { v -> json.has(it) && v.isNotEmpty() } })
    }

    override suspend fun update(sessionId: SessionId, old: NyutagProfileInfo, new: NyutagProfileInfo): Result<Unit> = call {
        val token = token(sessionId)
        val userId = UserId(sessionId.value)
        val before = old.normalized().toFields()
        new.normalized().toFields().forEach { (key, value) ->
            if (before[key] == value) return@forEach
            val path = profilePath(userId) + "/" + encode(key)
            val response = if (value.isEmpty()) {
                NyutagHttp.request(context, "DELETE", path, bearer = token)
            } else {
                NyutagHttp.request(context, "PUT", path, jsonBody = JSONObject().put(key, value).toString(), bearer = token)
            }
            // Удаление отсутствующего поля сервер может вернуть 404 — результат тот же.
            if (response.status != 200 && !(value.isEmpty() && response.status == 404)) {
                throw IOException("profile $key HTTP ${response.status}")
            }
        }
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Nyutag profile fields: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    private suspend fun token(sessionId: SessionId): String =
        sessionStore.getSession(sessionId.value)?.accessToken ?: throw IOException("No session for ${sessionId.value}")

    private fun profilePath(userId: UserId) = "/_matrix/client/v3/profile/" + encode(userId.value)

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object {
        val PROFILE_KEYS = listOf(
            NyutagProfileInfo.KEY_ABOUT,
            NyutagProfileInfo.KEY_TOONTO,
            NyutagProfileInfo.KEY_UG,
            NyutagProfileInfo.KEY_EAST_YEAR,
        )
    }
}

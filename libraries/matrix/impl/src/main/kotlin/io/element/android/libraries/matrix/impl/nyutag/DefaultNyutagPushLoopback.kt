/*
 * Нютаг — тестовый пуш через ядро (см. NyutagPushLoopback).
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.nyutag.NyutagPushLoopback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

@ContributesBinding(AppScope::class)
class DefaultNyutagPushLoopback(
    @ApplicationContext
    private val context: Context,
) : NyutagPushLoopback {
    override suspend fun notify(appId: String, pushKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("app_id", appId).put("pushkey", pushKey).toString()
            val response = NyutagHttp.request(context, "POST", "/_nyutag/v1/push/test", jsonBody = body)
            when (response.status) {
                200 -> {
                    val rejected = response.json().optJSONArray("rejected")
                    Result.success((0 until (rejected?.length() ?: 0)).any { rejected?.optString(it) == pushKey })
                }
                429 -> Result.failure(NyutagPushLoopback.RateLimitedException())
                else -> Result.failure(IOException("Push test HTTP ${response.status}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

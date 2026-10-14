/*
 * Нютаг — приглашения по одноразовому коду через сервис ядра `/_nyutag/v1/`.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagContactMedium
import io.element.android.libraries.matrix.api.nyutag.NyutagInvite
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.nyutag.NyutagInvites
import io.element.android.libraries.matrix.api.nyutag.NyutagSignUpConfig
import io.element.android.libraries.matrix.api.nyutag.NyutagVerification
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder

@ContributesBinding(AppScope::class)
class DefaultNyutagInvites(
    @ApplicationContext
    private val context: Context,
    private val sessionStore: SessionStore,
) : NyutagInvites {
    override suspend fun signUpConfig(): Result<NyutagSignUpConfig> = call {
        val response = NyutagHttp.request(context, "GET", "/_nyutag/v1/config")
        if (response.status != 200) throw response.toException()
        val json = response.json()
        NyutagSignUpConfig(
            requireInvite = json.optBoolean("require_invite", true),
            requireContact = json.optBoolean("require_contact", false),
            emailAvailable = json.optBoolean("email", false),
            phoneAvailable = json.optBoolean("msisdn", false),
            requireEmail = json.optBoolean("require_email", false),
            requirePhone = json.optBoolean("require_phone", false),
            phoneUnverified = json.optBoolean("phone_unverified", false),
        )
    }

    override suspend fun checkInvite(code: String): Result<String?> = call {
        val body = JSONObject().put("code", code).toString()
        val response = NyutagHttp.request(context, "POST", "/_nyutag/v1/invites/check", jsonBody = body)
        val json = response.json()
        if (response.status != 200 || !json.optBoolean("valid")) throw response.toException()
        json.optString("inviter").takeIf { it.isNotEmpty() }
    }

    override suspend fun checkUsername(username: String): Result<Unit> = call {
        val path = "/_matrix/client/v3/register/available?username=" + URLEncoder.encode(username, "UTF-8")
        val response = NyutagHttp.request(context, "GET", path)
        if (response.status != 200) throw response.toException()
        if (!response.json().optBoolean("available")) throw NyutagInviteException(NyutagInviteException.USER_IN_USE, "Username is taken")
    }

    override suspend fun startVerification(medium: NyutagContactMedium, address: String, inviteCode: String): Result<NyutagVerification> = call {
        val body = JSONObject().put("medium", medium.wire).put("address", address).put("code", inviteCode).toString()
        val response = NyutagHttp.request(context, "POST", "/_nyutag/v1/verify/start", jsonBody = body)
        val json = response.json()
        if (response.status != 200 || !json.has("sid")) throw response.toException()
        NyutagVerification(sid = json.getString("sid"), resendAfterSeconds = json.optInt("resend_after_s", 60))
    }

    override suspend fun checkVerification(sid: String, token: String): Result<Unit> = call {
        val body = JSONObject().put("sid", sid).put("token", token).toString()
        val response = NyutagHttp.request(context, "POST", "/_nyutag/v1/verify/check", jsonBody = body)
        if (response.status != 200 || !response.json().optBoolean("verified")) throw response.toException()
    }

    override suspend fun resolveLogin(identifier: String, password: String): Result<String> = call {
        val body = JSONObject().put("identifier", identifier).put("password", password).toString()
        val response = NyutagHttp.request(context, "POST", "/_nyutag/v1/resolve", jsonBody = body)
        val json = response.json()
        if (response.status != 200 || !json.has("user_id")) throw response.toException()
        json.getString("user_id")
    }

    override suspend fun checkPhone(phone: String, inviteCode: String): Result<String> = call {
        val body = JSONObject().put("phone", phone).put("code", inviteCode).toString()
        val response = NyutagHttp.request(context, "POST", "/_nyutag/v1/phone/check", jsonBody = body)
        val json = response.json()
        if (response.status != 200 || !json.has("phone")) throw response.toException()
        json.getString("phone")
    }

    override suspend fun register(code: String, username: String, password: String, sid: String?, phone: String?): Result<String> = call {
        val body = JSONObject()
            .put("code", code)
            .put("username", username)
            .put("password", password)
            .apply { if (sid != null) put("sid", sid) }
            .apply { if (phone != null) put("phone", phone) }
            .toString()
        val response = NyutagHttp.request(context, "POST", "/_nyutag/v1/register", jsonBody = body)
        val json = response.json()
        if (response.status != 200 || !json.has("user_id")) throw response.toException()
        json.getString("user_id").also { Timber.i("Nyutag: registered $it by invite") }
    }

    override suspend fun createInvite(sessionId: SessionId): Result<NyutagInvite> = call {
        val token = sessionStore.getSession(sessionId.value)?.accessToken
            ?: throw NyutagInviteException("M_UNKNOWN_TOKEN", "No session for ${sessionId.value}")
        val response = NyutagHttp.request(context, "POST", "/_nyutag/v1/invites", bearer = token)
        val json = response.json()
        if (response.status != 200 || !json.has("code")) throw response.toException()
        NyutagInvite(code = json.getString("code"), expiresAtMillis = json.optLong("expires_at"))
    }

    override suspend fun lookupPhones(sessionId: SessionId, phones: List<String>): Result<Map<String, String>> = call {
        val body = JSONObject().put("phones", JSONArray(phones)).toString()
        val response = NyutagHttp.request(context, "POST", "/_nyutag/v1/lookup", jsonBody = body, bearer = token(sessionId))
        val json = response.json()
        if (response.status != 200 || !json.has("matches")) throw response.toException()
        val matches = json.getJSONArray("matches")
        (0 until matches.length()).associate { i ->
            val match = matches.getJSONObject(i)
            match.getString("phone") to match.getString("user_id")
        }
    }

    override suspend fun hasPhone(sessionId: SessionId): Result<Boolean> = call {
        val response = NyutagHttp.request(context, "GET", "/_nyutag/v1/me", bearer = token(sessionId))
        if (response.status != 200) throw response.toException()
        response.json().optBoolean("phone")
    }

    override suspend fun setPhone(sessionId: SessionId, phone: String): Result<Unit> = call {
        val body = JSONObject().put("phone", phone).toString()
        val response = NyutagHttp.request(context, "POST", "/_nyutag/v1/me/phone", jsonBody = body, bearer = token(sessionId))
        if (response.status != 200) throw response.toException()
    }

    private suspend fun token(sessionId: SessionId): String = sessionStore.getSession(sessionId.value)?.accessToken
        ?: throw NyutagInviteException("M_UNKNOWN_TOKEN", "No session for ${sessionId.value}")

    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Nyutag invites: request on ${Thread.currentThread().name}")
            Result.success(block())
        } catch (e: NyutagInviteException) {
            Timber.w("Nyutag invites: ${e.errcode}")
            Result.failure(e)
        } catch (e: IOException) {
            Timber.w(e, "Nyutag invites: network error")
            Result.failure(NyutagInviteException(NyutagInviteException.NETWORK, e.message ?: "network error"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Nyutag invites: unexpected error")
            Result.failure(NyutagInviteException("NYUTAG_CLIENT_ERROR", e.javaClass.simpleName))
        }
    }

    private fun NyutagHttp.Response.toException(): NyutagInviteException {
        val json = json()
        return NyutagInviteException(json.optString("errcode", "HTTP_$status"), json.optString("error", "HTTP $status"))
    }
}

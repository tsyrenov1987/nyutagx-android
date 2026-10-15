/*
 * Нютаг — сверка записной книжки с сервером: кто из контактов уже в Нютаге.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.nyutag.NyutagAddressBook
import io.element.android.libraries.matrix.api.nyutag.NyutagContactsSync
import io.element.android.libraries.matrix.api.nyutag.NyutagInvites
import io.element.android.libraries.matrix.api.nyutag.NyutagMatchedContact
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@ContributesBinding(AppScope::class)
class DefaultNyutagContactsSync(
    @ApplicationContext private val context: Context,
    private val invites: NyutagInvites,
    private val addressBook: NyutagAddressBook,
    private val clientProvider: MatrixClientProvider,
) : NyutagContactsSync {
    private val prefs by lazy { context.getSharedPreferences("nyutag_contacts", Context.MODE_PRIVATE) }

    override suspend fun cached(sessionId: SessionId): List<NyutagMatchedContact>? = withContext(Dispatchers.IO) {
        prefs.getString(key(sessionId), null)?.let(::parse)
    }

    override suspend fun sync(sessionId: SessionId, force: Boolean): Result<List<NyutagMatchedContact>> {
        val syncedAt = prefs.getLong(key(sessionId) + ".at", 0L)
        if (!force && System.currentTimeMillis() - syncedAt < SYNC_INTERVAL_MS) {
            cached(sessionId)?.let { return Result.success(it) }
        }
        val contacts = addressBook.contacts()
        if (contacts.isEmpty()) return Result.success(emptyList())
        val byPhone = contacts.associateBy { it.phone }
        val client = clientProvider.getOrNull(sessionId)
        val previous = cached(sessionId).orEmpty().associateBy { it.user.userId }
        val matched = mutableListOf<NyutagMatchedContact>()
        for (batch in contacts.map { it.phone }.chunked(BATCH)) {
            val matches = invites.lookupPhones(sessionId, batch).getOrElse { return Result.failure(it) }
            for ((phone, userId) in matches) {
                val contact = byPhone[phone] ?: continue
                val id = UserId(userId)
                // Профиль (аватар, имя в Нютаге) — с сервера; не вышло — берём прошлый или пустой.
                val user = client?.getProfile(id)?.getOrNull() ?: previous[id]?.user ?: MatrixUser(id)
                matched += NyutagMatchedContact(name = contact.name, phone = phone, user = user)
            }
        }
        val result = matched.distinctBy { it.user.userId }.sortedBy { it.name.lowercase() }
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(key(sessionId), serialize(result))
                .putLong(key(sessionId) + ".at", System.currentTimeMillis())
                .apply()
        }
        return Result.success(result)
    }

    private fun key(sessionId: SessionId) = "matched." + sessionId.value

    private fun serialize(contacts: List<NyutagMatchedContact>): String = JSONArray().also { array ->
        for (contact in contacts) {
            array.put(
                JSONObject()
                    .put("name", contact.name)
                    .put("phone", contact.phone)
                    .put("user_id", contact.user.userId.value)
                    .put("display_name", contact.user.displayName ?: JSONObject.NULL)
                    .put("avatar_url", contact.user.avatarUrl ?: JSONObject.NULL)
            )
        }
    }.toString()

    private fun parse(json: String): List<NyutagMatchedContact>? = runCatching {
        val array = JSONArray(json)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            NyutagMatchedContact(
                name = o.getString("name"),
                phone = o.getString("phone"),
                user = MatrixUser(
                    userId = UserId(o.getString("user_id")),
                    displayName = o.optString("display_name").takeIf { it.isNotEmpty() && !o.isNull("display_name") },
                    avatarUrl = o.optString("avatar_url").takeIf { it.isNotEmpty() && !o.isNull("avatar_url") },
                ),
            )
        }
    }.getOrNull()

    private companion object {
        // Столько номеров сервер принимает в одном запросе поиска.
        const val BATCH = 500
        const val SYNC_INTERVAL_MS = 60 * 60 * 1000L
    }
}

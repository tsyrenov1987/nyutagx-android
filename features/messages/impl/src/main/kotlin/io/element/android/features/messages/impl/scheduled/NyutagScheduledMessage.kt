/*
 * Нютаг (форк Element X) — отложенные сообщения, как в Telegram: очередь живёт на телефоне.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.scheduled

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.IntentionalMention
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class NyutagScheduledMessage(
    val id: String,
    val sessionId: String,
    val roomId: String,
    val body: String,
    val html: String? = null,
    /** Id упомянутых пользователей; [ROOM_MENTION] — упоминание всего чата. */
    val mentions: List<String> = emptyList(),
    val sendAtMs: Long,
) {
    fun isIn(sessionId: SessionId, roomId: RoomId) = this.sessionId == sessionId.value && this.roomId == roomId.value

    fun intentionalMentions(): List<IntentionalMention> = mentions.map {
        if (it == ROOM_MENTION) IntentionalMention.Room else IntentionalMention.User(UserId(it))
    }

    companion object {
        const val ROOM_MENTION = "@room"

        private val json = Json { ignoreUnknownKeys = true }

        fun mentionsOf(intentionalMentions: List<IntentionalMention>): List<String> = intentionalMentions.map {
            when (it) {
                IntentionalMention.Room -> ROOM_MENTION
                is IntentionalMention.User -> it.userId.value
            }
        }

        fun encode(messages: List<NyutagScheduledMessage>): String = json.encodeToString(messages)

        /** Битые данные очередь не роняют — просто пусто. */
        fun decode(raw: String?): List<NyutagScheduledMessage> {
            if (raw.isNullOrEmpty()) return emptyList()
            return try {
                json.decodeFromString<List<NyutagScheduledMessage>>(raw)
            } catch (_: SerializationException) {
                emptyList()
            } catch (_: IllegalArgumentException) {
                emptyList()
            }
        }
    }
}

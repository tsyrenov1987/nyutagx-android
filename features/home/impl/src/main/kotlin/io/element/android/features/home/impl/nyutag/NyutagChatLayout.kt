/*
 * Нютаг — закреплённые чаты (не более 5) и папки чатов, как в Telegram.
 * Хранится в account data `chat.nyutag.chat_layout`, общий формат для Android и iOS:
 * {"pins":["!room:server", …], "folders":[{"id":"…","name":"…","rooms":["!room:server", …]}]}
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.nyutag

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val NYUTAG_CHAT_LAYOUT_TYPE = "chat.nyutag.chat_layout"
const val NYUTAG_MAX_PINNED_CHATS = 5

@Immutable
@Serializable
data class NyutagChatLayout(
    val pins: List<String> = emptyList(),
    val folders: List<NyutagChatFolder> = emptyList(),
) {
    fun isPinned(roomId: String) = roomId in pins

    val canPinMore: Boolean get() = pins.size < NYUTAG_MAX_PINNED_CHATS

    /** Новый закреп встаёт наверх, как в Telegram. Сверх лимита — без изменений. */
    fun withPinned(roomId: String, pinned: Boolean): NyutagChatLayout = when {
        !pinned -> copy(pins = pins - roomId)
        isPinned(roomId) || !canPinMore -> this
        else -> copy(pins = listOf(roomId) + pins)
    }

    fun withNewFolder(id: String, name: String, roomId: String?): NyutagChatLayout {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return this
        return copy(folders = folders + NyutagChatFolder(id = id, name = cleanName, rooms = listOfNotNull(roomId)))
    }

    fun withRenamedFolder(id: String, name: String): NyutagChatLayout {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return this
        return copy(folders = folders.map { if (it.id == id) it.copy(name = cleanName) else it })
    }

    fun withoutFolder(id: String) = copy(folders = folders.filterNot { it.id == id })

    fun withRoomInFolder(folderId: String, roomId: String, inFolder: Boolean) = copy(
        folders = folders.map { folder ->
            when {
                folder.id != folderId -> folder
                inFolder && roomId !in folder.rooms -> folder.copy(rooms = folder.rooms + roomId)
                !inFolder -> folder.copy(rooms = folder.rooms - roomId)
                else -> folder
            }
        }
    )

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        /** Битые или чужие данные не роняют список чатов — просто пустая раскладка. */
        fun fromJson(raw: String?): NyutagChatLayout {
            if (raw.isNullOrBlank()) return NyutagChatLayout()
            val parsed = runCatching { json.decodeFromString(serializer(), raw) }.getOrNull() ?: return NyutagChatLayout()
            return parsed.copy(
                pins = parsed.pins.distinct().take(NYUTAG_MAX_PINNED_CHATS),
                folders = parsed.folders.filter { it.id.isNotBlank() && it.name.isNotBlank() }.distinctBy { it.id },
            )
        }
    }
}

@Immutable
@Serializable
data class NyutagChatFolder(
    val id: String,
    val name: String,
    val rooms: List<String> = emptyList(),
)

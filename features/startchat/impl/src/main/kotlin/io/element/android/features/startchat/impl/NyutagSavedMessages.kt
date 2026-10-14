/*
 * Нютаг (форк Element X Android) — «Избранное», чат с самим собой, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.startchat.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.createroom.CreateRoomParameters
import io.element.android.libraries.matrix.api.createroom.RoomPreset
import io.element.android.libraries.matrix.api.roomdirectory.RoomVisibility

/** Комната «Избранного»: одна на аккаунт, id в account data — видна со всех устройств, в том числе с iOS. */
fun interface NyutagSavedMessages {
    suspend fun roomId(name: String, topic: String): Result<RoomId>
}

const val NYUTAG_SAVED_MESSAGES_TYPE = "chat.nyutag.saved_messages"

private const val AVATAR_RESOURCE = "/nyutag/saved_avatar.png"

private val ROOM_ID_REGEX = Regex("\"room_id\"\\s*:\\s*\"([^\"]+)\"")

@ContributesBinding(SessionScope::class)
class DefaultNyutagSavedMessages(
    private val matrixClient: MatrixClient,
    private val sessionEnterpriseService: SessionEnterpriseService,
) : NyutagSavedMessages {
    override suspend fun roomId(name: String, topic: String): Result<RoomId> {
        val stored = matrixClient.getAccountData(NYUTAG_SAVED_MESSAGES_TYPE).getOrNull()
            ?.let { ROOM_ID_REGEX.find(it)?.groupValues?.get(1) }
            ?.let { runCatching { RoomId(it) }.getOrNull() }
        if (stored != null && matrixClient.getJoinedRoomIds().getOrNull()?.contains(stored) == true) {
            return Result.success(stored)
        }
        // Аватар-закладка ставится при создании — так комната узнаётся и на iOS, и в списке чатов.
        val avatar = javaClass.getResourceAsStream(AVATAR_RESOURCE)?.use { it.readBytes() }
            ?.let { matrixClient.uploadMedia(MimeTypes.Png, it).getOrNull() }
        return matrixClient.createRoom(
            CreateRoomParameters(
                name = name,
                avatar = avatar,
                topic = topic,
                isEncrypted = !sessionEnterpriseService.isEncryptionDisabledByHomeserver(),
                visibility = RoomVisibility.Private,
                preset = RoomPreset.PRIVATE_CHAT,
                invite = emptyList(),
            )
        ).onSuccess { roomId ->
            matrixClient.setAccountData(NYUTAG_SAVED_MESSAGES_TYPE, "{\"room_id\":\"${roomId.value}\"}")
        }
    }
}

/*
 * Нютаг — имя «Избранного» на языке приложения. Имя комнаты задаётся один раз при создании,
 * и у тех, кто создал её на другом языке, в списке чатов так и оставалось «Saved messages».
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.appnav.loggedin

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.nyutag.nyutagSavedMessagesRoomId
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

/** Стандартные имена «Избранного» на языках Нютага (`nyutag_saved_messages`): своё имя пользователя не трогаем. */
private val DEFAULT_NAMES = setOf("Saved messages", "Избранное", "Хадагалһан")

@SingleIn(SessionScope::class)
@Inject
class NyutagSavedMessagesName(
    private val matrixClient: MatrixClient,
    private val stringProvider: StringProvider,
) {
    /** Комнаты со стандартным именем, которые уже проверили. */
    private val attempts = mutableSetOf<RoomId>()

    fun launchIn(coroutineScope: CoroutineScope) {
        val localizedName = stringProvider.getString(CommonStrings.nyutag_saved_messages)
        matrixClient.roomListService.allRooms.summaries
            .map { summaries ->
                summaries
                    .filter { it.info.rawName in DEFAULT_NAMES && it.info.rawName != localizedName }
                    .map { it.roomId }
                    .toSet()
            }
            .distinctUntilChanged()
            .onEach { candidates ->
                if (attempts.containsAll(candidates)) return@onEach
                val savedRoomId = matrixClient.nyutagSavedMessagesRoomId() ?: return@onEach
                // Одна попытка за сессию; чужие комнаты с таким же именем дальше не проверяем.
                attempts += candidates
                if (savedRoomId !in candidates) return@onEach
                matrixClient.getJoinedRoom(savedRoomId)?.use { room -> room.setName(localizedName) }
                    ?.onFailure { Timber.e(it, "Failed renaming saved messages $savedRoomId") }
            }
            .launchIn(coroutineScope)
    }
}

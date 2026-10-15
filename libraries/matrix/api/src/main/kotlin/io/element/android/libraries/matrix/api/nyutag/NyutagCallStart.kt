/*
 * Нютаг — подготовка исходящего звонка.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId

/**
 * Перед стартом звонка: снимает зависшие участия своих устройств (убитое приложение их не чистит, и следующий
 * звонок без них не звонит) и, если в комнате никто не разговаривает, кладёт незашифрованный сигнал «звоню»,
 * из которого шлюз делает VoIP-пуш собеседнику на iPhone.
 */
fun interface NyutagCallStart {
    suspend fun beforeStart(sessionId: SessionId, roomId: RoomId, isAudioCall: Boolean)
}

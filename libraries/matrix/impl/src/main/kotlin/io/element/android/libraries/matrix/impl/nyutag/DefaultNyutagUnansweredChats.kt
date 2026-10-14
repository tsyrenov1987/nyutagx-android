/*
 * Нютаг — личные чаты, принятые сами, где мы ещё не ответили.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagUnansweredChats

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultNyutagUnansweredChats(
    @ApplicationContext context: Context,
) : NyutagUnansweredChats {
    private val prefs = context.getSharedPreferences("nyutag_unanswered_chats", Context.MODE_PRIVATE)

    @Synchronized
    override fun all(sessionId: SessionId): Set<RoomId> =
        prefs.getStringSet(sessionId.value, emptySet()).orEmpty().map(::RoomId).toSet()

    @Synchronized
    override fun add(sessionId: SessionId, roomId: RoomId) = update(sessionId) { it + roomId.value }

    @Synchronized
    override fun remove(sessionId: SessionId, roomId: RoomId) = update(sessionId) { it - roomId.value }

    private fun update(sessionId: SessionId, block: (Set<String>) -> Set<String>) {
        val updated = block(prefs.getStringSet(sessionId.value, emptySet()).orEmpty())
        val editor = prefs.edit()
        if (updated.isEmpty()) editor.remove(sessionId.value) else editor.putStringSet(sessionId.value, updated)
        editor.apply()
    }
}

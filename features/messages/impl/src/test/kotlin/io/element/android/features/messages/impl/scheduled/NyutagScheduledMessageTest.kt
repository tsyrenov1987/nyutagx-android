/*
 * Нютаг (форк Element X) — формат очереди отложенных сообщений.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.scheduled

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.IntentionalMention
import org.junit.Test

class NyutagScheduledMessageTest {
    @Test
    fun `encode then decode keeps the message and mentions`() {
        val message = NyutagScheduledMessage(
            id = "1",
            sessionId = "@a:nyutag.chat",
            roomId = "!r:nyutag.chat",
            body = "Сайн байна",
            html = "<b>Сайн</b>",
            mentions = NyutagScheduledMessage.mentionsOf(listOf(IntentionalMention.User(UserId("@b:nyutag.chat")), IntentionalMention.Room)),
            sendAtMs = 42,
        )
        val decoded = NyutagScheduledMessage.decode(NyutagScheduledMessage.encode(listOf(message)))
        assertThat(decoded).containsExactly(message)
        assertThat(decoded.single().intentionalMentions())
            .containsExactly(IntentionalMention.User(UserId("@b:nyutag.chat")), IntentionalMention.Room)
    }

    @Test
    fun `broken storage gives an empty queue`() {
        assertThat(NyutagScheduledMessage.decode("{not json")).isEmpty()
        assertThat(NyutagScheduledMessage.decode(null)).isEmpty()
    }
}

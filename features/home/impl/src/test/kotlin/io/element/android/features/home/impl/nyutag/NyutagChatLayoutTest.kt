/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.nyutag

import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.model.createRoomListRoomSummary
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NyutagChatLayoutTest {
    @Test
    fun `pin puts the room on top and stops at the limit`() {
        var layout = NyutagChatLayout()
        (1..NYUTAG_MAX_PINNED_CHATS).forEach { layout = layout.withPinned("!r$it:s", true) }
        assertThat(layout.pins).containsExactly("!r5:s", "!r4:s", "!r3:s", "!r2:s", "!r1:s").inOrder()
        assertThat(layout.canPinMore).isFalse()
        assertThat(layout.withPinned("!r6:s", true)).isEqualTo(layout)
        val unpinned = layout.withPinned("!r3:s", false)
        assertThat(unpinned.pins).containsExactly("!r5:s", "!r4:s", "!r2:s", "!r1:s").inOrder()
        assertThat(unpinned.canPinMore).isTrue()
        assertThat(unpinned.withPinned("!r1:s", true)).isEqualTo(unpinned)
    }

    @Test
    fun `folders can be created, filled, renamed and deleted`() {
        val layout = NyutagChatLayout()
            .withNewFolder("f1", "  Семья ", "!a:s")
            .withNewFolder("f2", "   ", null)
            .withRoomInFolder("f1", "!b:s", true)
            .withRoomInFolder("f1", "!b:s", true)
            .withRoomInFolder("f1", "!a:s", false)
            .withRenamedFolder("f1", "Родня")
            .withRenamedFolder("f1", " ")
        assertThat(layout.folders).containsExactly(NyutagChatFolder(id = "f1", name = "Родня", rooms = listOf("!b:s")))
        assertThat(layout.withoutFolder("f1").folders).isEmpty()
    }

    @Test
    fun `json round trip keeps the shared format`() {
        val layout = NyutagChatLayout(
            pins = listOf("!a:s"),
            folders = listOf(NyutagChatFolder(id = "f1", name = "Работа", rooms = listOf("!b:s"))),
        )
        val json = layout.toJson()
        assertThat(json).isEqualTo("""{"pins":["!a:s"],"folders":[{"id":"f1","name":"Работа","rooms":["!b:s"]}]}""")
        assertThat(NyutagChatLayout.fromJson(json)).isEqualTo(layout)
    }

    @Test
    fun `broken or foreign json gives an empty or cleaned layout`() {
        assertThat(NyutagChatLayout.fromJson(null)).isEqualTo(NyutagChatLayout())
        assertThat(NyutagChatLayout.fromJson("{oops")).isEqualTo(NyutagChatLayout())
        val cleaned = NyutagChatLayout.fromJson(
            """{"pins":["!1:s","!1:s","!2:s","!3:s","!4:s","!5:s","!6:s"],"folders":[{"id":"","name":"x"},{"id":"f","name":"A"},{"id":"f","name":"B"}],"extra":1}"""
        )
        assertThat(cleaned.pins).containsExactly("!1:s", "!2:s", "!3:s", "!4:s", "!5:s").inOrder()
        assertThat(cleaned.folders).containsExactly(NyutagChatFolder(id = "f", name = "A"))
    }

    @Test
    fun `merge puts pinned rooms first in pin order and removes them from the list`() {
        val a = createRoomListRoomSummary().copy(roomId = RoomId("!a:s"))
        val b = createRoomListRoomSummary().copy(roomId = RoomId("!b:s"))
        val c = createRoomListRoomSummary().copy(roomId = RoomId("!c:s"))
        val pinned = listOf(b.copy(isPinned = true), c.copy(isPinned = true))
        val merged = mergePinnedRooms(pins = listOf("!c:s", "!b:s", "!gone:s"), pinned = pinned, rooms = listOf(a, b, c))
        assertThat(merged.map { it.roomId.value }).containsExactly("!c:s", "!b:s", "!a:s").inOrder()
        assertThat(merged.map { it.isPinned }).containsExactly(true, true, false).inOrder()
        assertThat(mergePinnedRooms(emptyList(), pinned, listOf(a))).containsExactly(a)
    }

    @Test
    fun `store saves to account data and rolls back on failure`() = runTest {
        val saved = mutableListOf<Pair<String, String>>()
        var fail = false
        val client = FakeMatrixClient(
            getAccountDataLambda = { Result.success("""{"pins":["!a:s"]}""") },
            setAccountDataLambda = { type, content ->
                if (fail) {
                    Result.failure(IllegalStateException("offline"))
                } else {
                    saved += type to content
                    Result.success(Unit)
                }
            },
        )
        val store = DefaultNyutagChatLayoutStore(client)
        store.refresh()
        assertThat(store.layout.value.pins).containsExactly("!a:s")
        assertThat(store.update { it.withPinned("!b:s", true) }.isSuccess).isTrue()
        assertThat(saved).containsExactly(NYUTAG_CHAT_LAYOUT_TYPE to """{"pins":["!b:s","!a:s"]}""")
        fail = true
        assertThat(store.update { it.withPinned("!a:s", false) }.isFailure).isTrue()
        assertThat(store.layout.value.pins).containsExactly("!b:s", "!a:s").inOrder()
    }
}

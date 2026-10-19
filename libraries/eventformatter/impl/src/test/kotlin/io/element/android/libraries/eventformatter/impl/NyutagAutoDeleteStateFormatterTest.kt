/*
 * Нютаг (форк Element X) — текст смены таймера автоудаления в ленте.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.eventformatter.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.eventformatter.impl.mode.RenderingMode
import io.element.android.libraries.matrix.api.nyutag.NyutagAutoDeleteRule
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.services.toolbox.impl.strings.AndroidStringProvider
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class NyutagAutoDeleteStateFormatterTest : RobolectricTest() {
    private val formatter = StateContentFormatter(AndroidStringProvider(RuntimeEnvironment.getApplication()))

    @Test
    fun `auto-delete timer change is shown as text, not as raw event type`() {
        assertThat(format(NyutagAutoDeleteRule.EVENT_TYPE, senderIsYou = false, RenderingMode.Timeline))
            .isEqualTo("Арюна changed the auto-delete timer")
        assertThat(format(NyutagAutoDeleteRule.EVENT_TYPE, senderIsYou = true, RenderingMode.Timeline))
            .isEqualTo("You changed the auto-delete timer")
        assertThat(format(NyutagAutoDeleteRule.EVENT_TYPE, senderIsYou = false, RenderingMode.RoomList)).isNull()
        assertThat(format("other.type", senderIsYou = false, RenderingMode.Timeline)).isEqualTo("Custom event other.type")
    }

    private fun format(eventType: String, senderIsYou: Boolean, mode: RenderingMode) =
        formatter.format(StateContent("", OtherState.Custom(eventType)), "Арюна", senderIsYou, mode)?.toString()
}

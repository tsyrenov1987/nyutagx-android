/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.ui

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.impl.ui.formatCallDuration
import org.junit.Test

class NyutagCallDurationTest {
    @Test
    fun `duration is formatted like a phone call timer`() {
        assertThat(formatCallDuration(0)).isEqualTo("0:00")
        assertThat(formatCallDuration(5)).isEqualTo("0:05")
        assertThat(formatCallDuration(754)).isEqualTo("12:34")
        assertThat(formatCallDuration(3_723)).isEqualTo("1:02:03")
        assertThat(formatCallDuration(36_000)).isEqualTo("10:00:00")
    }
}

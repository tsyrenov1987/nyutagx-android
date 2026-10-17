/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NyutagNetworkKeyTest {
    @Test
    fun `wifi without gateways is plain wifi, cell and wired are single networks`() {
        assertThat(NyutagNetworkKey.of(wifi = true, wired = false, gateways = emptyList())).isEqualTo("wifi")
        assertThat(NyutagNetworkKey.of(wifi = false, wired = false, gateways = listOf("10.0.0.1"))).isEqualTo("cell")
        assertThat(NyutagNetworkKey.of(wifi = false, wired = true, gateways = listOf("10.0.0.1"))).isEqualTo("wired")
    }

    @Test
    fun `wifi with gateways is hashed, order does not matter, different gateways differ`() {
        val home = NyutagNetworkKey.of(wifi = true, wired = false, gateways = listOf("192.168.1.1", "fe80::1"))
        val homeAgain = NyutagNetworkKey.of(wifi = true, wired = false, gateways = listOf("fe80::1", "192.168.1.1"))
        val office = NyutagNetworkKey.of(wifi = true, wired = false, gateways = listOf("10.10.0.1"))
        assertThat(home).matches("wifi:[0-9a-f]{8}")
        assertThat(homeAgain).isEqualTo(home)
        assertThat(office).isNotEqualTo(home)
        assertThat(home).doesNotContain("192.168")
    }

    @Test
    fun `same network is not reported again, bare wifi after hashed wifi is the same network`() {
        assertThat(NyutagNetworkKey.change("cell", "cell")).isNull()
        assertThat(NyutagNetworkKey.change("wifi:0a0b0c0d", "wifi")).isNull()
        assertThat(NyutagNetworkKey.change("wifi", "wifi:0a0b0c0d")).isEqualTo("wifi:0a0b0c0d")
        assertThat(NyutagNetworkKey.change("wifi:0a0b0c0d", "wifi:11223344")).isEqualTo("wifi:11223344")
        assertThat(NyutagNetworkKey.change("wifi:0a0b0c0d", "cell")).isEqualTo("cell")
    }
}

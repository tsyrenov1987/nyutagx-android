/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NyutagCatalogueTest {
    private val a = "kilvater://k@1.1.1.1:8080?sni=x&kid=1#a"
    private val b = "kilvater://k@2.2.2.2:443?sni=y&kid=1#b"
    private val c = "kilvater://k@3.3.3.3:8080?sni=z&kid=1#c"

    @Test
    fun `without a saved copy the bundled catalogue is used`() {
        assertThat(NyutagCatalogue.compose("$a\n$b\n", null)).isEqualTo("$a\n$b")
    }

    @Test
    fun `server paths come first, bundled ones stay as spare unless retired`() {
        val fetched = NyutagCatalogue(2, listOf(c, b), setOf("1.1.1.1:8080"))
        assertThat(NyutagCatalogue.compose("$a\n$b", fetched)).isEqualTo("$c\n$b")
        val keepA = NyutagCatalogue(2, listOf(c), emptySet())
        assertThat(NyutagCatalogue.compose("$a\n$b", keepA)).isEqualTo("$c\n$a\n$b")
    }

    @Test
    fun `parse keeps only kilvater lines and survives junk`() {
        val json = """{"version":7,"paths":["$a"," ","http://x"],"retired":["9.9.9.9:443"]}"""
        val cat = NyutagCatalogue.parse(json)!!
        assertThat(cat.paths).containsExactly(a)
        assertThat(cat.retired).containsExactly("9.9.9.9:443")
        assertThat(NyutagCatalogue.parse(cat.toJson())).isEqualTo(cat)
        assertThat(NyutagCatalogue.parse("""{"paths":[]}""")).isNull()
        assertThat(NyutagCatalogue.parse("not json")).isNull()
        assertThat(NyutagCatalogue.parse(null)).isNull()
    }

    @Test
    fun `host and port are read from the line`() {
        assertThat(NyutagCatalogue.hostPort(b)).isEqualTo("2.2.2.2:443")
    }
}

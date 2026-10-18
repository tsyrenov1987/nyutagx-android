/*
 * Нютаг — строки вне экранов на языке, выбранном в приложении.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.services.toolbox.impl.strings

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en", sdk = [Build.VERSION_CODES.R])
class NyutagAppLocaleResourcesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteFile(APP_LOCALES_FILE)
    }

    @Test
    fun `without a language chosen in the app, strings follow the system`() {
        assertThat(AndroidStringProvider(context).getString(android.R.string.cancel)).isEqualTo("Cancel")
    }

    @Test
    fun `before Android 13, strings follow the language chosen in the app`() {
        chooseAppLanguage("ru")
        assertThat(AndroidStringProvider(context).getString(android.R.string.cancel)).isEqualTo("Отмена")
    }

    @Test
    fun `a new choice is picked up after the refresh interval`() {
        var now = 10_000L
        val resources = NyutagAppLocaleResources(context, elapsedRealtime = { now })
        chooseAppLanguage("ru")
        assertThat(resources.get().getString(android.R.string.cancel)).isEqualTo("Отмена")
        chooseAppLanguage("fr")
        now += 1_000L
        assertThat(resources.get().getString(android.R.string.cancel)).isEqualTo("Отмена")
        now += 1_500L
        assertThat(resources.get().getString(android.R.string.cancel)).isEqualTo("Annuler")
    }

    @Test
    fun `from Android 13 the system applies the app language itself`() {
        chooseAppLanguage("ru")
        val resources = NyutagAppLocaleResources(context, sdkInt = Build.VERSION_CODES.TIRAMISU)
        assertThat(resources.get()).isSameInstanceAs(context.resources)
    }

    /** Как AppCompat (`autoStoreLocales`) сохраняет выбор языка до Android 13. */
    private fun chooseAppLanguage(tags: String) {
        context.openFileOutput(APP_LOCALES_FILE, Context.MODE_PRIVATE).use {
            it.write("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><locales application_locales=\"$tags\" />".toByteArray())
        }
    }

    private companion object {
        const val APP_LOCALES_FILE = "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file"
    }
}

/*
 * Nyutag: in-app language switcher option.
 * Buryat is paired with Russian as the fallback locale so untranslated strings show in Russian, not English.
 */

package io.element.android.features.preferences.impl.root

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.preferences.DropdownOption

enum class NyutagLanguageOption(val tags: String) : DropdownOption {
    System("") {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.nyutag_language_system)
    },
    Buryat("bxr,ru") {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = "Буряад хэлэн"
    },
    Russian("ru") {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = "Русский"
    },
    English("en") {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = "English"
    };

    companion object {
        fun current(): NyutagLanguageOption {
            val first = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag().orEmpty()
            return entries.firstOrNull { it.tags.isNotEmpty() && first.startsWith(it.tags.substringBefore(',')) } ?: System
        }

        fun select(option: NyutagLanguageOption) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tags))
        }
    }
}

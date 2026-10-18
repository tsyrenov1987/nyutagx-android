/*
 * Нютаг — строки вне экранов на языке, выбранном в приложении.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.services.toolbox.impl.strings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.core.app.LocaleManagerCompat

/**
 * Язык, выбранный в Нютаге (AppCompat), до Android 13 применяется только к экранам: ресурсы приложения остаются на языке
 * системы, и строки вне экранов (превью в списке чатов, имя «Избранного», уведомления) шли по-русски при бурятском интерфейсе.
 * С Android 13 язык приложения применяет система, и ресурсы приложения уже на нём.
 */
internal class NyutagAppLocaleResources(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private data class Snapshot(val tags: String, val resources: Resources, val readAtMillis: Long)

    @Volatile
    private var snapshot: Snapshot? = null

    fun get(): Resources {
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) return context.resources
        val now = elapsedRealtime()
        val current = snapshot
        if (current != null && now - current.readAtMillis < REFRESH_MILLIS) return current.resources
        // Выбор лежит в файле AppCompat (его же читает LocaleManagerCompat) — перечитываем не чаще раза в REFRESH_MILLIS.
        val tags = LocaleManagerCompat.getApplicationLocales(context).toLanguageTags()
        val resources = current?.takeIf { it.tags == tags }?.resources ?: resourcesFor(tags)
        snapshot = Snapshot(tags, resources, now)
        return resources
    }

    private fun resourcesFor(tags: String): Resources {
        if (tags.isEmpty()) return context.resources
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(tags))
        return context.createConfigurationContext(configuration).resources
    }

    private companion object {
        const val REFRESH_MILLIS = 2_000L
    }
}

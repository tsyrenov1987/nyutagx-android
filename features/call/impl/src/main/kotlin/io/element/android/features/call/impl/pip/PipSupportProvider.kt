/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.pip

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.annotation.ChecksSdkIntAtLeast
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.bool.orFalse
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext

interface PipSupportProvider {
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.O)
    fun isPipSupported(): Boolean
}

@ContributesBinding(AppScope::class)
class DefaultPipSupportProvider(
    @ApplicationContext private val context: Context,
) : PipSupportProvider {
    override fun isPipSupported(): Boolean {
        val isSupportedByTheOs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE).orFalse()
        return isSupportedByTheOs && isAllowedByTheUser()
    }

    /**
     * Нютаг: на Xiaomi и HONOR окно «картинка в картинке» часто запрещено в настройках — тогда система молча не сворачивает
     * звонок в окно, и кнопок не видно. В этом случае звонок сворачивается в приложение под полосу сверху.
     */
    private fun isAllowedByTheUser(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return true
        val mode = runCatchingExceptions {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), context.packageName)
            }
        }.getOrDefault(AppOpsManager.MODE_ALLOWED)
        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
    }
}

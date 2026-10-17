/*
 * Нютаг — входящий звонок поверх других приложений, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.impl.utils

import android.content.Context
import android.content.Intent
import io.element.android.features.call.impl.notifications.CallNotificationData
import io.element.android.features.call.impl.ui.IncomingCallActivity
import io.element.android.libraries.core.extensions.runCatchingExceptions
import timber.log.Timber

object NyutagIncomingCallLauncher {
    /**
     * Открывает [IncomingCallActivity] прямо из фона. Android 10+ пропускает такой запуск только с разрешением
     * «поверх других приложений» (на MIUI — «всплывающие окна в фоне»), иначе молча отбрасывает его: тогда экран
     * покажет full-screen intent уведомления, когда телефон заперт, а на разблокированном останется шторка.
     * Разрешение просим баннером «Улучшите качество звонков» (FullScreenIntentPermissionsPresenter).
     */
    fun showOverOtherApps(context: Context, notificationData: CallNotificationData) {
        val intent = Intent(context, IncomingCallActivity::class.java)
            .putExtra(IncomingCallActivity.EXTRA_NOTIFICATION_DATA, notificationData)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatchingExceptions { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Nyutag: could not open the incoming call screen directly") }
    }
}

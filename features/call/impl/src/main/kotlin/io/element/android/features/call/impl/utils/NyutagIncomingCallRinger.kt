/*
 * Нютаг — звонок входящего, пока на виду экран входящего.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.impl.utils

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.element.android.libraries.core.extensions.runCatchingExceptions
import timber.log.Timber

/**
 * Нютаг: пока открыт экран входящего, уведомление звонка перевыпускается тихим — иначе система держит его шторку поверх
 * экрана. Звонит тогда этот класс: мелодия и вибрация канала рингтона, с учётом режима звука телефона.
 */
internal class NyutagIncomingCallRinger(private val context: Context) {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    /** При «Не беспокоить» решает система (кому можно звонить) — тогда уведомление не подменяем. */
    fun canReplaceNotificationSound(): Boolean =
        context.getSystemService<NotificationManager>()?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL

    fun start(notificationChannelId: String) {
        stop()
        val ringerMode = context.getSystemService<AudioManager>()?.ringerMode ?: return
        val channel = NotificationManagerCompat.from(context).getNotificationChannelCompat(notificationChannelId)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // У канала без звука (пользователь выключил его в настройках) мелодии нет.
        val soundUri = if (channel != null) channel.sound else Settings.System.DEFAULT_RINGTONE_URI
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL && soundUri != null) {
            runCatchingExceptions {
                ringtone = RingtoneManager.getRingtone(context, soundUri)?.apply {
                    audioAttributes = attributes
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                    play()
                }
            }.onFailure { Timber.e(it, "Failed to play the incoming call ringtone") }
        }
        if (ringerMode != AudioManager.RINGER_MODE_SILENT && channel?.shouldVibrate() != false) {
            vibrator = context.getSystemService<Vibrator>()?.apply {
                vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1_000, 1_000), 0), attributes)
            }
        }
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }
}

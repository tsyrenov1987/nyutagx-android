/*
 * Нютаг (форк Element X) — отправка отложенного сообщения в назначенное время.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.scheduled

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.workmanager.api.di.MetroWorkerFactory
import io.element.android.libraries.workmanager.api.di.WorkerKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class NyutagScheduledSendWorker(
    @Assisted private val params: WorkerParameters,
    @ApplicationContext context: Context,
    private val matrixClientProvider: MatrixClientProvider,
    private val scheduledMessages: NyutagScheduledMessages,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = params.inputData.getString(KEY_ID) ?: return Result.failure()
        // Удалили из очереди раньше времени — отправлять нечего.
        val message = scheduledMessages.get(id) ?: return Result.success()
        val client = matrixClientProvider.getOrRestore(SessionId(message.sessionId)).getOrElse {
            Timber.w(it, "Nyutag scheduled: session not restored")
            return retryOrGiveUp(id)
        }
        val room = client.getJoinedRoom(RoomId(message.roomId)) ?: run {
            Timber.w("Nyutag scheduled: room ${message.roomId} is not joined, dropping")
            scheduledMessages.delete(id)
            return Result.failure()
        }
        try {
            room.liveTimeline.sendMessage(
                body = message.body,
                htmlBody = message.html,
                intentionalMentions = message.intentionalMentions(),
            ).getOrElse {
                Timber.w(it, "Nyutag scheduled: send failed")
                return retryOrGiveUp(id)
            }
            // Сообщение уже в очереди SDK (она переживает перезапуск) — из своей убираем сразу, чтобы повтор воркера не задвоил.
            scheduledMessages.delete(id)
            // Даём SDK дослать, пока процесс жив: ждём появления локального эха и его отправки.
            withTimeoutOrNull(SEND_GRACE) {
                val timelineItems = room.liveTimeline.timelineItems
                withTimeoutOrNull(ECHO_WAIT) { timelineItems.first { it.hasSending() } }
                timelineItems.first { !it.hasSending() }
            }
        } finally {
            room.destroy()
        }
        return Result.success()
    }

    private suspend fun retryOrGiveUp(id: String): Result = if (runAttemptCount < MAX_ATTEMPTS) {
        Result.retry()
    } else {
        // Сообщение остаётся в очереди: пользователь увидит его в чате и отправит сам.
        Timber.e("Nyutag scheduled: giving up on $id after $runAttemptCount attempts")
        Result.failure()
    }

    private fun List<MatrixTimelineItem>.hasSending() =
        any { (it as? MatrixTimelineItem.Event)?.event?.localSendState is LocalEventSendState.Sending }

    @ContributesIntoMap(AppScope::class, binding = binding<MetroWorkerFactory.WorkerInstanceFactory<*>>())
    @WorkerKey(NyutagScheduledSendWorker::class)
    @AssistedFactory
    interface Factory : MetroWorkerFactory.WorkerInstanceFactory<NyutagScheduledSendWorker>

    companion object {
        const val KEY_ID = "nyutag_scheduled_id"
        private const val MAX_ATTEMPTS = 8
        private val SEND_GRACE = 30.seconds
        private val ECHO_WAIT = 5.seconds
    }
}

/*
 * Нютаг (форк Element X) — очередь отложенных сообщений в SharedPreferences + WorkManager.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.scheduled

import android.content.Context
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultNyutagScheduledMessages(
    @ApplicationContext private val context: Context,
    lazyWorkManager: Lazy<WorkManager>,
) : NyutagScheduledMessages {
    private val workManager by lazyWorkManager
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val mutex = Mutex()
    private val items = MutableStateFlow<List<NyutagScheduledMessage>?>(null)

    override fun observe(sessionId: SessionId, roomId: RoomId): Flow<List<NyutagScheduledMessage>> = items
        .onStart { load() }
        .map { list -> list.orEmpty().filter { it.isIn(sessionId, roomId) }.sortedBy { it.sendAtMs } }

    override suspend fun get(id: String): NyutagScheduledMessage? = load().firstOrNull { it.id == id }

    override suspend fun schedule(message: NyutagScheduledMessage) {
        update { it.filterNot { item -> item.id == message.id } + message }
        enqueue(message.id, delayMs = message.sendAtMs - System.currentTimeMillis())
    }

    override suspend fun sendNow(id: String) {
        if (get(id) != null) enqueue(id, delayMs = 0)
    }

    override suspend fun delete(id: String) {
        workManager.cancelUniqueWork(workName(id))
        update { it.filterNot { item -> item.id == id } }
    }

    private suspend fun load(): List<NyutagScheduledMessage> = items.value ?: mutex.withLock {
        items.value ?: withContext(Dispatchers.IO) {
            NyutagScheduledMessage.decode(prefs.getString(KEY_ITEMS, null))
        }.also { items.value = it }
    }

    private suspend fun update(transform: (List<NyutagScheduledMessage>) -> List<NyutagScheduledMessage>) {
        load()
        mutex.withLock {
            val updated = transform(items.value.orEmpty())
            withContext(Dispatchers.IO) {
                prefs.edit(commit = true) { putString(KEY_ITEMS, NyutagScheduledMessage.encode(updated)) }
            }
            items.value = updated
        }
    }

    private fun enqueue(id: String, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<NyutagScheduledSendWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(NyutagScheduledSendWorker.KEY_ID to id))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(id: String) = "$TAG-$id"

    private companion object {
        const val PREFS = "nyutag_scheduled_messages"
        const val KEY_ITEMS = "items"
        const val TAG = "nyutag_scheduled"
    }
}

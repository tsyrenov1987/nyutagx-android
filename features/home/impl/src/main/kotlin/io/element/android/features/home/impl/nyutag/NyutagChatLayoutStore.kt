/*
 * Нютаг — хранилище закрепов и папок поверх account data.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.nyutag

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

interface NyutagChatLayoutStore {
    val layout: StateFlow<NyutagChatLayout>

    /** Перечитать с сервера: правки с другого устройства подтягиваются при открытии списка. */
    suspend fun refresh()

    /** Меняет раскладку сразу на экране и сохраняет; при ошибке сохранения откатывает. */
    suspend fun update(transform: (NyutagChatLayout) -> NyutagChatLayout): Result<Unit>
}

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@Inject
class DefaultNyutagChatLayoutStore(
    private val client: MatrixClient,
) : NyutagChatLayoutStore {
    private val mutex = Mutex()
    private val state = MutableStateFlow(NyutagChatLayout())
    override val layout: StateFlow<NyutagChatLayout> = state

    override suspend fun refresh() {
        mutex.withLock {
            client.getAccountData(NYUTAG_CHAT_LAYOUT_TYPE)
                .onSuccess { state.value = NyutagChatLayout.fromJson(it) }
                .onFailure { Timber.w(it, "Nyutag: failed to read chat layout") }
        }
    }

    override suspend fun update(transform: (NyutagChatLayout) -> NyutagChatLayout): Result<Unit> = mutex.withLock {
        val old = state.value
        val new = transform(old)
        if (new == old) return@withLock Result.success(Unit)
        state.value = new
        client.setAccountData(NYUTAG_CHAT_LAYOUT_TYPE, new.toJson())
            .onFailure {
                Timber.w(it, "Nyutag: failed to save chat layout")
                state.value = old
            }
    }
}

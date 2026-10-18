/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.nyutag

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemoryNyutagChatLayoutStore(
    initial: NyutagChatLayout = NyutagChatLayout(),
) : NyutagChatLayoutStore {
    private val state = MutableStateFlow(initial)
    override val layout: StateFlow<NyutagChatLayout> = state
    var refreshCount = 0
        private set

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun update(transform: (NyutagChatLayout) -> NyutagChatLayout): Result<Unit> {
        state.value = transform(state.value)
        return Result.success(Unit)
    }
}

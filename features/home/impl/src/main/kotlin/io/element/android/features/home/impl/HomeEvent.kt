/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagAnnouncement

sealed interface HomeEvent {
    data class SelectHomeNavigationBarItem(val item: HomeNavigationBarItem) : HomeEvent
    data class SwitchToAccount(val sessionId: SessionId) : HomeEvent
    /** Нютаг: кнопка в окне обращения разработчика (ссылка или донат). */
    data class NyutagAnnouncementAction(val action: NyutagAnnouncement.Action) : HomeEvent
    /** Нютаг: окно обращения закрыто без действия («позже»). */
    data object NyutagAnnouncementDismiss : HomeEvent
    data object NyutagDonateDismiss : HomeEvent
}

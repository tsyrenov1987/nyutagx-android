/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui
internal sealed interface CallScreenBackPressAction {
    data object DispatchEscapeToWebView : CallScreenBackPressAction
    data object EnterPictureInPicture : CallScreenBackPressAction

    /** Нютаг: свернуть звонок в приложение — дальше им управляет полоса сверху. */
    data object MinimizeToApp : CallScreenBackPressAction
}

internal object CallScreenBackPressPolicy {
    fun resolve(
        supportPip: Boolean,
        hasWebView: Boolean,
        fromNative: Boolean,
        isAudioCall: Boolean = false,
    ): CallScreenBackPressAction? {
        return when {
            hasWebView && fromNative -> CallScreenBackPressAction.DispatchEscapeToWebView
            // Нютаг: голосовому звонку окно поверх не нужно, как в Telegram; без PiP «Назад» раньше ничего не делал.
            hasWebView && (isAudioCall || !supportPip) -> CallScreenBackPressAction.MinimizeToApp
            hasWebView && supportPip -> CallScreenBackPressAction.EnterPictureInPicture
            else -> null
        }
    }
}

/*
 * Нютаг (форк Element X) — исчезающее фото в ленте.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.timeline.model.event

enum class NyutagViewOnceState {
    None,

    /** Своё исчезающее фото: отправитель видит только заглушку, как в Telegram. */
    Sent,

    /** Чужое: открывается один раз, затем удаляется. */
    Received,
}

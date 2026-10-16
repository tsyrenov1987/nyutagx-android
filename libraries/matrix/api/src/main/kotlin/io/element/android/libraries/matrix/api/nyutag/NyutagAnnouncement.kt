/*
 * Нютаг (форк Element X) — обращения разработчика ко всем пользователям.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

/**
 * Одно обращение из `announce.json` на ядре (`/_nyutag/v1/announce`): тексты уже на языке приложения.
 * Клиент показывает окно один раз на [id]; после «позже» — снова через [repeatDays] дней (0 — больше не показывать).
 */
data class NyutagAnnouncement(
    val id: String,
    val title: String,
    val body: String,
    val actions: List<Action>,
    val dismissLabel: String?,
    val repeatDays: Int,
) {
    data class Action(
        val kind: Kind,
        val label: String,
        val url: String?,
    )

    enum class Kind { Url, Donate }
}

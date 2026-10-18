/*
 * Нютаг (форк Element X) — автоудаление в ленте чата: истёкшие сообщения прячутся у всех, свои отправитель удаляет.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.timeline

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.nyutag.NyutagAutoDeleteRule
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.api.timeline.item.event.LiveLocationContent
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.PollContent
import io.element.android.libraries.matrix.api.timeline.item.event.RedactedContent
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.libraries.matrix.api.timeline.item.event.StickerContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent

object NyutagAutoDeleteTimeline {
    /** Как в Telegram: удаляются сообщения, служебные события (вход, переименование) остаются. */
    fun isMessage(content: EventContent): Boolean = when (content) {
        is MessageContent,
        is StickerContent,
        is PollContent,
        is RedactedContent,
        is UnableToDecryptContent,
        is LiveLocationContent -> true
        else -> false
    }

    fun visibleItems(items: List<MatrixTimelineItem>, rule: NyutagAutoDeleteRule, nowMs: Long): List<MatrixTimelineItem> {
        if (!rule.isEnabled) return items
        return items.filterNot { it is MatrixTimelineItem.Event && isExpired(it, rule, nowMs) }
    }

    /** Свои отправленные истёкшие сообщения, которые ещё не удалены на сервере. */
    fun ownToRedact(items: List<MatrixTimelineItem>, rule: NyutagAutoDeleteRule, nowMs: Long): List<EventId> {
        if (!rule.isEnabled) return emptyList()
        return items.mapNotNull { item ->
            val event = (item as? MatrixTimelineItem.Event)?.event ?: return@mapNotNull null
            val eventId = event.eventId ?: return@mapNotNull null
            eventId.takeIf { event.isOwn && event.content !is RedactedContent && isExpired(item, rule, nowMs) }
        }
    }

    /** Ключ последнего изменения таймера в ленте: сменился — правило перечитывается с сервера. */
    fun ruleChangeKey(items: List<MatrixTimelineItem>): String? = items.lastOrNull { item ->
        val content = (item as? MatrixTimelineItem.Event)?.event?.content as? StateContent
        (content?.content as? OtherState.Custom)?.eventType == NyutagAutoDeleteRule.EVENT_TYPE
    }?.let { (it as MatrixTimelineItem.Event).eventId?.value }

    private fun isExpired(item: MatrixTimelineItem.Event, rule: NyutagAutoDeleteRule, nowMs: Long): Boolean =
        isMessage(item.event.content) && rule.isExpired(item.event.timestamp, nowMs)
}

/*
 * Нютаг (форк Element X) — отложенные сообщения: выбор времени и плашка очереди над полем ввода.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.scheduled

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Дата, затем время; в прошлое не даёт. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NyutagSchedulePicker(
    onDismiss: () -> Unit,
    onPick: (sendAtMs: Long) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var pickedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    val date = pickedDate
    if (date == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = today.toUtcMillis(),
            selectableDates = remember(today) {
                object : SelectableDates {
                    // DatePicker отдаёт полночь UTC выбранного дня.
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today.toUtcMillis()

                    override fun isSelectableYear(year: Int) = year >= today.year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    text = stringResource(CommonStrings.action_next),
                    onClick = { pickedDate = dateState.selectedDateMillis },
                    enabled = dateState.selectedDateMillis != null,
                )
            },
            dismissButton = { TextButton(text = stringResource(CommonStrings.action_cancel), onClick = onDismiss) },
        ) {
            DatePicker(state = dateState, showModeToggle = false)
        }
    } else {
        val soon = remember { LocalTime.now(zone).plusMinutes(10) }
        val timeState = rememberTimePickerState(
            initialHour = soon.hour,
            initialMinute = soon.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        val day = Instant.ofEpochMilli(date).atZone(ZoneOffset.UTC).toLocalDate()
        val sendAtMs = LocalDateTime.of(day, LocalTime.of(timeState.hour, timeState.minute)).atZone(zone).toInstant().toEpochMilli()
        val isInPast = sendAtMs <= System.currentTimeMillis()
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    text = stringResource(CommonStrings.nyutag_schedule_send),
                    onClick = { onPick(sendAtMs) },
                    enabled = !isInPast,
                )
            },
            dismissButton = { TextButton(text = stringResource(CommonStrings.action_cancel), onClick = onDismiss) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                    Text(
                        text = stringResource(if (isInPast) CommonStrings.nyutag_schedule_error_past else CommonStrings.nyutag_schedule_hint),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = if (isInPast) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary,
                    )
                }
            },
        )
    }
}

/** Плашка «Отложенных сообщений: N» над полем ввода; по нажатию — список с «Отправить сейчас» и «Удалить». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NyutagScheduledBanner(
    messages: ImmutableList<NyutagScheduledMessage>,
    formatSendAt: (Long) -> String,
    onSendNow: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) return
    var showList by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary)
            .clickable { showList = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.Time(),
            contentDescription = null,
            tint = ElementTheme.colors.iconAccentPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(CommonStrings.nyutag_schedule_banner, messages.size),
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textPrimary,
        )
    }
    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }, scrollable = true) {
            Text(
                text = stringResource(CommonStrings.nyutag_schedule_title),
                style = ElementTheme.typography.fontHeadingSmMedium,
                color = ElementTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            messages.forEachIndexed { index, message ->
                if (index > 0) HorizontalDivider()
                NyutagScheduledItem(
                    message = message,
                    formatSendAt = formatSendAt,
                    onSendNow = { onSendNow(message.id) },
                    onDelete = {
                        if (messages.size == 1) showList = false
                        onDelete(message.id)
                    },
                )
            }
            Text(
                text = stringResource(CommonStrings.nyutag_schedule_hint),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun NyutagScheduledItem(
    message: NyutagScheduledMessage,
    formatSendAt: (Long) -> String,
    onSendNow: () -> Unit,
    onDelete: () -> Unit,
) {
    val time = remember(message.sendAtMs) { formatSendAt(message.sendAtMs) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = message.body,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(CommonStrings.nyutag_schedule_at, time),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = stringResource(CommonStrings.nyutag_schedule_send_now), onClick = onSendNow)
            TextButton(text = stringResource(CommonStrings.action_delete), onClick = onDelete, destructive = true)
        }
    }
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

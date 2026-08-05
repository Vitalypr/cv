package com.vitalypr.daylog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vitalypr.daylog.R
import com.vitalypr.daylog.domain.model.DayStatus
import com.vitalypr.daylog.ui.theme.Amber
import com.vitalypr.daylog.ui.theme.AmberTint
import com.vitalypr.daylog.ui.theme.InkMuted
import com.vitalypr.daylog.ui.theme.InkSecondary
import com.vitalypr.daylog.ui.theme.Line
import com.vitalypr.daylog.ui.theme.SendGreenDark
import com.vitalypr.daylog.ui.theme.SendGreenTint

/** Section card matching the mockup's 18dp white cards. */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = InkSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            content()
        }
    }
}

@Composable
fun StatusBadge(status: DayStatus, modifier: Modifier = Modifier) {
    val (text, fg, bg) = when (status) {
        DayStatus.EMPTY -> Triple(stringResource(R.string.status_empty), InkMuted, Line.copy(alpha = 0.5f))
        DayStatus.LOGGED -> Triple(stringResource(R.string.status_logged), Amber, AmberTint)
        DayStatus.REPORTED -> Triple(stringResource(R.string.status_reported), SendGreenDark, SendGreenTint)
        DayStatus.REPORTED_EDITED -> Triple(stringResource(R.string.status_reported_edited), SendGreenDark, SendGreenTint)
        DayStatus.OFF -> Triple(stringResource(R.string.day_off), InkSecondary, Line.copy(alpha = 0.5f))
        DayStatus.HOLIDAY -> Triple(stringResource(R.string.holiday), InkSecondary, Line.copy(alpha = 0.5f))
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = modifier
            .background(bg, RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60) % 24,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.confirm_time))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = state) },
    )
}

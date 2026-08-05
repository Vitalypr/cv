package com.vitalypr.daylog.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitalypr.daylog.R
import com.vitalypr.daylog.data.repo.ActivityRow
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.time.formatDate
import com.vitalypr.daylog.domain.time.formatDuration
import com.vitalypr.daylog.domain.time.formatMinutes
import com.vitalypr.daylog.domain.time.hebrewDayName
import com.vitalypr.daylog.reporting.ReportShare
import com.vitalypr.daylog.ui.components.SectionCard
import com.vitalypr.daylog.ui.components.StatusBadge
import com.vitalypr.daylog.ui.components.TimePickerDialog
import com.vitalypr.daylog.ui.theme.InkMuted
import com.vitalypr.daylog.ui.theme.InkSecondary
import com.vitalypr.daylog.ui.theme.SendGreen

@Composable
fun TodayScreen(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is TodayEffect.LaunchShare ->
                    context.startActivity(ReportShare.intentFor(context, effect.reportText))
            }
        }
    }
    TodayContent(
        state = state,
        callbacks = TodayCallbacks(
            onArriveNow = viewModel::arriveNow,
            onLeaveNow = viewModel::leaveNow,
            onSetArrival = viewModel::setArrival,
            onSetDeparture = viewModel::setDeparture,
            onToggleDayType = viewModel::toggleDayType,
            onAddActivity = viewModel::addActivity,
            onUpdateActivity = viewModel::updateActivity,
            onRemoveActivity = viewModel::removeActivity,
            onAddFieldJob = viewModel::addFieldJob,
            onSetNotes = viewModel::setNotes,
            onShare = viewModel::share,
        ),
    )
}

/** All screen callbacks in one bundle so TodayContent stays snapshot-testable. */
data class TodayCallbacks(
    val onArriveNow: () -> Unit = {},
    val onLeaveNow: () -> Unit = {},
    val onSetArrival: (Int) -> Unit = {},
    val onSetDeparture: (Int) -> Unit = {},
    val onToggleDayType: (DayType) -> Unit = {},
    val onAddActivity: (Long) -> Unit = {},
    val onUpdateActivity: (ActivityRow) -> Unit = {},
    val onRemoveActivity: (Long) -> Unit = {},
    val onAddFieldJob: (String, String?, Int?, Int?) -> Unit = { _, _, _, _ -> },
    val onSetNotes: (String) -> Unit = {},
    val onShare: () -> Unit = {},
)

@Composable
fun TodayContent(state: TodayUiState, callbacks: TodayCallbacks) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tab_today), style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${hebrewDayName(state.date)}, ${formatDate(state.date)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary,
                )
            }
            StatusBadge(state.status)
        }

        TimeCard(state, callbacks)
        FieldJobsCard(state, callbacks)
        ActivitiesCard(state, callbacks)
        NotesCard(state, callbacks)
        ReportCard(state, callbacks)
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun TimeCard(state: TodayUiState, cb: TodayCallbacks) {
    var pickArrival by remember { mutableStateOf(false) }
    var pickDeparture by remember { mutableStateOf(false) }

    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeSlot(
                label = stringResource(R.string.arrival),
                minutes = state.day.arrivalMin,
                source = state.day.arrivalSource,
                actionLabel = stringResource(R.string.arrived_now),
                onAction = cb.onArriveNow,
                onEdit = { pickArrival = true },
                onNudge = { delta -> state.day.arrivalMin?.let { cb.onSetArrival((it + delta).coerceAtLeast(0)) } },
                modifier = Modifier.weight(1f),
            )
            TimeSlot(
                label = stringResource(R.string.departure),
                minutes = state.day.departureMin,
                source = state.day.departureSource,
                actionLabel = stringResource(R.string.left_now),
                onAction = cb.onLeaveNow,
                onEdit = { pickDeparture = true },
                onNudge = { delta -> state.day.departureMin?.let { cb.onSetDeparture((it + delta).coerceAtLeast(0)) } },
                modifier = Modifier.weight(1f),
            )
        }
        val arr = state.day.arrivalMin
        val dep = state.day.departureMin
        if (arr != null && dep != null && dep > arr) {
            Text(
                stringResource(R.string.total_at_office, formatDuration(dep - arr)),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.special_day), style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            FilterChip(
                selected = state.day.dayType == DayType.OFF,
                onClick = { cb.onToggleDayType(DayType.OFF) },
                label = { Text(stringResource(R.string.day_off)) },
            )
            FilterChip(
                selected = state.day.dayType == DayType.HOLIDAY,
                onClick = { cb.onToggleDayType(DayType.HOLIDAY) },
                label = { Text(stringResource(R.string.holiday)) },
            )
        }
    }

    if (pickArrival) {
        TimePickerDialog(
            initialMinutes = state.day.arrivalMin ?: (8 * 60),
            onConfirm = { cb.onSetArrival(it); pickArrival = false },
            onDismiss = { pickArrival = false },
        )
    }
    if (pickDeparture) {
        TimePickerDialog(
            initialMinutes = state.day.departureMin ?: (17 * 60),
            onConfirm = { cb.onSetDeparture(it); pickDeparture = false },
            onDismiss = { pickDeparture = false },
        )
    }
}

@Composable
private fun TimeSlot(
    label: String,
    minutes: Int?,
    source: TimeSource,
    actionLabel: String,
    onAction: () -> Unit,
    onEdit: () -> Unit,
    onNudge: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
        TextButton(onClick = onEdit, enabled = minutes != null) {
            Text(
                minutes?.let(::formatMinutes) ?: stringResource(R.string.time_unset),
                style = MaterialTheme.typography.headlineMedium,
                color = if (minutes != null) MaterialTheme.colorScheme.onSurface else InkMuted,
            )
        }
        if (minutes == null) {
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
        } else {
            Text(
                if (source == TimeSource.GEOFENCE) stringResource(R.string.source_geofence) else stringResource(R.string.source_manual),
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onNudge(-5) }) { Text(stringResource(R.string.minus_5)) }
                OutlinedButton(onClick = { onNudge(+5) }) { Text(stringResource(R.string.plus_5)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldJobsCard(state: TodayUiState, cb: TodayCallbacks) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.field_jobs)) {
        state.fieldJobRows.forEach { job ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🚗", Modifier.padding(end = 8.dp))
                Text(job.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                val range = when {
                    job.startMin != null && job.endMin != null -> "${formatMinutes(job.startMin)}–${formatMinutes(job.endMin)}"
                    job.startMin != null -> "${formatMinutes(job.startMin)}–…"
                    else -> ""
                }
                Text(range, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
        }
        OutlinedButton(onClick = { showSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_field_job))
        }
    }
    if (showSheet) {
        FieldJobSheet(
            onSave = { t, l, s, e -> cb.onAddFieldJob(t, l, s, e); showSheet = false },
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldJobSheet(onSave: (String, String?, Int?, Int?) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var start by remember { mutableStateOf<Int?>(null) }
    var end by remember { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.field_job_title)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.field_job_location)) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { picking = "start" }, Modifier.weight(1f)) {
                    Text(start?.let(::formatMinutes) ?: stringResource(R.string.start_time))
                }
                OutlinedButton(onClick = { picking = "end" }, Modifier.weight(1f)) {
                    Text(end?.let(::formatMinutes) ?: stringResource(R.string.end_time))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                Button(
                    onClick = { onSave(title, location.ifBlank { null }, start, end) },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.save)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
    picking?.let { which ->
        TimePickerDialog(
            initialMinutes = if (which == "start") start ?: 600 else end ?: 780,
            onConfirm = { if (which == "start") start = it else end = it; picking = null },
            onDismiss = { picking = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivitiesCard(state: TodayUiState, cb: TodayCallbacks) {
    SectionCard(title = stringResource(R.string.activities)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.categories.forEach { cat ->
                FilterChip(
                    selected = state.activityRows.any { it.categoryId == cat.id },
                    onClick = { cb.onAddActivity(cat.id) },
                    label = { Text(cat.name) },
                )
            }
        }
        state.activityRows.forEach { row ->
            androidx.compose.runtime.key(row.id) { ActivityEditor(row, cb) }
        }
    }
}

@Composable
private fun ActivityEditor(row: ActivityRow, cb: TodayCallbacks) {
    var picking by remember { mutableStateOf<String?>(null) }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.category,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        NoteField(
            value = row.note,
            hint = stringResource(R.string.activity_note_hint),
            onChange = { cb.onUpdateActivity(row.copy(note = it)) },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { cb.onRemoveActivity(row.id) }) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), tint = InkMuted)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.activity_times), style = MaterialTheme.typography.labelSmall, color = InkMuted)
        TextButton(onClick = { picking = "start" }) {
            Text(row.startMin?.let(::formatMinutes) ?: stringResource(R.string.time_unset))
        }
        Text("–", color = InkMuted)
        TextButton(onClick = { picking = "end" }) {
            Text(row.endMin?.let(::formatMinutes) ?: stringResource(R.string.time_unset))
        }
        NoteField(
            value = row.result,
            hint = stringResource(R.string.activity_result_hint),
            onChange = { cb.onUpdateActivity(row.copy(result = it)) },
            modifier = Modifier.weight(1f),
        )
    }
    picking?.let { which ->
        TimePickerDialog(
            initialMinutes = if (which == "start") row.startMin ?: 540 else row.endMin ?: (row.startMin ?: 540) + 60,
            onConfirm = {
                cb.onUpdateActivity(if (which == "start") row.copy(startMin = it) else row.copy(endMin = it))
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

/** Local-state text field that pushes updates upward without losing the cursor. */
@Composable
private fun NoteField(value: String, hint: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall, color = InkMuted) },
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun NotesCard(state: TodayUiState, cb: TodayCallbacks) {
    SectionCard(title = stringResource(R.string.notes)) {
        NoteField(
            value = state.day.notes,
            hint = stringResource(R.string.notes_hint),
            onChange = cb.onSetNotes,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReportCard(state: TodayUiState, cb: TodayCallbacks) {
    SectionCard(title = stringResource(R.string.report_preview)) {
        if (state.isSpecialDay) {
            val label = if (state.day.dayType == DayType.OFF) stringResource(R.string.day_off) else stringResource(R.string.holiday)
            Text(stringResource(R.string.no_report_special_day, label), color = InkSecondary)
        } else {
            Text(
                state.reportText,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Default),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
            Button(
                onClick = cb.onShare,
                enabled = state.day.hasData,
                colors = ButtonDefaults.buttonColors(containerColor = SendGreen),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.day.reported) stringResource(R.string.resend_whatsapp) else stringResource(R.string.send_whatsapp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

package com.vitalypr.daylog.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitalypr.daylog.R
import com.vitalypr.daylog.data.repo.ActivityRow
import com.vitalypr.daylog.domain.model.ActivityDuration
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.time.formatActivityDuration
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
                    context.startActivity(ReportShare.pdfIntent(context, effect.pdf, effect.caption))
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
            onClearArrival = viewModel::clearArrival,
            onClearDeparture = viewModel::clearDeparture,
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
    val onClearArrival: () -> Unit = {},
    val onClearDeparture: () -> Unit = {},
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
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Text(stringResource(R.string.tab_today), style = MaterialTheme.typography.titleLarge)
            Text(
                "${hebrewDayName(state.date)}, ${formatDate(state.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            StatusBadge(state.status)
        }

        TimeCard(state, callbacks)
        // חופש/חג: no hours and no tasks can be entered — only the chips and the
        // "no report" card remain (product-owner rule; spec S4).
        if (!state.isSpecialDay) {
            FieldJobsCard(state, callbacks)
            ActivitiesCard(state, callbacks)
            NotesCard(state, callbacks)
        }
        ReportCard(state, callbacks)
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun TimeCard(state: TodayUiState, cb: TodayCallbacks) {
    var pickArrival by remember { mutableStateOf(false) }
    var pickDeparture by remember { mutableStateOf(false) }

    SectionCard {
        if (!state.isSpecialDay) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeSlot(
                label = stringResource(R.string.arrival),
                minutes = state.day.arrivalMin,
                source = state.day.arrivalSource,
                uncertain = state.day.arrivalUncertain,
                actionLabel = stringResource(R.string.arrived_now),
                onAction = cb.onArriveNow,
                onEdit = { pickArrival = true },
                onClear = cb.onClearArrival,
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
                onClear = cb.onClearDeparture,
                onNudge = { delta -> state.day.departureMin?.let { cb.onSetDeparture((it + delta).coerceAtLeast(0)) } },
                modifier = Modifier.weight(1f),
            )
        }
        if (!state.isSpecialDay) HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val totalMin = com.vitalypr.daylog.domain.stats.StatsCalculator.dayMinutes(state.day).total
            Text(
                if (totalMin > 0 && !state.isSpecialDay) {
                    stringResource(R.string.total_at_office, formatDuration(totalMin))
                } else {
                    stringResource(R.string.special_day)
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = state.day.dayType == DayType.OFF,
                onClick = { cb.onToggleDayType(DayType.OFF) },
                label = { Text(stringResource(R.string.day_off), style = MaterialTheme.typography.labelMedium) },
            )
            FilterChip(
                selected = state.day.dayType == DayType.HOLIDAY,
                onClick = { cb.onToggleDayType(DayType.HOLIDAY) },
                label = { Text(stringResource(R.string.holiday), style = MaterialTheme.typography.labelMedium) },
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
    uncertain: Boolean = false,
    actionLabel: String,
    onAction: () -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    onNudge: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            // A visit under an hour: amber, so it reads as "check this", not as fact.
            if (minutes != null && uncertain) {
                Text(
                    stringResource(R.string.short_visit_tag),
                    style = MaterialTheme.typography.labelSmall,
                    color = com.vitalypr.daylog.ui.theme.Amber,
                    modifier = Modifier
                        .background(com.vitalypr.daylog.ui.theme.AmberTint, RoundedCornerShape(99.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            } else if (minutes != null && source == TimeSource.GEOFENCE) {
                Text(stringResource(R.string.source_geofence), style = MaterialTheme.typography.labelSmall, color = InkMuted)
            }
            // Reset back to "—:—" — sits in the label row so the compact card keeps its height.
            if (minutes != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(22.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear_time, label),
                        tint = InkMuted,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        TextButton(
            onClick = onEdit,
            enabled = minutes != null,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            modifier = Modifier.height(34.dp),
        ) {
            Text(
                minutes?.let(::formatMinutes) ?: stringResource(R.string.time_unset),
                style = MaterialTheme.typography.headlineSmall,
                color = when {
                    minutes == null -> InkMuted
                    uncertain -> com.vitalypr.daylog.ui.theme.Amber
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (minutes == null) {
            Button(
                onClick = onAction,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
            ) { Text(actionLabel) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactOutlined(stringResource(R.string.minus_5)) { onNudge(-5) }
                CompactOutlined(stringResource(R.string.plus_5)) { onNudge(+5) }
            }
        }
    }
}

@Composable
private fun CompactOutlined(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier
            .height(28.dp)
            .width(46.dp),
    ) { Text(label, style = MaterialTheme.typography.labelMedium) }
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
                val suggested = job.isStartSuggested || job.isEndSuggested
                if (suggested) {
                    Text(
                        stringResource(R.string.suggested_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = com.vitalypr.daylog.ui.theme.Amber,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .background(com.vitalypr.daylog.ui.theme.AmberTint, RoundedCornerShape(99.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                val range = job.startMin?.let { com.vitalypr.daylog.domain.time.formatRange(it, job.endMin) }
                    ?: job.endMin?.let { "…‎–‎" + formatMinutes(it) } ?: ""
                Text(
                    range,
                    style = MaterialTheme.typography.bodySmall,
                    // Geofence-suggested times wear amber until confirmed/edited (spec §6.6b).
                    color = if (suggested) com.vitalypr.daylog.ui.theme.Amber else InkSecondary,
                )
            }
        }
        OutlinedButton(
            onClick = { showSheet = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
        ) { Text(stringResource(R.string.add_field_job), style = MaterialTheme.typography.labelMedium) }
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
    HorizontalDivider(Modifier.padding(vertical = 5.dp))
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
        IconButton(onClick = { cb.onRemoveActivity(row.id) }, modifier = Modifier.height(28.dp).width(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), tint = InkMuted)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // Duration in half-hour steps — no clock times on activities (spec F4 v0.9).
        CompactOutlined(stringResource(R.string.minus_step)) {
            cb.onUpdateActivity(row.copy(durationMin = ActivityDuration.decrease(row.durationMin)))
        }
        Text(
            row.durationMin?.let(::formatActivityDuration) ?: stringResource(R.string.duration_unset),
            style = MaterialTheme.typography.labelLarge,
            color = if (row.durationMin != null) MaterialTheme.colorScheme.onSurface else InkMuted,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        CompactOutlined(stringResource(R.string.plus_step)) {
            cb.onUpdateActivity(row.copy(durationMin = ActivityDuration.increase(row.durationMin)))
        }
        Spacer(Modifier.width(6.dp))
        NoteField(
            value = row.result,
            hint = stringResource(R.string.activity_result_hint),
            onChange = { cb.onUpdateActivity(row.copy(result = it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactTimeButton(minutes: Int?, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
        modifier = Modifier.height(28.dp),
    ) {
        Text(
            minutes?.let(::formatMinutes) ?: stringResource(R.string.time_unset),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Compact single-line field (32dp vs OutlinedTextField's 56dp minimum). */
@Composable
private fun NoteField(value: String, hint: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value) }
    androidx.compose.foundation.text.BasicTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = true,
        modifier = modifier.height(28.dp),
        decorationBox = { inner ->
            Column {
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    }
                    inner()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        },
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
                stringResource(R.string.report_pdf_note),
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            Button(
                onClick = cb.onShare,
                enabled = state.day.hasData,
                colors = ButtonDefaults.buttonColors(containerColor = SendGreen),
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                Text(
                    if (state.day.reported) stringResource(R.string.resend_whatsapp) else stringResource(R.string.send_whatsapp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

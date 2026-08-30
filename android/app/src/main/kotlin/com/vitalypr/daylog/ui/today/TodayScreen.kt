package com.vitalypr.daylog.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitalypr.daylog.R
import com.vitalypr.daylog.data.db.ProjectEntity
import com.vitalypr.daylog.data.repo.ActivityRow
import com.vitalypr.daylog.data.repo.SessionRow
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeBudget
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.model.budget
import com.vitalypr.daylog.domain.time.WorkTimeStep
import com.vitalypr.daylog.domain.time.formatActivityDuration
import com.vitalypr.daylog.domain.time.formatDate
import com.vitalypr.daylog.domain.time.formatDuration
import com.vitalypr.daylog.domain.time.formatMinutes
import com.vitalypr.daylog.domain.time.hebrewDayName
import com.vitalypr.daylog.reporting.ReportShare
import com.vitalypr.daylog.ui.components.SectionCard
import com.vitalypr.daylog.ui.components.StatusBadge
import com.vitalypr.daylog.ui.components.TimePickerDialog
import com.vitalypr.daylog.ui.theme.Amber
import com.vitalypr.daylog.ui.theme.AmberTint
import com.vitalypr.daylog.ui.theme.InkMuted
import com.vitalypr.daylog.ui.theme.InkSecondary
import com.vitalypr.daylog.ui.theme.SendGreen
import com.vitalypr.daylog.ui.theme.Warn
import com.vitalypr.daylog.ui.theme.WarnTint

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
            onAddSession = viewModel::addSession,
            onRemoveSession = viewModel::removeSession,
            onSetSessionStart = viewModel::setSessionStart,
            onSetSessionEnd = viewModel::setSessionEnd,
            onStartNow = viewModel::startNow,
            onEndNow = viewModel::endNow,
            onSetSessionTitle = viewModel::setSessionTitle,
            onToggleDayType = viewModel::toggleDayType,
            onAddActivity = viewModel::addActivity,
            onSetActivityNote = viewModel::setActivityNote,
            onSetActivityResult = viewModel::setActivityResult,
            onSetActivityProject = viewModel::setActivityProject,
            onStepActivityDuration = viewModel::stepActivityDuration,
            onRemoveActivity = viewModel::removeActivity,
            onSetNotes = viewModel::setNotes,
            onShare = viewModel::share,
        ),
    )
}

/** All screen callbacks in one bundle so TodayContent stays snapshot-testable. */
data class TodayCallbacks(
    val onAddSession: (WorkMode) -> Unit = {},
    val onRemoveSession: (Long) -> Unit = {},
    val onSetSessionStart: (Long, Int?) -> Unit = { _, _ -> },
    val onSetSessionEnd: (Long, Int?) -> Unit = { _, _ -> },
    val onStartNow: (Long) -> Unit = {},
    val onEndNow: (Long) -> Unit = {},
    val onSetSessionTitle: (Long, String) -> Unit = { _, _ -> },
    val onToggleDayType: (DayType) -> Unit = {},
    val onAddActivity: (Long, Long, Long) -> Unit = { _, _, _ -> },
    val onSetActivityNote: (Long, String) -> Unit = { _, _ -> },
    val onSetActivityResult: (Long, String) -> Unit = { _, _ -> },
    val onSetActivityProject: (Long, Long) -> Unit = { _, _ -> },
    val onStepActivityDuration: (Long, Boolean) -> Unit = { _, _ -> },
    val onRemoveActivity: (Long) -> Unit = {},
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

        DayCard(state, callbacks)
        // חופש/חג: no hours and no tasks can be entered — only the chips and the
        // "no report" card remain (product-owner rule; spec S4).
        if (!state.isSpecialDay) {
            state.sessionRows.forEach { row ->
                androidx.compose.runtime.key(row.entity.id) { SessionCard(row, state, callbacks) }
            }
            AddSessionRow(state, callbacks)
            NotesCard(state, callbacks)
        }
        ReportCard(state, callbacks)
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

/** The day at a glance: how long was worked, split by mode, and how much of it is described. */
@Composable
private fun DayCard(state: TodayUiState, cb: TodayCallbacks) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val totals = state.modeTotals
            val total = totals.values.sum()
            // joinToString isn't inline, so the labels are resolved first.
            val labels = WorkMode.entries.associateWith { modeLabel(it) }
            val parts = WorkMode.entries
                .filter { (totals[it] ?: 0) > 0 }
                .joinToString(" · ") { "${labels.getValue(it)} ${formatDuration(totals.getValue(it))}" }
            Text(
                when {
                    state.isSpecialDay -> ""
                    totals.count { it.value > 0 } > 1 ->
                        stringResource(R.string.day_total_modes, formatDuration(total), parts)
                    else -> stringResource(R.string.total_at_office, formatDuration(total))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
        // How much of the worked time the activities account for — the whole point
        // of the screen is to leave the day fully described (and to say so loudly
        // when the activities claim more time than was actually worked).
        // With one session its own card already says this; the day line earns its
        // place only when several sessions add up.
        if (!state.isSpecialDay && state.day.sessions.size > 1) {
            BudgetLine(state.budget, Modifier.padding(top = 6.dp))
        }
    }
}

/** One stretch of work: its mode, its hours, its own time budget and its activities. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(row: SessionRow, state: TodayUiState, cb: TodayCallbacks) {
    val id = row.entity.id
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    // An activity always belongs to a project, so the category tap asks which one
    // before anything is created (v1.2).
    var pendingCategory by remember { mutableStateOf<Long?>(null) }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(modeLabel(row.session.mode), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            NoteField(
                value = row.entity.title,
                hint = stringResource(
                    // Only a field session is at someone's site; the others are a
                    // free description of what that stretch of work was.
                    if (row.session.mode == WorkMode.FIELD) {
                        R.string.session_site_hint
                    } else {
                        R.string.session_title_hint
                    },
                ),
                onChange = { cb.onSetSessionTitle(id, it) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { cb.onRemoveSession(id) }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_session),
                    tint = InkMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            TimeSlot(
                label = stringResource(R.string.arrival),
                minutes = row.session.startMin,
                source = row.session.startSource,
                uncertain = row.session.startUncertain,
                actionLabel = stringResource(R.string.arrived_now),
                onAction = { cb.onStartNow(id) },
                onEdit = { pickStart = true },
                onClear = { cb.onSetSessionStart(id, null) },
                onNudge = { delta ->
                    row.session.startMin?.let { cb.onSetSessionStart(id, (it + delta).coerceAtLeast(0)) }
                },
                modifier = Modifier.weight(1f),
            )
            TimeSlot(
                label = stringResource(R.string.departure),
                minutes = row.session.endMin,
                source = row.session.endSource,
                actionLabel = stringResource(R.string.left_now),
                onAction = { cb.onEndNow(id) },
                onEdit = { pickEnd = true },
                onClear = { cb.onSetSessionEnd(id, null) },
                onNudge = { delta ->
                    row.session.endMin?.let { cb.onSetSessionEnd(id, (it + delta).coerceAtLeast(0)) }
                },
                modifier = Modifier.weight(1f),
            )
        }

        BudgetLine(row.session.budget(), Modifier.padding(top = 4.dp))

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.categories.forEach { cat ->
                FilterChip(
                    selected = row.activityRows.any { it.categoryId == cat.id },
                    onClick = {
                        // One project: no question worth asking. Several: pick.
                        val only = state.projects.singleOrNull()
                        if (only != null) cb.onAddActivity(id, cat.id, only.id) else pendingCategory = cat.id
                    },
                    label = { Text(cat.name) },
                )
            }
        }
        if (state.projects.isEmpty()) {
            Text(
                stringResource(R.string.no_projects_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Amber,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        row.activityRows.forEach { activity ->
            androidx.compose.runtime.key(activity.id) { ActivityEditor(activity, state.projects, cb) }
        }
    }

    if (pickStart) {
        TimePickerDialog(
            initialMinutes = row.session.startMin ?: defaultStartMinutes(row.session.mode),
            onConfirm = { cb.onSetSessionStart(id, it); pickStart = false },
            onDismiss = { pickStart = false },
        )
    }
    if (pickEnd) {
        TimePickerDialog(
            initialMinutes = row.session.endMin ?: (row.session.startMin?.plus(60) ?: (17 * 60)),
            onConfirm = { cb.onSetSessionEnd(id, it); pickEnd = false },
            onDismiss = { pickEnd = false },
        )
    }
    pendingCategory?.let { categoryId ->
        ProjectPickerDialog(
            projects = state.projects,
            onPick = { projectId ->
                cb.onAddActivity(id, categoryId, projectId)
                pendingCategory = null
            },
            onDismiss = { pendingCategory = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSessionRow(state: TodayUiState, cb: TodayCallbacks) {
    SectionCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkMode.entries.forEach { mode ->
                OutlinedButton(
                    onClick = { cb.onAddSession(mode) },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(
                        stringResource(R.string.add_session, modeLabel(mode)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * "How much of this time is described?" — worked vs. allocated vs. left, and a
 * red line when the activities add up to more than the hours actually worked.
 */
@Composable
private fun BudgetLine(budget: TimeBudget, modifier: Modifier = Modifier) {
    val allocated = formatDuration(budget.allocatedMin)
    val span = budget.spanMin
    val remaining = budget.remainingMin
    val problem = budget.overAllocated
    val text = when {
        span == null -> stringResource(R.string.budget_no_span, allocated)
        problem -> stringResource(R.string.budget_over, allocated, formatDuration(span), formatDuration(-remaining!!))
        remaining == 0 -> stringResource(R.string.budget_complete, allocated, formatDuration(span))
        else -> stringResource(R.string.budget_line, allocated, formatDuration(span), formatDuration(remaining!!))
    }
    val color = when {
        problem -> Warn
        span != null && remaining == 0 -> SendGreen
        else -> InkSecondary
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = if (problem) {
            modifier
                .fillMaxWidth()
                .background(WarnTint, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            modifier
        },
    )
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
                    color = Amber,
                    modifier = Modifier
                        .background(AmberTint, RoundedCornerShape(99.dp))
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
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.height(34.dp),
        ) {
            Text(
                minutes?.let(::formatMinutes) ?: stringResource(R.string.time_unset),
                style = MaterialTheme.typography.headlineSmall,
                color = when {
                    minutes == null -> InkMuted
                    uncertain -> Amber
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (minutes == null) {
            Button(
                onClick = onAction,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
            ) { Text(actionLabel) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactOutlined(stringResource(R.string.minus_15)) { onNudge(-WorkTimeStep.STEP_MIN) }
                CompactOutlined(stringResource(R.string.plus_15)) { onNudge(+WorkTimeStep.STEP_MIN) }
            }
        }
    }
}

@Composable
private fun CompactOutlined(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .height(28.dp)
            .width(46.dp),
    ) { Text(label, style = MaterialTheme.typography.labelMedium) }
}

/** Which project does this work belong to? Asked once, at creation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectPickerDialog(
    projects: List<ProjectEntity>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_project)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                projects.forEach { project ->
                    FilterChip(
                        selected = false,
                        onClick = { onPick(project.id) },
                        label = { Text(project.name) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ActivityEditor(row: ActivityRow, projects: List<ProjectEntity>, cb: TodayCallbacks) {
    var reassigning by remember { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(vertical = 5.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Project first, then what was done — the same order the report uses.
        TextButton(
            onClick = { reassigning = true },
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.height(24.dp),
        ) {
            Text(
                row.project.ifBlank { stringResource(R.string.pick_project) },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "· ${row.category}",
            style = MaterialTheme.typography.labelSmall,
            color = InkSecondary,
        )
        Spacer(Modifier.width(4.dp))
        NoteField(
            value = row.note,
            hint = stringResource(R.string.activity_note_hint),
            onChange = { cb.onSetActivityNote(row.id, it) },
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { cb.onRemoveActivity(row.id) },
            modifier = Modifier
                .height(28.dp)
                .width(32.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), tint = InkMuted)
        }
    }
    if (reassigning) {
        ProjectPickerDialog(
            projects = projects,
            onPick = { projectId ->
                cb.onSetActivityProject(row.id, projectId)
                reassigning = false
            },
            onDismiss = { reassigning = false },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // Duration in half-hour steps — no clock times on activities (spec F4 v0.9).
        CompactOutlined(stringResource(R.string.minus_step)) { cb.onStepActivityDuration(row.id, false) }
        Text(
            row.durationMin?.let(::formatActivityDuration) ?: stringResource(R.string.duration_unset),
            style = MaterialTheme.typography.labelLarge,
            color = if (row.durationMin != null) MaterialTheme.colorScheme.onSurface else InkMuted,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        CompactOutlined(stringResource(R.string.plus_step)) { cb.onStepActivityDuration(row.id, true) }
        Spacer(Modifier.width(6.dp))
        NoteField(
            value = row.result,
            hint = stringResource(R.string.activity_result_hint),
            onChange = { cb.onSetActivityResult(row.id, it) },
            modifier = Modifier.weight(1f),
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
            val label = if (state.day.dayType == DayType.OFF) {
                stringResource(R.string.day_off)
            } else {
                stringResource(R.string.holiday)
            }
            Text(stringResource(R.string.no_report_special_day, label), color = InkSecondary)
        } else {
            // The report itself is the preview — the user checks what will be
            // sent, not a description of it.
            if (state.reportText.isNotBlank()) {
                Text(
                    state.reportText,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )
            }
            Button(
                onClick = cb.onShare,
                enabled = state.day.hasData,
                colors = ButtonDefaults.buttonColors(containerColor = SendGreen),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                Text(
                    if (state.day.reported) {
                        stringResource(R.string.resend_whatsapp)
                    } else {
                        stringResource(R.string.send_whatsapp)
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun modeLabel(mode: WorkMode): String = stringResource(
    when (mode) {
        WorkMode.BASE -> R.string.mode_base
        WorkMode.HOME -> R.string.mode_home
        WorkMode.FIELD -> R.string.mode_field
    },
)

/** A sensible starting point for the picker when nothing is set yet. */
private fun defaultStartMinutes(mode: WorkMode): Int = when (mode) {
    WorkMode.BASE -> 8 * 60
    WorkMode.HOME -> 18 * 60
    WorkMode.FIELD -> 10 * 60
}

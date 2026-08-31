package com.vitalypr.daylog.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitalypr.daylog.R
import com.vitalypr.daylog.domain.time.formatDuration
import com.vitalypr.daylog.domain.time.formatMinutes
import com.vitalypr.daylog.reporting.ReportShare
import com.vitalypr.daylog.ui.components.SectionCard
import com.vitalypr.daylog.ui.theme.ChartField
import com.vitalypr.daylog.ui.theme.ChartHome
import com.vitalypr.daylog.ui.theme.ChartOffice
import com.vitalypr.daylog.ui.theme.InkMuted
import com.vitalypr.daylog.ui.theme.InkSecondary
import com.vitalypr.daylog.ui.theme.Line
import com.vitalypr.daylog.ui.theme.SendGreen

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is StatsEffect.LaunchShare ->
                    context.startActivity(ReportShare.pdfIntent(context, effect.pdf, effect.caption))
            }
        }
    }
    StatsContent(
        state = state,
        onPeriod = viewModel::setPeriod,
        onSelectBar = viewModel::selectBar,
        onShare = viewModel::share,
        onPrevious = viewModel::previousPeriod,
        onNext = viewModel::nextPeriod,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsContent(
    state: StatsUiState,
    onPeriod: (StatsPeriod) -> Unit = {},
    onSelectBar: (Int?) -> Unit = {},
    onShare: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tab_stats),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(
                StatsPeriod.WEEK to stringResource(R.string.period_week),
                StatsPeriod.MONTH to stringResource(R.string.period_month),
                StatsPeriod.QUARTER to stringResource(R.string.period_quarter),
                StatsPeriod.YEAR to stringResource(R.string.period_year),
            )
            labels.forEachIndexed { i, (p, label) ->
                SegmentedButton(
                    selected = state.period == p,
                    onClick = { onPeriod(p) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                ) { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
            }
        }

        // Which week/month/quarter/year — a period report is usually filed once
        // the period is over, so the past has to be reachable.
        PeriodNavigator(state, onPrevious, onNext)

        state.summary?.let { s ->
            KpiGrid(
                listOf(
                    formatDuration(s.totalMinutes) to stringResource(R.string.kpi_total_hours),
                    "${s.workDays}" to stringResource(R.string.kpi_work_days),
                    "${s.fieldDays}" to stringResource(R.string.kpi_field_days),
                    (if (s.workDays > 0) formatDuration(s.totalMinutes / s.workDays) else "—") to stringResource(R.string.kpi_avg_day),
                    (s.avgArrivalMin?.let(::formatMinutes) ?: "—") to stringResource(R.string.kpi_avg_arrival),
                    (s.avgDepartureMin?.let(::formatMinutes) ?: "—") to stringResource(R.string.kpi_avg_departure),
                ),
            )
            // The mode split as text — the chart is never the only source (§5.3).
            val modeParts = listOf(
                stringResource(R.string.legend_office) to s.baseMinutes,
                stringResource(R.string.legend_home) to s.homeMinutes,
                stringResource(R.string.legend_field) to s.fieldMinutes,
            ).filter { it.second > 0 }
            if (modeParts.isNotEmpty()) {
                Text(
                    modeParts.joinToString(" · ") { "${it.first} ${formatDuration(it.second)}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
            }
            if (s.offDays > 0 || s.holidays > 0) {
                Text(
                    buildString {
                        if (s.offDays > 0) append("${s.offDays} ימי חופש")
                        if (s.offDays > 0 && s.holidays > 0) append(" · ")
                        if (s.holidays > 0) append("${s.holidays} חגים")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
            }
        }

        SectionCard(title = state.chartTitle) {
            ChartLegend()
            Spacer(Modifier.height(8.dp))
            HoursChart(
                bars = state.bars,
                avgMinutes = state.summary?.let { if (it.workDays > 0) it.totalMinutes / it.workDays else null },
                selected = state.selectedBar,
                onSelect = onSelectBar,
            )
            state.selectedBar?.let { i ->
                state.bars.getOrNull(i)?.let { bar ->
                    val offLabel = stringResource(R.string.day_off)
                    val parts = listOf(
                        stringResource(R.string.legend_office) to bar.baseMin,
                        stringResource(R.string.legend_home) to bar.homeMin,
                        stringResource(R.string.legend_field) to bar.fieldMin,
                    ).filter { it.second > 0 }
                    val detail = when {
                        bar.isOff -> "${bar.label}: $offLabel"
                        parts.isEmpty() -> "${bar.label}: —"
                        else -> parts.joinToString(" · ", prefix = "${bar.label}: ") {
                            "${it.first} ${formatDuration(it.second)}"
                        }
                    }
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        // Where the hours went (v2.2). Activities carry durations, not clock
        // times, so this can only account for what was described — the rest is
        // named "לא שויך" rather than quietly missing from the split.
        state.summary?.let { s ->
            if (s.projectMinutes.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.hours_by_project)) {
                    val max = (s.projectMinutes.first().second)
                        .coerceAtLeast(s.unallocatedMinutes)
                        .coerceAtLeast(1)
                    s.projectMinutes.forEach { (name, minutes) ->
                        ProjectHoursRow(name, minutes, minutes.toFloat() / max, ChartOffice)
                    }
                    if (s.unallocatedMinutes > 0) {
                        ProjectHoursRow(
                            stringResource(R.string.unallocated_hours),
                            s.unallocatedMinutes,
                            s.unallocatedMinutes.toFloat() / max,
                            InkMuted,
                        )
                    }
                }
            }
        }

        state.summary?.let { s ->
            if (s.categoryCounts.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.activities_by_category)) {
                    val max = s.categoryCounts.first().second
                    s.categoryCounts.take(6).forEach { (name, count) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Text(name, Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(13.dp),
                            ) {
                                Surface(
                                    color = ChartOffice,
                                    shape = RoundedCornerShape(5.dp),
                                    modifier = Modifier
                                        .fillMaxWidth(count.toFloat() / max)
                                        .height(13.dp),
                                ) {}
                            }
                            Text(
                                "$count",
                                Modifier.width(32.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary,
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = onShare,
            enabled = state.summary != null && state.summary.workDays > 0,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = SendGreen),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.share_period_summary)) }
        Spacer(Modifier.height(8.dp))
    }
}

/** ‹ current period › — the label names it, the arrows move through it. */
@Composable
private fun PeriodNavigator(state: StatsUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.IconButton(onClick = onPrevious) {
            androidx.compose.material3.Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.period_previous),
            )
        }
        Text(
            state.summary?.label.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.IconButton(onClick = onNext, enabled = state.canGoForward) {
            androidx.compose.material3.Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.period_next),
                tint = if (state.canGoForward) InkSecondary else Line,
            )
        }
    }
}

@Composable
private fun KpiGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { (value, label) ->
                    SectionCard(modifier = Modifier.weight(1f)) {
                        Text(value, style = MaterialTheme.typography.titleLarge)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = InkMuted)
                    }
                }
            }
        }
    }
}

/** One project: its name and hours over a full-width share bar. */
@Composable
private fun ProjectHoursRow(
    name: String,
    minutes: Int,
    fraction: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatDuration(minutes),
                style = MaterialTheme.typography.labelLarge,
                color = InkSecondary,
            )
        }
        Box(
            Modifier
                .padding(top = 3.dp)
                .fillMaxWidth()
                .height(9.dp)
                .background(Line, RoundedCornerShape(5.dp)),
        ) {
            Surface(
                color = color,
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(9.dp),
            ) {}
        }
    }
}

@Composable
private fun ChartLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(ChartOffice, stringResource(R.string.legend_office))
        LegendDot(ChartHome, stringResource(R.string.legend_home))
        LegendDot(ChartField, stringResource(R.string.legend_field))
        Text("– – ${stringResource(R.string.legend_avg)}", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = RoundedCornerShape(3.dp), modifier = Modifier.size(9.dp)) {}
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkSecondary)
    }
}

/**
 * Stacked base/home/field bars, RTL time axis (first bar at the right), dashed
 * average line, 2dp gaps between stacked segments (dataviz mark specs).
 */
@Composable
fun HoursChart(
    bars: List<StatsBar>,
    avgMinutes: Int?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    if (bars.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = InkMuted)
    val density = LocalDensity.current
    val maxTotal = (bars.maxOf { it.totalMin }).coerceAtLeast(60)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(bars) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat()
                    val slot = w / bars.size
                    val idxFromLeft = (offset.x / slot).toInt().coerceIn(0, bars.lastIndex)
                    onSelect(bars.lastIndex - idxFromLeft) // RTL: first bar rightmost
                }
            },
    ) {
        val labelSpace = with(density) { 16.dp.toPx() }
        val plotH = size.height - labelSpace
        val slot = size.width / bars.size
        val gap = with(density) { if (bars.size > 15) 2.dp.toPx() else 6.dp.toPx() }
        val barW = (slot - gap).coerceAtLeast(with(density) { 2.dp.toPx() })
        val segGap = with(density) { 2.dp.toPx() }
        val corner = with(density) { 2.dp.toPx() }

        // grid lines at 0 / half / max
        listOf(0f, 0.5f, 1f).forEach { g ->
            val y = plotH * (1 - g)
            drawLine(Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        bars.forEachIndexed { i, bar ->
            // RTL axis: bar i sits i slots from the RIGHT edge.
            val x = size.width - (i + 1) * slot + gap / 2
            val highlight = selected == null || selected == i
            // Stack bottom-up: base, then home, then field — one segment per mode.
            var top = plotH
            listOf(
                ChartOffice to bar.baseMin,
                ChartHome to bar.homeMin,
                ChartField to bar.fieldMin,
            ).forEach { (color, minutes) ->
                if (minutes <= 0) return@forEach
                val h = plotH * minutes / maxTotal
                drawRoundRect(
                    color.copy(alpha = if (highlight) 1f else 0.35f),
                    topLeft = Offset(x, top - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(corner),
                )
                top -= h + segGap
            }
            val showLabel = bars.size <= 12 || i % 5 == 0
            if (showLabel) {
                val measured = textMeasurer.measure(bar.label, labelStyle)
                drawText(
                    measured,
                    topLeft = Offset(x + barW / 2 - measured.size.width / 2, plotH + 2f),
                )
            }
        }

        avgMinutes?.let { avg ->
            val y = plotH * (1 - avg.toFloat() / maxTotal)
            if (y in 0f..plotH) {
                drawLine(
                    InkMuted,
                    Offset(0f, y), Offset(size.width, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }
        }
    }
}

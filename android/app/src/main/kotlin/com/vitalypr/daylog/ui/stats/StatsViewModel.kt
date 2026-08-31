package com.vitalypr.daylog.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.report.ReportBuilder
import com.vitalypr.daylog.domain.stats.PeriodSummary
import com.vitalypr.daylog.domain.stats.StatsCalculator
import com.vitalypr.daylog.domain.time.formatDate
import com.vitalypr.daylog.domain.time.hebrewMonthName
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

enum class StatsPeriod { WEEK, MONTH, QUARTER, YEAR }

/** One chart bar: the day's minutes split by work mode, drawn as a stack. */
data class StatsBar(
    val label: String,
    val baseMin: Int = 0,
    val homeMin: Int = 0,
    val fieldMin: Int = 0,
    val isOff: Boolean = false,
) {
    val totalMin: Int get() = baseMin + homeMin + fieldMin
}

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val summary: PeriodSummary? = null,
    val bars: List<StatsBar> = emptyList(),
    val chartTitle: String = "",
    val selectedBar: Int? = null,
    /** 0 = the period we are in, -1 = the one before it. Never positive. */
    val offset: Int = 0,
) {
    /** The future holds no hours to report. */
    val canGoForward: Boolean get() = offset < 0
}

sealed interface StatsEffect {
    /** Period summary ships as a styled PDF with the text as caption (like the daily report). */
    data class LaunchShare(val pdf: java.io.File, val caption: String) : StatsEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: DayRepository,
    private val periodPdf: com.vitalypr.daylog.reporting.PeriodPdfRenderer,
    @Now private val now: () -> LocalDateTime,
) : ViewModel() {

    private val period = MutableStateFlow(StatsPeriod.WEEK)
    private val selected = MutableStateFlow<Int?>(null)
    private val offset = MutableStateFlow(0)

    private val effects = Channel<StatsEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    val uiState: StateFlow<StatsUiState> =
        kotlinx.coroutines.flow.combine(period, selected, offset) { p, sel, off -> Triple(p, sel, off) }
            .mapLatest { (p, sel, off) ->
                val today = now().toLocalDate()
                val (from, to, label, title) = periodRange(p, today, off)
                val days = repository.getRange(from, to)
                StatsUiState(
                    period = p,
                    summary = StatsCalculator.summarize(label, days),
                    bars = buildBars(p, from, to, days),
                    chartTitle = title,
                    selectedBar = sel,
                    offset = off,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /** Switching the kind of period starts again from the current one. */
    fun setPeriod(p: StatsPeriod) { period.value = p; selected.value = null; offset.value = 0 }
    fun selectBar(index: Int?) { selected.value = index }

    /** Step back through past periods — a month's report is usually filed after it ends. */
    fun previousPeriod() { offset.value -= 1; selected.value = null }
    fun nextPeriod() { if (offset.value < 0) { offset.value += 1; selected.value = null } }

    fun share() = viewModelScope.launch {
        uiState.value.summary?.let { summary ->
            effects.send(StatsEffect.LaunchShare(periodPdf.render(summary), ReportBuilder.period(summary)))
        }
    }

    private data class Range(val from: LocalDate, val to: LocalDate, val label: String, val title: String)

    private fun periodRange(p: StatsPeriod, today: LocalDate, offset: Int): Range = when (p) {
        StatsPeriod.WEEK -> {
            val from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).plusWeeks(offset.toLong())
            val to = from.plusDays(6)
            val span = "${formatDate(from)}–${formatDate(to)}"
            Range(from, to, "סיכום שבועי ($span)", "שעות לפי יום — $span")
        }
        StatsPeriod.MONTH -> {
            val m = YearMonth.from(today).plusMonths(offset.toLong())
            val name = "${hebrewMonthName(m.monthValue)} ${m.year}"
            Range(m.atDay(1), m.atEndOfMonth(), "סיכום חודשי — $name", "שעות לפי יום — $name")
        }
        StatsPeriod.QUARTER -> {
            // The quarter we are in, then back in three-month steps.
            val first = YearMonth.from(today).let { it.minusMonths(((it.monthValue - 1) % 3).toLong()) }
                .plusMonths((offset * 3).toLong())
            val last = first.plusMonths(2)
            val name = "רבעון ${(first.monthValue - 1) / 3 + 1} ${first.year}"
            Range(first.atDay(1), last.atEndOfMonth(), "סיכום רבעוני — $name", "שעות לפי שבוע — $name")
        }
        StatsPeriod.YEAR -> {
            val year = today.year + offset
            Range(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31),
                "סיכום שנתי — $year",
                "שעות לפי חודש — $year",
            )
        }
    }

    private fun buildBars(p: StatsPeriod, from: LocalDate, to: LocalDate, days: List<DaySnapshot>): List<StatsBar> {
        val byDate = days.associateBy { it.date }
        return when (p) {
            StatsPeriod.WEEK, StatsPeriod.MONTH -> generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .map { d ->
                    val snap = byDate[d]
                    val m = snap?.let(StatsCalculator::dayMinutes)
                    StatsBar(
                        label = if (p == StatsPeriod.WEEK) hebrewDayLetter(d) else d.dayOfMonth.toString(),
                        baseMin = m?.base ?: 0,
                        homeMin = m?.home ?: 0,
                        fieldMin = m?.field ?: 0,
                        isOff = snap?.dayType?.name in listOf("OFF", "HOLIDAY"),
                    )
                }.toList()
            // A quarter is thirteen weeks: per-day would be unreadable, per-month
            // would be three bars.
            StatsPeriod.QUARTER -> {
                val firstWeek = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                generateSequence(firstWeek) { it.plusWeeks(1) }
                    .takeWhile { !it.isAfter(to) }
                    .map { weekStart ->
                        val week = days.filter { it.date >= weekStart && it.date <= weekStart.plusDays(6) }
                        val sums = week.map(StatsCalculator::dayMinutes)
                        StatsBar(
                            label = "${weekStart.dayOfMonth}.${weekStart.monthValue}",
                            baseMin = sums.sumOf { m -> m.base },
                            homeMin = sums.sumOf { m -> m.home },
                            fieldMin = sums.sumOf { m -> m.field },
                        )
                    }.toList()
            }
            StatsPeriod.YEAR -> (1..12).map { month ->
                val inMonth = days.filter { it.date.monthValue == month }
                val sums = inMonth.map(StatsCalculator::dayMinutes)
                StatsBar(
                    label = hebrewMonthName(month).take(3),
                    baseMin = sums.sumOf { m -> m.base },
                    homeMin = sums.sumOf { m -> m.home },
                    fieldMin = sums.sumOf { m -> m.field },
                )
            }
        }
    }

    private fun hebrewDayLetter(d: LocalDate): String = when (d.dayOfWeek) {
        DayOfWeek.SUNDAY -> "א"; DayOfWeek.MONDAY -> "ב"; DayOfWeek.TUESDAY -> "ג"
        DayOfWeek.WEDNESDAY -> "ד"; DayOfWeek.THURSDAY -> "ה"; DayOfWeek.FRIDAY -> "ו"
        DayOfWeek.SATURDAY -> "ש"
    }
}

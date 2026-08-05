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

enum class StatsPeriod { WEEK, MONTH, YEAR }

/** One chart bar: office + field minutes (field drawn stacked on top). */
data class StatsBar(val label: String, val officeMin: Int, val fieldMin: Int, val isOff: Boolean = false) {
    val totalMin: Int get() = officeMin + fieldMin
}

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val summary: PeriodSummary? = null,
    val bars: List<StatsBar> = emptyList(),
    val chartTitle: String = "",
    val selectedBar: Int? = null,
)

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

    private val effects = Channel<StatsEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    val uiState: StateFlow<StatsUiState> = kotlinx.coroutines.flow.combine(period, selected) { p, sel -> p to sel }
        .mapLatest { (p, sel) ->
            val today = now().toLocalDate()
            val (from, to, label, title) = periodRange(p, today)
            val days = repository.getRange(from, to)
            StatsUiState(
                period = p,
                summary = StatsCalculator.summarize(label, days),
                bars = buildBars(p, from, to, days),
                chartTitle = title,
                selectedBar = sel,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPeriod(p: StatsPeriod) { period.value = p; selected.value = null }
    fun selectBar(index: Int?) { selected.value = index }

    fun share() = viewModelScope.launch {
        uiState.value.summary?.let { summary ->
            effects.send(StatsEffect.LaunchShare(periodPdf.render(summary), ReportBuilder.period(summary)))
        }
    }

    private data class Range(val from: LocalDate, val to: LocalDate, val label: String, val title: String)

    private fun periodRange(p: StatsPeriod, today: LocalDate): Range = when (p) {
        StatsPeriod.WEEK -> {
            val from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val to = from.plusDays(6)
            Range(from, to, "סיכום שבועי (${formatDate(from)}–${formatDate(to)})", "שעות לפי יום — השבוע")
        }
        StatsPeriod.MONTH -> {
            val m = YearMonth.from(today)
            Range(
                m.atDay(1), m.atEndOfMonth(),
                "סיכום חודשי — ${hebrewMonthName(m.monthValue)} ${m.year}",
                "שעות לפי יום — ${hebrewMonthName(m.monthValue)} ${m.year}",
            )
        }
        StatsPeriod.YEAR -> Range(
            LocalDate.of(today.year, 1, 1), LocalDate.of(today.year, 12, 31),
            "סיכום שנתי — ${today.year}",
            "שעות לפי חודש — ${today.year}",
        )
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
                        officeMin = m?.office ?: 0,
                        fieldMin = m?.fieldOutside ?: 0,
                        isOff = snap?.dayType?.name in listOf("OFF", "HOLIDAY"),
                    )
                }.toList()
            StatsPeriod.YEAR -> (1..12).map { month ->
                val inMonth = days.filter { it.date.monthValue == month }
                val sums = inMonth.map(StatsCalculator::dayMinutes)
                StatsBar(
                    label = hebrewMonthName(month).take(3),
                    officeMin = sums.sumOf { it.office },
                    fieldMin = sums.sumOf { it.fieldOutside },
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

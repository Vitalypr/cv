package com.vitalypr.daylog.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayStatus
import com.vitalypr.daylog.domain.model.status
import com.vitalypr.daylog.domain.stats.StatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryDayCard(
    val date: LocalDate,
    val status: DayStatus,
    val totalMinutes: Int,
    val summary: String, // first categories / field-job hint
)

data class HistoryUiState(
    val month: YearMonth,
    val days: List<HistoryDayCard> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: DayRepository,
    @Now now: () -> LocalDateTime,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(now().toLocalDate()))

    val uiState: StateFlow<HistoryUiState> = month
        .flatMapLatest { m ->
            repository.observeRange(m.atDay(1), m.atEndOfMonth()).map { days ->
                HistoryUiState(
                    month = m,
                    days = days.filter { it.hasData || it.dayType.name != "WORK" }.map { it.toCard() },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState(month.value))

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }

    private fun DaySnapshot.toCard(): HistoryDayCard {
        val cats = activities.map { it.category }.distinct().take(3)
        val hint = buildList {
            addAll(cats)
            if (fieldJobs.isNotEmpty()) add("שטח")
        }.joinToString(" · ")
        return HistoryDayCard(
            date = date,
            status = status(),
            totalMinutes = StatsCalculator.dayMinutes(this).total,
            summary = hint,
        )
    }
}

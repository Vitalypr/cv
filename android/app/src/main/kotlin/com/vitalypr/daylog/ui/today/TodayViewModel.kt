package com.vitalypr.daylog.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitalypr.daylog.data.db.CategoryEntity
import com.vitalypr.daylog.data.repo.ActivityRow
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.data.repo.EditableDay
import com.vitalypr.daylog.data.repo.FieldJobRow
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayStatus
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.FieldJob
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.status
import com.vitalypr.daylog.domain.report.ReportBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayUiState(
    val date: LocalDate,
    val day: DaySnapshot,
    val activityRows: List<ActivityRow> = emptyList(),
    val fieldJobRows: List<FieldJobRow> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val projects: List<com.vitalypr.daylog.data.db.ProjectEntity> = emptyList(),
    val reportText: String = "",
    val status: DayStatus = DayStatus.EMPTY,
) {
    val isSpecialDay: Boolean get() = day.dayType != DayType.WORK
}

sealed interface TodayEffect {
    /** Screen launches the PDF share intent; day was already marked reported. */
    data class LaunchShare(val pdf: java.io.File, val caption: String) : TodayEffect
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: DayRepository,
    private val projectRepository: com.vitalypr.daylog.data.repo.ProjectRepository,
    private val reportPdf: com.vitalypr.daylog.reporting.DailyPdfRenderer,
    @Now private val now: () -> LocalDateTime,
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    /** Bound day: today by default; History passes a "date" nav argument to edit past days. */
    val date: LocalDate = savedStateHandle.get<String>("date")?.let(LocalDate::parse) ?: now().toLocalDate()

    private val effects = Channel<TodayEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    val uiState: StateFlow<TodayUiState> = combine(
        repository.observeEditable(date),
        repository.observeVisibleCategories(),
        projectRepository.observeActive(),
    ) { editable: EditableDay?, categories, projects ->
        val snapshot = editable?.snapshot ?: DaySnapshot(date = date)
        TodayUiState(
            date = date,
            day = snapshot,
            activityRows = editable?.activityRows.orEmpty(),
            fieldJobRows = editable?.fieldJobRows.orEmpty(),
            categories = categories,
            projects = projects,
            reportText = if (snapshot.dayType == DayType.WORK) ReportBuilder.daily(snapshot) else "",
            status = snapshot.status(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(date, DaySnapshot(date)))

    private fun nowMinutes(): Int = now().toLocalTime().toSecondOfDay() / 60

    fun arriveNow() = viewModelScope.launch { repository.setArrival(date, nowMinutes(), TimeSource.MANUAL) }
    fun leaveNow() = viewModelScope.launch { repository.setDeparture(date, nowMinutes(), TimeSource.MANUAL) }
    fun setArrival(minutes: Int) = viewModelScope.launch { repository.setArrival(date, minutes, TimeSource.MANUAL) }
    fun setDeparture(minutes: Int) = viewModelScope.launch { repository.setDeparture(date, minutes, TimeSource.MANUAL) }

    /** Back to "—:—" — a mis-tapped הגעתי/יצאתי must be undoable, not only editable. */
    fun clearArrival() = viewModelScope.launch { repository.clearArrival(date) }
    fun clearDeparture() = viewModelScope.launch { repository.clearDeparture(date) }

    fun toggleDayType(type: DayType) = viewModelScope.launch {
        val current = uiState.value.day.dayType
        repository.setDayType(date, if (current == type) DayType.WORK else type)
    }

    /** An activity cannot exist without a project (v1.2) — the screen picks one first. */
    fun addActivity(categoryId: Long, projectId: Long) = viewModelScope.launch {
        repository.addActivity(date, categoryId, projectId)
    }
    fun updateActivity(row: ActivityRow) = viewModelScope.launch { repository.updateActivity(row.toEntity()) }
    fun removeActivity(id: Long) = viewModelScope.launch { repository.removeActivity(date, id) }

    fun addFieldJob(title: String, location: String?, startMin: Int?, endMin: Int?) = viewModelScope.launch {
        if (title.isNotBlank()) repository.addFieldJob(date, FieldJob(title.trim(), location?.trim()?.ifBlank { null }, startMin, endMin))
    }

    fun setNotes(notes: String) = viewModelScope.launch { repository.setNotes(date, notes) }

    /** Spec §6.4/§2.4 v0.5: styled PDF + text caption; mark reported on launch. */
    fun share() = viewModelScope.launch {
        val state = uiState.value
        if (state.isSpecialDay || !state.day.hasData) return@launch
        val pdf = reportPdf.render(state.day)
        repository.markReported(date)
        effects.send(TodayEffect.LaunchShare(pdf, state.reportText))
    }
}

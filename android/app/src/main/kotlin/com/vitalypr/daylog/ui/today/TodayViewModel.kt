package com.vitalypr.daylog.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitalypr.daylog.data.db.CategoryEntity
import com.vitalypr.daylog.data.db.ProjectEntity
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.data.repo.EditableDay
import com.vitalypr.daylog.data.repo.ProjectRepository
import com.vitalypr.daylog.data.repo.SessionRow
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.model.ActivityDuration
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayStatus
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeBudget
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.model.status
import com.vitalypr.daylog.domain.report.ReportBuilder
import com.vitalypr.daylog.domain.stats.StatsCalculator
import com.vitalypr.daylog.reporting.DailyPdfRenderer
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
    val sessionRows: List<SessionRow> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val reportText: String = "",
    val status: DayStatus = DayStatus.EMPTY,
) {
    val isSpecialDay: Boolean get() = day.dayType != DayType.WORK

    /** Worked vs. described, for the whole day — the screen's "how much is left to fill". */
    val budget: TimeBudget get() = day.budget()

    /** Worked minutes per mode; drives the "בסיס 4:00 · בית 2:00" line. */
    val modeTotals: Map<WorkMode, Int> get() = StatsCalculator.dayMinutes(day).byMode
}

sealed interface TodayEffect {
    /** Screen launches the PDF share intent; day was already marked reported. */
    data class LaunchShare(val pdf: java.io.File, val caption: String) : TodayEffect
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: DayRepository,
    private val projectRepository: ProjectRepository,
    private val reportPdf: DailyPdfRenderer,
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
            sessionRows = editable?.sessionRows.orEmpty(),
            categories = categories,
            projects = projects,
            reportText = if (snapshot.dayType == DayType.WORK) ReportBuilder.daily(snapshot) else "",
            status = snapshot.status(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(date, DaySnapshot(date)))

    private fun nowMinutes(): Int = now().toLocalTime().toSecondOfDay() / 60

    // --- sessions -----------------------------------------------------------

    /**
     * Adds a stretch of work in [mode]. On today it starts now — that is the
     * one-tap path ("I've just started working from home"); on a past day it
     * opens empty, because "now" would be a lie there.
     */
    fun addSession(mode: WorkMode) = viewModelScope.launch {
        val startNow = date == now().toLocalDate()
        repository.addSession(date, mode, startMin = if (startNow) nowMinutes() else null)
    }

    fun removeSession(sessionId: Long) = viewModelScope.launch { repository.removeSession(date, sessionId) }

    /**
     * Manual edits always carry MANUAL source and clear the amber short-visit
     * doubt. Everything is addressed by id and re-read inside the repository, so
     * two quick edits (start then end) can never write through a stale row.
     */
    fun setSessionStart(sessionId: Long, minutes: Int?) = viewModelScope.launch {
        repository.editSession(date, sessionId) {
            it.copy(startMin = minutes, startSource = TimeSource.MANUAL.name, startUncertain = false)
        }
    }

    fun setSessionEnd(sessionId: Long, minutes: Int?) = viewModelScope.launch {
        repository.editSession(date, sessionId) {
            it.copy(endMin = minutes, endSource = TimeSource.MANUAL.name)
        }
    }

    fun startNow(sessionId: Long) = setSessionStart(sessionId, nowMinutes())
    fun endNow(sessionId: Long) = setSessionEnd(sessionId, nowMinutes())

    fun setSessionTitle(sessionId: Long, title: String) = viewModelScope.launch {
        repository.editSession(date, sessionId) { it.copy(title = title) }
    }

    // --- day-level ----------------------------------------------------------

    fun toggleDayType(type: DayType) = viewModelScope.launch {
        val current = uiState.value.day.dayType
        repository.setDayType(date, if (current == type) DayType.WORK else type)
    }

    fun setNotes(notes: String) = viewModelScope.launch { repository.setNotes(date, notes) }

    // --- activities ---------------------------------------------------------

    /**
     * An activity cannot exist without a project (v1.2) or outside a session
     * (v2.0) — the screen supplies both before this is called.
     */
    fun addActivity(sessionId: Long, categoryId: Long, projectId: Long) = viewModelScope.launch {
        repository.addActivity(sessionId, categoryId, projectId)
    }

    fun setActivityNote(id: Long, note: String) = viewModelScope.launch {
        repository.editActivity(id) { it.copy(note = note) }
    }

    fun setActivityResult(id: Long, result: String) = viewModelScope.launch {
        repository.editActivity(id) { it.copy(result = result) }
    }

    fun setActivityProject(id: Long, projectId: Long) = viewModelScope.launch {
        repository.editActivity(id) { it.copy(projectId = projectId) }
    }

    /** Half-hour steps (spec F4 v0.9); the stored value is the one stepped. */
    fun stepActivityDuration(id: Long, up: Boolean) = viewModelScope.launch {
        repository.editActivity(id) {
            it.copy(
                durationMin = if (up) {
                    ActivityDuration.increase(it.durationMin)
                } else {
                    ActivityDuration.decrease(it.durationMin)
                },
            )
        }
    }

    fun removeActivity(id: Long) = viewModelScope.launch { repository.removeActivity(id) }

    /** Spec §6.4/§2.4 v0.5: styled PDF + text caption; mark reported on launch. */
    fun share() = viewModelScope.launch {
        val state = uiState.value
        if (state.isSpecialDay || !state.day.hasData) return@launch
        val pdf = reportPdf.render(state.day)
        repository.markReported(date)
        effects.send(TodayEffect.LaunchShare(pdf, state.reportText))
    }
}

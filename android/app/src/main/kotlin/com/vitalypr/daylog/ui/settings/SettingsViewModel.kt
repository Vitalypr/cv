package com.vitalypr.daylog.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitalypr.daylog.data.export.Exporter
import com.vitalypr.daylog.data.settings.Settings
import com.vitalypr.daylog.data.settings.SettingsRepository
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.geofence.GeofenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsEffect {
    data class ShareFile(val file: File, val mime: String) : SettingsEffect
    data object RequestLocationPermission : SettingsEffect
    data class Toast(val message: String) : SettingsEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val geofenceManager: GeofenceManager,
    private val exporter: Exporter,
    private val officeLocator: com.vitalypr.daylog.geofence.OfficeLocator,
    @Now private val now: () -> LocalDateTime,
) : ViewModel() {

    private val effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun toggleWorkDay(day: DayOfWeek) = viewModelScope.launch {
        val current = settings.value.workDays
        settingsRepository.setWorkDays(if (day in current) current - day else current + day)
    }

    fun setReportTime(minutes: Int) = viewModelScope.launch {
        settingsRepository.setReportTime(minutes)
    }

    fun setGeofenceEnabled(enabled: Boolean, hasPermission: Boolean) = viewModelScope.launch {
        if (enabled && !hasPermission) {
            effects.send(SettingsEffect.RequestLocationPermission)
            return@launch
        }
        settingsRepository.setGeofenceEnabled(enabled)
        geofenceManager.sync()
    }

    fun setSilent(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSilentGeofence(enabled)
    }

    /** One-shot location fix — no Maps SDK, no network (spec §6.6). */
    fun captureOffice() = viewModelScope.launch {
        val fix = officeLocator.currentLocation()
        if (fix == null) {
            effects.send(SettingsEffect.Toast("לא נמצא מיקום — ודא ש־GPS פעיל"))
        } else {
            settingsRepository.setOffice(fix.first, fix.second, settings.value.officeRadiusM)
            geofenceManager.sync()
            effects.send(SettingsEffect.Toast("מיקום המשרד נשמר"))
        }
    }

    fun exportJson() = export("daylog-export.json", "application/json") { from, to -> exporter.exportJson(from, to) }
    fun exportCsv() = export("daylog-export.csv", "text/csv") { from, to -> exporter.exportCsv(from, to) }

    private fun export(name: String, mime: String, build: suspend (LocalDate, LocalDate) -> String) =
        viewModelScope.launch {
            val today = now().toLocalDate()
            val content = build(today.minusYears(2), today)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, name).apply { writeText(content) }
            effects.send(SettingsEffect.ShareFile(file, mime))
        }
}

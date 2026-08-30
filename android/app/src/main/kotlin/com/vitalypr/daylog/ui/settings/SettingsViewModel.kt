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
    /** Android 11+: background location can only be granted from system settings. */
    data object OpenLocationSettings : SettingsEffect
    data class Toast(val message: String) : SettingsEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val geofenceManager: GeofenceManager,
    private val exporter: Exporter,
    private val officeLocator: com.vitalypr.daylog.geofence.OfficeLocator,
    private val jobLocationRepository: com.vitalypr.daylog.data.repo.JobLocationRepository,
    private val geofenceLog: com.vitalypr.daylog.geofence.GeofenceLog,
    private val projectRepository: com.vitalypr.daylog.data.repo.ProjectRepository,
    private val backupRepository: com.vitalypr.daylog.data.backup.BackupRepository,
    private val reminderScheduler: com.vitalypr.daylog.reminder.ReminderScheduler,
    private val widgetRefresher: com.vitalypr.daylog.widget.DayWidgetRefresher,
    @Now private val now: () -> LocalDateTime,
) : ViewModel() {

    val jobLocations: StateFlow<List<com.vitalypr.daylog.data.db.JobLocationEntity>> =
        jobLocationRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projects: StateFlow<List<com.vitalypr.daylog.data.db.ProjectEntity>> =
        projectRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addProject(name: String) = viewModelScope.launch {
        if (projectRepository.add(name) >= 0) effects.send(SettingsEffect.Toast("הפרויקט נוסף"))
    }

    fun removeProject(project: com.vitalypr.daylog.data.db.ProjectEntity) = viewModelScope.launch {
        // A project with logged work is archived, never deleted — history must keep rendering.
        val deleted = projectRepository.remove(project)
        effects.send(SettingsEffect.Toast(if (deleted) "הפרויקט נמחק" else "הפרויקט הועבר לארכיון (יש עליו רישומים)"))
    }

    fun restoreProject(project: com.vitalypr.daylog.data.db.ProjectEntity) =
        viewModelScope.launch { projectRepository.restore(project) }

    /** Last transitions and what each one did — the field-diagnosis trail. */
    val geofenceEvents: StateFlow<List<String>> = geofenceLog.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setOfficeRadius(meters: Int) = viewModelScope.launch {
        settingsRepository.setOfficeRadius(meters)
        geofenceManager.resync() // the fence has to be rebuilt at the new size
    }

    /** Live registration status — the screen renders it so failures are never silent. */
    val geofenceStatus: StateFlow<com.vitalypr.daylog.geofence.GeofenceStatus> = geofenceManager.status

    private fun hasFine(): Boolean = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun hasBackground(): Boolean = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** After any tracking-related change: sync and walk the user through what's missing. */
    private suspend fun syncAndGuide() {
        geofenceManager.sync()
        when (geofenceManager.status.value) {
            is com.vitalypr.daylog.geofence.GeofenceStatus.NoBackgroundPermission -> {
                effects.send(SettingsEffect.Toast("להצעות אוטומטיות בחר ׳לאפשר כל הזמן׳ בהרשאת המיקום"))
                effects.send(SettingsEffect.OpenLocationSettings)
            }
            is com.vitalypr.daylog.geofence.GeofenceStatus.NoPermission ->
                effects.send(SettingsEffect.RequestLocationPermission)
            else -> Unit
        }
    }

    /** Captures the current position as a named job location (2 km fence, spec §6.6b). */
    fun addJobLocation(name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val fix = officeLocator.currentLocation()
        if (fix == null) {
            effects.send(SettingsEffect.Toast("לא נמצא מיקום — ודא ש־GPS פעיל"))
        } else {
            jobLocationRepository.add(name, fix.first, fix.second)
            enableTrackingForNewLocation()
        }
    }

    /** Adding a location IS the intent to track it — enable + guide through permissions. */
    private suspend fun enableTrackingForNewLocation() {
        if (!settings.value.geofenceEnabled && hasFine()) {
            settingsRepository.setGeofenceEnabled(true)
        }
        syncAndGuide()
        effects.send(SettingsEffect.Toast("מיקום העבודה נשמר"))
    }

    fun removeJobLocation(location: com.vitalypr.daylog.data.db.JobLocationEntity) = viewModelScope.launch {
        jobLocationRepository.remove(location)
        geofenceManager.sync()
    }

    fun refreshGeofences() = viewModelScope.launch { geofenceManager.sync() }

    /** Map-picked coordinates (OSM pin picker). */
    fun setOfficeAt(lat: Double, lon: Double) = viewModelScope.launch {
        settingsRepository.setOffice(lat, lon, settings.value.officeRadiusM)
        geofenceManager.sync()
        effects.send(SettingsEffect.Toast("מיקום המשרד נשמר"))
    }

    fun addJobLocationAt(name: String, lat: Double, lon: Double) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        jobLocationRepository.add(name, lat, lon)
        enableTrackingForNewLocation()
    }

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
        if (enabled) syncAndGuide() else geofenceManager.sync()
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

    /** Everything the app owns, in one file the user can mail to themselves. */
    fun exportBackup() = viewModelScope.launch {
        val json = backupRepository.exportJson()
        val stamp = now().toLocalDate()
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "daylog-backup-$stamp.json").apply { writeText(json) }
        effects.send(SettingsEffect.ShareFile(file, "application/json"))
    }

    /**
     * Replaces everything with the contents of [uri]. Destructive by design —
     * the screen confirms first — and it re-arms the systems that depend on
     * settings, since the restored values may point somewhere else entirely.
     */
    fun restoreBackup(uri: android.net.Uri) = viewModelScope.launch {
        val json = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (json.isNullOrBlank()) {
            effects.send(SettingsEffect.Toast("לא ניתן לקרוא את הקובץ"))
            return@launch
        }
        runCatching { backupRepository.restoreJson(json) }
            .onSuccess {
                reminderScheduler.scheduleNext()
                geofenceManager.resync()
                widgetRefresher.refresh()
                effects.send(SettingsEffect.Toast("הגיבוי שוחזר"))
            }
            .onFailure { effects.send(SettingsEffect.Toast(it.message ?: "שחזור נכשל")) }
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

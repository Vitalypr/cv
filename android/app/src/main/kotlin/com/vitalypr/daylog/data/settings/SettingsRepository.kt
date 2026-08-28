package com.vitalypr.daylog.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    /** Israel default: Sunday–Thursday (spec S4). */
    val workDays: Set<DayOfWeek> = setOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    ),
    val reportTimeMin: Int = 17 * 60 + 45,
    val geofenceEnabled: Boolean = false,
    /**
     * Record arrivals/departures as they happen instead of only suggesting them.
     * Default ON since v1.1: a suggestion that is never tapped is data lost, and
     * a GEOFENCE write still never overwrites a MANUAL value.
     */
    val silentGeofence: Boolean = true,
    val officeLat: Double? = null,
    val officeLon: Double? = null,
    val officeRadiusM: Int = 150,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Read-only view — system engines depend on this, tests fake it. */
interface SettingsSource {
    val settings: Flow<Settings>
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsSource {
    private object Keys {
        val workDays = stringPreferencesKey("work_days") // comma-joined DayOfWeek values
        val reportTime = intPreferencesKey("report_time_min")
        val geofenceEnabled = booleanPreferencesKey("geofence_enabled")
        val silentGeofence = booleanPreferencesKey("silent_geofence")
        val officeLat = doublePreferencesKey("office_lat")
        val officeLon = doublePreferencesKey("office_lon")
        val officeRadius = intPreferencesKey("office_radius_m")
    }

    override val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val defaults = Settings()
        Settings(
            workDays = p[Keys.workDays]
                ?.split(',')?.filter { it.isNotBlank() }?.map { DayOfWeek.of(it.toInt()) }?.toSet()
                ?: defaults.workDays,
            reportTimeMin = p[Keys.reportTime] ?: defaults.reportTimeMin,
            geofenceEnabled = p[Keys.geofenceEnabled] ?: defaults.geofenceEnabled,
            silentGeofence = p[Keys.silentGeofence] ?: defaults.silentGeofence,
            officeLat = p[Keys.officeLat],
            officeLon = p[Keys.officeLon],
            officeRadiusM = p[Keys.officeRadius] ?: defaults.officeRadiusM,
        )
    }

    suspend fun setWorkDays(days: Set<DayOfWeek>) = context.dataStore.edit {
        it[Keys.workDays] = days.joinToString(",") { d -> d.value.toString() }
    }

    suspend fun setReportTime(minutes: Int) = context.dataStore.edit { it[Keys.reportTime] = minutes }

    suspend fun setOfficeRadius(meters: Int) = context.dataStore.edit { it[Keys.officeRadius] = meters }

    suspend fun setGeofenceEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.geofenceEnabled] = enabled }

    suspend fun setSilentGeofence(enabled: Boolean) = context.dataStore.edit { it[Keys.silentGeofence] = enabled }

    suspend fun setOffice(lat: Double, lon: Double, radiusM: Int) = context.dataStore.edit {
        it[Keys.officeLat] = lat
        it[Keys.officeLon] = lon
        it[Keys.officeRadius] = radiusM
    }
}

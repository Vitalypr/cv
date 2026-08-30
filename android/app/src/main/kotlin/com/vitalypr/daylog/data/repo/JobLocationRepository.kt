package com.vitalypr.daylog.data.repo

import com.vitalypr.daylog.data.db.JobLocationDao
import com.vitalypr.daylog.data.db.JobLocationEntity
import com.vitalypr.daylog.domain.geo.GeofenceRules
import com.vitalypr.daylog.domain.model.DayType
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Job-location tracking per spec §6.6b: wide (2 km) fences around client sites.
 *
 * v2.0: a visit is a FIELD **work session**, exactly like a visit to the base —
 * ENTER opens one, EXIT closes it, and a second visit the same day is a second
 * session rather than an overwrite of the first. Everything is scoped to the
 * location, so two sites on the same day track independently.
 */
@Singleton
class JobLocationRepository @Inject constructor(
    private val jobLocationDao: JobLocationDao,
    private val dayRepository: DayRepository,
) {

    fun observeAll(): Flow<List<JobLocationEntity>> = jobLocationDao.observeAll()

    suspend fun activeLocations(): List<JobLocationEntity> = jobLocationDao.activeLocations()

    suspend fun add(name: String, lat: Double, lon: Double, radiusM: Int = DEFAULT_RADIUS_M): Long =
        jobLocationDao.insert(JobLocationEntity(name = name.trim(), lat = lat, lon = lon, radiusM = radiusM))

    suspend fun remove(location: JobLocationEntity) = jobLocationDao.delete(location)

    /** ENTER opens a visit named after the location, unless one is already running. */
    suspend fun onEnter(locationId: Long, date: LocalDate, eventMin: Int) {
        val location = jobLocationDao.byId(locationId) ?: return
        if (isSpecialDay(date)) return // חופש/חג — nothing is tracked (S4)
        dayRepository.startJobSession(date, locationId, location.name, eventMin)
    }

    /**
     * EXIT closes the running visit. A stay under [GeofenceRules.MIN_VISIT] is a
     * drive-past, not a client visit — 2 km fences are crossed in minutes — so no
     * leaving time is invented; the arrival stands but is flagged amber
     * ("ביקור קצר"), the same rule the office fence follows.
     */
    suspend fun onExit(locationId: Long, date: LocalDate, eventMin: Int, visitStartMin: Int) {
        jobLocationDao.byId(locationId) ?: return
        if (isSpecialDay(date)) return
        val open = dayRepository.openJobSession(date, locationId) ?: return
        if (eventMin - visitStartMin < GeofenceRules.MIN_VISIT.toMinutes()) {
            dayRepository.setStartUncertain(open.id, date, true)
            return
        }
        dayRepository.endJobSession(date, locationId, eventMin)
        dayRepository.setStartUncertain(open.id, date, false)
    }

    private suspend fun isSpecialDay(date: LocalDate): Boolean =
        dayRepository.getDay(date)?.dayType?.let { it != DayType.WORK } ?: false

    companion object {
        const val DEFAULT_RADIUS_M = 2000
    }
}

package com.vitalypr.daylog.data.repo

import com.vitalypr.daylog.data.db.DayDao
import com.vitalypr.daylog.data.db.FieldJobEntity
import com.vitalypr.daylog.data.db.JobLocationDao
import com.vitalypr.daylog.data.db.JobLocationEntity
import com.vitalypr.daylog.data.db.WorkDayEntity
import com.vitalypr.daylog.domain.geo.GeofenceRules
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Job-location tracking per spec §6.6b: wide (2 km) fences around client sites.
 * First ENTER of a day writes the field job's suggested start; EVERY EXIT
 * overwrites the suggested end — so a lunch break is automatically superseded
 * by the final exit, leaving first-arrive/last-leave. Suggestions live in their
 * own columns; manual times always win at the mapping layer and are never touched.
 */
@Singleton
class JobLocationRepository @Inject constructor(
    private val jobLocationDao: JobLocationDao,
    private val dayDao: DayDao,
) {

    fun observeAll(): Flow<List<JobLocationEntity>> = jobLocationDao.observeAll()

    suspend fun activeLocations(): List<JobLocationEntity> = jobLocationDao.activeLocations()

    suspend fun add(name: String, lat: Double, lon: Double, radiusM: Int = DEFAULT_RADIUS_M): Long =
        jobLocationDao.insert(JobLocationEntity(name = name.trim(), lat = lat, lon = lon, radiusM = radiusM))

    suspend fun remove(location: JobLocationEntity) = jobLocationDao.delete(location)

    /** First ENTER of the day: auto-create the field job / fill the suggested start. */
    suspend fun onEnter(locationId: Long, date: LocalDate, eventMin: Int) {
        val location = jobLocationDao.byId(locationId) ?: return
        if (isSpecialDay(date)) return // חופש/חג — nothing is tracked (S4)
        ensureDay(date)
        val existing = dayDao.fieldJobForLocation(date.toString(), locationId)
        when {
            existing == null -> dayDao.insertFieldJob(
                FieldJobEntity(
                    date = date.toString(), title = location.name,
                    jobLocationId = locationId, suggestedStartMin = eventMin,
                ),
            )
            existing.suggestedStartMin == null -> dayDao.updateFieldJob(existing.copy(suggestedStartMin = eventMin))
            else -> Unit // not the first enter today (e.g., back from lunch) — start stays
        }
    }

    /**
     * Every EXIT overwrites the suggested end — last exit of the day wins.
     *
     * No row means we never saw the entry, so there is nothing to close: inventing
     * a field job from an exit alone produced entries with an end and no start.
     * A visit shorter than [GeofenceRules.MIN_JOB_DWELL_MINUTES] was a drive-past
     * (2 km fences are crossed in minutes) and the untouched suggestion is dropped.
     */
    suspend fun onExit(locationId: Long, date: LocalDate, eventMin: Int) {
        val location = jobLocationDao.byId(locationId) ?: return
        if (isSpecialDay(date)) return // חופש/חג — nothing is tracked (S4)
        val existing = dayDao.fieldJobForLocation(date.toString(), locationId) ?: return

        val start = existing.suggestedStartMin
        if (start != null && eventMin - start < GeofenceRules.MIN_JOB_DWELL_MINUTES &&
            isUntouched(existing, location.name)
        ) {
            dayDao.deleteFieldJob(existing)
            return
        }
        dayDao.updateFieldJob(existing.copy(suggestedEndMin = eventMin))
    }

    /** Only ever discard a row the user has not shaped in any way. */
    private fun isUntouched(job: FieldJobEntity, locationName: String): Boolean =
        job.startMin == null && job.endMin == null &&
            job.locationText.isNullOrBlank() && job.title == locationName

    private suspend fun isSpecialDay(date: LocalDate): Boolean =
        dayDao.getDay(date.toString())?.day?.dayType?.let { it != "WORK" } ?: false

    private suspend fun ensureDay(date: LocalDate) {
        val key = date.toString()
        if (!dayDao.dayExists(key)) dayDao.upsertDay(WorkDayEntity(date = key))
    }

    companion object {
        const val DEFAULT_RADIUS_M = 2000
    }
}

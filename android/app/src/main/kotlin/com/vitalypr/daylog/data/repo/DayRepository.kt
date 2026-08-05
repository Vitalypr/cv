package com.vitalypr.daylog.data.repo

import com.vitalypr.daylog.data.db.ActivityEntity
import com.vitalypr.daylog.data.db.CategoryDao
import com.vitalypr.daylog.data.db.CategoryEntity
import com.vitalypr.daylog.data.db.DayDao
import com.vitalypr.daylog.data.db.DayWithEntries
import com.vitalypr.daylog.data.db.FieldJobEntity
import com.vitalypr.daylog.data.db.WorkDayEntity
import com.vitalypr.daylog.domain.model.ActivityEntry
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.FieldJob
import com.vitalypr.daylog.domain.model.TimeSource
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single write/read gateway for day data. Enforces the source rules from spec §6.6:
 * a GEOFENCE-sourced write never overwrites a MANUAL value (callers pass the source).
 * Room entities never leak above this class — everything maps to domain models.
 */
@Singleton
class DayRepository @Inject constructor(
    private val dayDao: DayDao,
    private val categoryDao: CategoryDao,
    private val clock: () -> Instant = Instant::now,
) {

    fun observeDay(date: LocalDate): Flow<DaySnapshot?> =
        dayDao.observeDay(date.toString()).map { it?.toSnapshot() }

    suspend fun getDay(date: LocalDate): DaySnapshot? = dayDao.getDay(date.toString())?.toSnapshot()

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DaySnapshot>> =
        dayDao.observeRange(from.toString(), to.toString()).map { list -> list.map { it.toSnapshot() } }

    suspend fun getRange(from: LocalDate, to: LocalDate): List<DaySnapshot> =
        dayDao.getRange(from.toString(), to.toString()).map { it.toSnapshot() }

    fun observeVisibleCategories(): Flow<List<CategoryEntity>> = categoryDao.observeVisible()

    /**
     * Sets arrival. Returns false when rejected: GEOFENCE never overwrites MANUAL,
     * and GEOFENCE prompts only apply when no arrival is set (spec §5.5).
     */
    suspend fun setArrival(date: LocalDate, minutes: Int, source: TimeSource): Boolean {
        val existing = ensureDay(date)
        if (source == TimeSource.GEOFENCE && existing.arrivalMin != null) return false
        upsertEdited(existing.copy(arrivalMin = minutes, arrivalSource = source.name))
        return true
    }

    /** Sets/updates departure. GEOFENCE may update a previous GEOFENCE value (last exit wins) but never MANUAL. */
    suspend fun setDeparture(date: LocalDate, minutes: Int, source: TimeSource): Boolean {
        val existing = ensureDay(date)
        if (source == TimeSource.GEOFENCE && existing.departureMin != null &&
            existing.departureSource == TimeSource.MANUAL.name
        ) return false
        upsertEdited(existing.copy(departureMin = minutes, departureSource = source.name))
        return true
    }

    suspend fun clearArrival(date: LocalDate) =
        upsertEdited(ensureDay(date).copy(arrivalMin = null))

    suspend fun clearDeparture(date: LocalDate) =
        upsertEdited(ensureDay(date).copy(departureMin = null))

    suspend fun setNotes(date: LocalDate, notes: String) =
        upsertEdited(ensureDay(date).copy(notes = notes))

    suspend fun setDayType(date: LocalDate, type: DayType) =
        upsertEdited(ensureDay(date).copy(dayType = type.name))

    suspend fun addActivity(date: LocalDate, categoryId: Long): Long {
        val day = ensureDay(date)
        markEdited(day)
        val order = (dayDao.getDay(day.date)?.activities?.maxOfOrNull { it.activity.sortOrder } ?: -1) + 1
        return dayDao.insertActivity(ActivityEntity(date = day.date, categoryId = categoryId, sortOrder = order))
    }

    suspend fun updateActivity(activity: ActivityEntity) {
        dayDao.getDay(activity.date)?.let { markEdited(it.day) }
        dayDao.updateActivity(activity)
    }

    suspend fun removeActivity(date: LocalDate, id: Long) {
        dayDao.getDay(date.toString())?.let { markEdited(it.day) }
        dayDao.deleteActivity(id)
    }

    suspend fun addFieldJob(date: LocalDate, job: FieldJob): Long {
        val day = ensureDay(date)
        markEdited(day)
        return dayDao.insertFieldJob(
            FieldJobEntity(
                date = day.date, title = job.title, locationText = job.locationText,
                startMin = job.startMin, endMin = job.endMin,
            ),
        )
    }

    suspend fun updateFieldJob(job: FieldJobEntity) {
        dayDao.getDay(job.date)?.let { markEdited(it.day) }
        dayDao.updateFieldJob(job)
    }

    suspend fun removeFieldJob(job: FieldJobEntity) {
        dayDao.getDay(job.date)?.let { markEdited(it.day) }
        dayDao.deleteFieldJob(job)
    }

    /** Marks the day reported now; re-sending overwrites the timestamp and clears the edited flag (spec F9). */
    suspend fun markReported(date: LocalDate) {
        val day = ensureDay(date)
        dayDao.upsertDay(day.copy(reportedAt = clock().toEpochMilli(), editedAfterReport = false))
    }

    private suspend fun ensureDay(date: LocalDate): WorkDayEntity {
        val key = date.toString()
        return dayDao.getDay(key)?.day ?: WorkDayEntity(date = key).also { dayDao.upsertDay(it) }
    }

    /** Any content edit after a report flips the edited flag (drives "נשלח (עודכן)"). */
    private suspend fun upsertEdited(day: WorkDayEntity) {
        dayDao.upsertDay(day.copy(editedAfterReport = day.reportedAt != null))
    }

    private suspend fun markEdited(day: WorkDayEntity) {
        if (day.reportedAt != null && !day.editedAfterReport) {
            dayDao.upsertDay(day.copy(editedAfterReport = true))
        }
    }
}

internal fun DayWithEntries.toSnapshot(): DaySnapshot = DaySnapshot(
    date = LocalDate.parse(day.date),
    arrivalMin = day.arrivalMin,
    departureMin = day.departureMin,
    arrivalSource = TimeSource.valueOf(day.arrivalSource),
    departureSource = TimeSource.valueOf(day.departureSource),
    dayType = DayType.valueOf(day.dayType),
    notes = day.notes,
    fieldJobs = fieldJobs.map { FieldJob(it.title, it.locationText, it.startMin, it.endMin) },
    activities = activities
        .sortedBy { it.activity.sortOrder }
        .map { ActivityEntry(it.category.name, it.activity.startMin, it.activity.endMin, it.activity.note, it.activity.result) },
    reported = day.reportedAt != null,
    editedAfterReport = day.editedAfterReport,
)

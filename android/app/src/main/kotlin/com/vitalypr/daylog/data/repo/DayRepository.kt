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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val writeLock = Mutex()

    fun observeDay(date: LocalDate): Flow<DaySnapshot?> =
        dayDao.observeDay(date.toString()).map { it?.toSnapshot() }

    /** Editable view with row ids for the day editor UI. */
    fun observeEditable(date: LocalDate): Flow<EditableDay?> =
        dayDao.observeDay(date.toString()).map { d ->
            d?.let {
                EditableDay(
                    snapshot = it.toSnapshot(),
                    activityRows = it.activities.sortedBy { a -> a.activity.sortOrder }.map { a ->
                        ActivityRow(
                            id = a.activity.id, categoryId = a.activity.categoryId,
                            category = a.category.name, durationMin = a.activity.durationMin,
                            note = a.activity.note, result = a.activity.result,
                            date = date, sortOrder = a.activity.sortOrder,
                        )
                    },
                    fieldJobRows = it.fieldJobs.map { j ->
                        FieldJobRow(
                            id = j.id, title = j.title, locationText = j.locationText,
                            startMin = j.startMin ?: j.suggestedStartMin,
                            endMin = j.endMin ?: j.suggestedEndMin,
                            isStartSuggested = j.startMin == null && j.suggestedStartMin != null,
                            isEndSuggested = j.endMin == null && j.suggestedEndMin != null,
                        )
                    },
                )
            }
        }

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
    suspend fun setArrival(date: LocalDate, minutes: Int, source: TimeSource): Boolean = edit(date) { existing ->
        if (source == TimeSource.GEOFENCE && existing.arrivalMin != null) return@edit false
        upsertEdited(existing.copy(arrivalMin = minutes, arrivalSource = source.name))
        true
    }

    /** Sets/updates departure. GEOFENCE may update a previous GEOFENCE value (last exit wins) but never MANUAL. */
    suspend fun setDeparture(date: LocalDate, minutes: Int, source: TimeSource): Boolean = edit(date) { existing ->
        if (source == TimeSource.GEOFENCE && existing.departureMin != null &&
            existing.departureSource == TimeSource.MANUAL.name
        ) return@edit false
        upsertEdited(existing.copy(departureMin = minutes, departureSource = source.name))
        true
    }

    /**
     * Back to unset (F1: a logged time must be erasable, not only editable).
     * The source resets to MANUAL so a cleared value can be re-suggested by a
     * later geofence event instead of staying locked by the old MANUAL source.
     */
    suspend fun clearArrival(date: LocalDate) = edit(date) {
        upsertEdited(it.copy(arrivalMin = null, arrivalSource = TimeSource.MANUAL.name))
    }

    suspend fun clearDeparture(date: LocalDate) = edit(date) {
        upsertEdited(it.copy(departureMin = null, departureSource = TimeSource.MANUAL.name))
    }

    suspend fun setNotes(date: LocalDate, notes: String) = edit(date) { upsertEdited(it.copy(notes = notes)) }

    suspend fun setDayType(date: LocalDate, type: DayType) = edit(date) { upsertEdited(it.copy(dayType = type.name)) }

    suspend fun addActivity(date: LocalDate, categoryId: Long): Long = edit(date) { day ->
        markEdited(day)
        val order = (dayDao.getDay(day.date)?.activities?.maxOfOrNull { it.activity.sortOrder } ?: -1) + 1
        dayDao.insertActivity(ActivityEntity(date = day.date, categoryId = categoryId, sortOrder = order))
    }

    suspend fun updateActivity(activity: ActivityEntity) = writeLock.withLock {
        dayDao.getDay(activity.date)?.let { markEdited(it.day) }
        dayDao.updateActivity(activity)
    }

    suspend fun removeActivity(date: LocalDate, id: Long) = writeLock.withLock {
        dayDao.getDay(date.toString())?.let { markEdited(it.day) }
        dayDao.deleteActivity(id)
    }

    suspend fun addFieldJob(date: LocalDate, job: FieldJob): Long = edit(date) { day ->
        markEdited(day)
        dayDao.insertFieldJob(
            FieldJobEntity(
                date = day.date, title = job.title, locationText = job.locationText,
                startMin = job.startMin, endMin = job.endMin,
            ),
        )
    }

    suspend fun updateFieldJob(job: FieldJobEntity) = writeLock.withLock {
        dayDao.getDay(job.date)?.let { markEdited(it.day) }
        dayDao.updateFieldJob(job)
    }

    suspend fun removeFieldJob(job: FieldJobEntity) = writeLock.withLock {
        dayDao.getDay(job.date)?.let { markEdited(it.day) }
        dayDao.deleteFieldJob(job)
    }

    /** Marks the day reported now; re-sending overwrites the timestamp and clears the edited flag (spec F9). */
    suspend fun markReported(date: LocalDate) = edit(date) { day ->
        dayDao.upsertDay(day.copy(reportedAt = clock().toEpochMilli(), editedAfterReport = false))
    }

    /**
     * Serializes read-modify-write on the day row. Without it two mutations issued
     * back-to-back (tap הגעתי, then יצאתי) can both read the pre-write row and the
     * second write silently drops the first. NOT reentrant — never call from inside.
     */
    private suspend fun <T> edit(date: LocalDate, block: suspend (WorkDayEntity) -> T): T =
        writeLock.withLock { block(ensureDay(date)) }

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

data class ActivityRow(
    val id: Long,
    val categoryId: Long,
    val category: String,
    /** Half-hour steps; null = not stated (spec F4). */
    val durationMin: Int?,
    val note: String,
    val result: String,
    val date: LocalDate,
    val sortOrder: Int,
) {
    fun toEntity() = ActivityEntity(
        id = id, date = date.toString(), categoryId = categoryId,
        durationMin = durationMin, note = note, result = result, sortOrder = sortOrder,
    )
}

data class FieldJobRow(
    val id: Long,
    val title: String,
    val locationText: String?,
    val startMin: Int?, // effective: manual if set, else suggested
    val endMin: Int?,
    val isStartSuggested: Boolean = false,
    val isEndSuggested: Boolean = false,
)

data class EditableDay(
    val snapshot: DaySnapshot,
    val activityRows: List<ActivityRow>,
    val fieldJobRows: List<FieldJobRow>,
)

internal fun DayWithEntries.toSnapshot(): DaySnapshot = DaySnapshot(
    date = LocalDate.parse(day.date),
    arrivalMin = day.arrivalMin,
    departureMin = day.departureMin,
    arrivalSource = TimeSource.valueOf(day.arrivalSource),
    departureSource = TimeSource.valueOf(day.departureSource),
    dayType = DayType.valueOf(day.dayType),
    notes = day.notes,
    // Effective times: MANUAL wins, else geofence suggestion (spec §6.6b).
    fieldJobs = fieldJobs.map {
        FieldJob(it.title, it.locationText, it.startMin ?: it.suggestedStartMin, it.endMin ?: it.suggestedEndMin)
    },
    activities = activities
        .sortedBy { it.activity.sortOrder }
        .map { ActivityEntry(it.category.name, it.activity.durationMin, it.activity.note, it.activity.result) },
    reported = day.reportedAt != null,
    editedAfterReport = day.editedAfterReport,
)

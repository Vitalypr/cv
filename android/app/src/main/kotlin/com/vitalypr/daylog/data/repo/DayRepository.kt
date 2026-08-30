package com.vitalypr.daylog.data.repo

import com.vitalypr.daylog.data.db.ActivityEntity
import com.vitalypr.daylog.data.db.CategoryDao
import com.vitalypr.daylog.data.db.CategoryEntity
import com.vitalypr.daylog.data.db.DayDao
import com.vitalypr.daylog.data.db.DayWithEntries
import com.vitalypr.daylog.data.db.WorkDayEntity
import com.vitalypr.daylog.data.db.WorkSessionEntity
import com.vitalypr.daylog.domain.model.ActivityEntry
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.model.WorkSession
import com.vitalypr.daylog.domain.time.WorkTimeStep
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single write/read gateway for day data. Worked time is a list of sessions
 * (v2.0); a GEOFENCE-sourced write never overwrites a MANUAL value. Room
 * entities never leak above this class — everything maps to domain models.
 */
@Singleton
class DayRepository @Inject constructor(
    private val dayDao: DayDao,
    private val categoryDao: CategoryDao,
    private val clock: () -> Instant = Instant::now,
) {

    private val writeLock = Mutex()

    /**
     * Worked time is booked in quarter hours (product-owner rule): a start rounds
     * down, an end rounds up. Applied here, at the single write gateway, so it
     * holds for every path — the office fence, the widget, the הגעתי/יצאתי
     * buttons and the time picker — instead of being re-implemented per caller.
     * Restore is untouched: it re-inserts stored rows in bulk, not through here.
     */
    private fun WorkSessionEntity.snapped(): WorkSessionEntity = copy(
        startMin = startMin?.let(WorkTimeStep::roundStart),
        endMin = endMin?.let(WorkTimeStep::roundEnd),
    )

    fun observeDay(date: LocalDate): Flow<DaySnapshot?> =
        dayDao.observeDay(date.toString()).map { it?.toSnapshot() }

    /** Editable view with row ids for the day editor UI. */
    fun observeEditable(date: LocalDate): Flow<EditableDay?> =
        dayDao.observeDay(date.toString()).map { it?.toEditable() }

    suspend fun getDay(date: LocalDate): DaySnapshot? = dayDao.getDay(date.toString())?.toSnapshot()

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DaySnapshot>> =
        dayDao.observeRange(from.toString(), to.toString()).map { list -> list.map { it.toSnapshot() } }

    suspend fun getRange(from: LocalDate, to: LocalDate): List<DaySnapshot> =
        dayDao.getRange(from.toString(), to.toString()).map { it.toSnapshot() }

    fun observeVisibleCategories(): Flow<List<CategoryEntity>> = categoryDao.observeVisible()

    // --- sessions -----------------------------------------------------------

    /** Adds a session the user created by hand. */
    suspend fun addSession(
        date: LocalDate,
        mode: WorkMode,
        startMin: Int? = null,
        endMin: Int? = null,
        title: String = "",
    ): Long = edit(date) { day ->
        markEdited(day)
        val order = (dayDao.sessionsOn(day.date).maxOfOrNull { it.sortOrder } ?: -1) + 1
        dayDao.insertSession(
            WorkSessionEntity(
                date = day.date, mode = mode.name, startMin = startMin, endMin = endMin,
                title = title.trim(), sortOrder = order,
            ).snapped(),
        )
    }

    suspend fun updateSession(session: WorkSessionEntity) = writeLock.withLock {
        dayDao.getDay(session.date)?.let { markEdited(it.day) }
        dayDao.updateSession(session.snapped())
    }

    /**
     * Edits one session by id, reading it fresh inside the lock. The UI holds
     * entities from a previous state emission, so editing through a captured copy
     * would silently undo whatever changed in between.
     */
    suspend fun editSession(
        date: LocalDate,
        sessionId: Long,
        block: (WorkSessionEntity) -> WorkSessionEntity,
    ): Boolean = edit(date) { day ->
        val current = dayDao.sessionsOn(day.date).firstOrNull { it.id == sessionId } ?: return@edit false
        markEdited(day)
        dayDao.updateSession(block(current).snapped())
        true
    }

    suspend fun removeSession(session: WorkSessionEntity) = writeLock.withLock {
        dayDao.getDay(session.date)?.let { markEdited(it.day) }
        dayDao.deleteSession(session)
    }

    suspend fun removeSession(date: LocalDate, sessionId: Long) = writeLock.withLock {
        val session = dayDao.sessionsOn(date.toString()).firstOrNull { it.id == sessionId } ?: return@withLock
        dayDao.getDay(session.date)?.let { markEdited(it.day) }
        dayDao.deleteSession(session)
    }

    suspend fun openSession(date: LocalDate, mode: WorkMode): WorkSessionEntity? =
        dayDao.openSession(date.toString(), mode.name)

    /**
     * Starts a session of [mode] unless one is already running (v2.0 replacement
     * for "set arrival"). Returns false when a session is already open, so a
     * duplicate geofence delivery cannot open a second one.
     */
    suspend fun startSession(
        date: LocalDate,
        mode: WorkMode,
        minutes: Int,
        source: TimeSource,
        title: String = "",
        jobLocationId: Long? = null,
    ): Boolean = edit(date) { day ->
        if (dayDao.openSession(day.date, mode.name) != null) return@edit false
        markEdited(day)
        val order = (dayDao.sessionsOn(day.date).maxOfOrNull { it.sortOrder } ?: -1) + 1
        dayDao.insertSession(
            WorkSessionEntity(
                date = day.date, mode = mode.name, startMin = minutes,
                title = title.trim(), startSource = source.name,
                jobLocationId = jobLocationId, sortOrder = order,
            ).snapped(),
        )
        true
    }

    /**
     * Closes the running session of [mode]. This is also what keeps a GEOFENCE
     * write off a value the user typed: a session the user already closed by
     * hand is no longer open, so there is nothing here to overwrite.
     */
    suspend fun endSession(date: LocalDate, mode: WorkMode, minutes: Int, source: TimeSource): Boolean =
        edit(date) { day ->
            val open = dayDao.openSession(day.date, mode.name) ?: return@edit false
            markEdited(day)
            dayDao.updateSession(open.copy(endMin = minutes, endSource = source.name).snapped())
            true
        }

    /**
     * Records a leaving time for [mode]: it closes the session that is running,
     * and when none is (the exit follows a visit we already closed) it moves the
     * last visit's end — spec §5.5's "last exit wins". A hand-typed end is never
     * moved by the geofence.
     */
    suspend fun recordDeparture(date: LocalDate, mode: WorkMode, minutes: Int, source: TimeSource): Boolean =
        edit(date) { day ->
            val target = dayDao.openSession(day.date, mode.name)
                ?: dayDao.sessionsOn(day.date).lastOrNull { it.mode == mode.name }
                ?: return@edit false
            if (source == TimeSource.GEOFENCE && target.endSource == TimeSource.MANUAL.name &&
                target.endMin != null
            ) {
                return@edit false
            }
            markEdited(day)
            dayDao.updateSession(target.copy(endMin = minutes, endSource = source.name).snapped())
            true
        }

    /**
     * Amber "check this" on a session whose geofence visit was under an hour.
     * A hand-typed start is never flagged — the user knows what they entered.
     */
    suspend fun setStartUncertain(sessionId: Long, date: LocalDate, uncertain: Boolean) = writeLock.withLock {
        val session = dayDao.sessionsOn(date.toString()).firstOrNull { it.id == sessionId } ?: return@withLock
        if (uncertain && session.startSource == TimeSource.MANUAL.name) return@withLock
        if (session.startUncertain == uncertain) return@withLock
        dayDao.updateSession(session.copy(startUncertain = uncertain).snapped())
    }

    suspend fun lastSessionOf(date: LocalDate, mode: WorkMode): WorkSessionEntity? =
        dayDao.sessionsOn(date.toString()).lastOrNull { it.mode == mode.name }

    suspend fun sessionsForJobLocation(date: LocalDate, jobLocationId: Long): List<WorkSessionEntity> =
        dayDao.sessionsForJobLocation(date.toString(), jobLocationId)

    suspend fun openJobSession(date: LocalDate, jobLocationId: Long): WorkSessionEntity? =
        dayDao.openSessionForJobLocation(date.toString(), jobLocationId)

    /**
     * Opens a visit to a client site. Scoped to the location, not to FIELD as a
     * whole: two sites on one day are two visits, and a duplicate ENTER for a
     * site already being visited opens nothing.
     */
    suspend fun startJobSession(
        date: LocalDate,
        jobLocationId: Long,
        title: String,
        minutes: Int,
    ): Boolean = edit(date) { day ->
        if (dayDao.openSessionForJobLocation(day.date, jobLocationId) != null) return@edit false
        markEdited(day)
        val order = (dayDao.sessionsOn(day.date).maxOfOrNull { it.sortOrder } ?: -1) + 1
        dayDao.insertSession(
            WorkSessionEntity(
                date = day.date, mode = WorkMode.FIELD.name, startMin = minutes,
                title = title.trim(), startSource = TimeSource.GEOFENCE.name,
                jobLocationId = jobLocationId, sortOrder = order,
            ).snapped(),
        )
        true
    }

    /** Closes the running visit to a site; a visit the user closed by hand stays put. */
    suspend fun endJobSession(date: LocalDate, jobLocationId: Long, minutes: Int): Boolean = edit(date) { day ->
        val open = dayDao.openSessionForJobLocation(day.date, jobLocationId) ?: return@edit false
        markEdited(day)
        dayDao.updateSession(open.copy(endMin = minutes, endSource = TimeSource.GEOFENCE.name).snapped())
        true
    }

    // --- day-level ----------------------------------------------------------

    suspend fun setNotes(date: LocalDate, notes: String) = edit(date) { upsertEdited(it.copy(notes = notes)) }

    suspend fun setDayType(date: LocalDate, type: DayType) = edit(date) { upsertEdited(it.copy(dayType = type.name)) }

    // --- activities ---------------------------------------------------------

    /** An activity always belongs to a session and to a project. */
    suspend fun addActivity(sessionId: Long, categoryId: Long, projectId: Long): Long = writeLock.withLock {
        val order = (dayDao.allActivities().filter { it.sessionId == sessionId }.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dayDao.insertActivity(
            ActivityEntity(sessionId = sessionId, categoryId = categoryId, projectId = projectId, sortOrder = order),
        )
    }

    suspend fun updateActivity(activity: ActivityEntity) = writeLock.withLock {
        dayDao.updateActivity(activity)
    }

    /**
     * Edits one activity by id, read fresh inside the lock — the same protection
     * sessions get. Two quick taps on the duration stepper both start from the
     * stored value instead of from one stale copy of the row.
     */
    suspend fun editActivity(id: Long, block: (ActivityEntity) -> ActivityEntity) = writeLock.withLock {
        val current = dayDao.activityById(id) ?: return@withLock
        dayDao.updateActivity(block(current))
    }

    suspend fun removeActivity(id: Long) = writeLock.withLock { dayDao.deleteActivity(id) }

    /** Marks the day reported now; re-sending overwrites the timestamp and clears the edited flag (spec F9). */
    suspend fun markReported(date: LocalDate) = edit(date) { day ->
        dayDao.upsertDay(day.copy(reportedAt = clock().toEpochMilli(), editedAfterReport = false))
    }

    /**
     * Serializes read-modify-write on the day row. Without it two mutations
     * issued back-to-back can both read the pre-write row and the second write
     * silently drops the first. NOT reentrant — never call from inside.
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

/** UI row for one activity, carrying the ids needed to edit it. */
data class ActivityRow(
    val id: Long,
    val sessionId: Long,
    val categoryId: Long,
    val category: String,
    val projectId: Long,
    val project: String,
    /** Half-hour steps; null = not stated. */
    val durationMin: Int?,
    val note: String,
    val result: String,
    val sortOrder: Int,
) {
    fun toEntity() = ActivityEntity(
        id = id, sessionId = sessionId, categoryId = categoryId, projectId = projectId,
        durationMin = durationMin, note = note, result = result, sortOrder = sortOrder,
    )
}

/** UI row for one session, with its editable entity and its activities. */
data class SessionRow(
    val entity: WorkSessionEntity,
    val session: WorkSession,
    val activityRows: List<ActivityRow>,
)

data class EditableDay(
    val snapshot: DaySnapshot,
    val sessionRows: List<SessionRow>,
)

internal fun DayWithEntries.toSnapshot(): DaySnapshot = DaySnapshot(
    date = LocalDate.parse(day.date),
    sessions = sessions.sortedBy { it.session.sortOrder }.map { it.toDomain() },
    dayType = DayType.valueOf(day.dayType),
    notes = day.notes,
    reported = day.reportedAt != null,
    editedAfterReport = day.editedAfterReport,
)

internal fun com.vitalypr.daylog.data.db.SessionWithActivities.toDomain(): WorkSession = WorkSession(
    id = session.id,
    mode = WorkMode.valueOf(session.mode),
    startMin = session.startMin,
    endMin = session.endMin,
    title = session.title,
    locationText = session.locationText,
    startSource = TimeSource.valueOf(session.startSource),
    endSource = TimeSource.valueOf(session.endSource),
    startUncertain = session.startUncertain,
    activities = activities.sortedBy { it.activity.sortOrder }.map {
        ActivityEntry(
            project = it.project?.name.orEmpty(),
            category = it.category?.name.orEmpty(),
            durationMin = it.activity.durationMin,
            note = it.activity.note,
            result = it.activity.result,
        )
    },
)

internal fun DayWithEntries.toEditable(): EditableDay = EditableDay(
    snapshot = toSnapshot(),
    sessionRows = sessions.sortedBy { it.session.sortOrder }.map { s ->
        SessionRow(
            entity = s.session,
            session = s.toDomain(),
            activityRows = s.activities.sortedBy { it.activity.sortOrder }.map { a ->
                ActivityRow(
                    id = a.activity.id,
                    sessionId = a.activity.sessionId,
                    categoryId = a.activity.categoryId,
                    category = a.category?.name.orEmpty(),
                    projectId = a.activity.projectId,
                    project = a.project?.name.orEmpty(),
                    durationMin = a.activity.durationMin,
                    note = a.activity.note,
                    result = a.activity.result,
                    sortOrder = a.activity.sortOrder,
                )
            },
        )
    },
)

package com.vitalypr.daylog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ActivityWithRefs(
    @Embedded val activity: ActivityEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id") val category: CategoryEntity?,
    @Relation(parentColumn = "projectId", entityColumn = "id") val project: ProjectEntity?,
)

data class SessionWithActivities(
    @Embedded val session: WorkSessionEntity,
    @Relation(entity = ActivityEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val activities: List<ActivityWithRefs>,
)

data class DayWithEntries(
    @Embedded val day: WorkDayEntity,
    @Relation(entity = WorkSessionEntity::class, parentColumn = "date", entityColumn = "date")
    val sessions: List<SessionWithActivities>,
)

@Dao
interface DayDao {

    @Transaction
    @Query("SELECT * FROM work_day WHERE date = :date")
    fun observeDay(date: String): Flow<DayWithEntries?>

    @Transaction
    @Query("SELECT * FROM work_day WHERE date = :date")
    suspend fun getDay(date: String): DayWithEntries?

    @Transaction
    @Query("SELECT * FROM work_day WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeRange(from: String, to: String): Flow<List<DayWithEntries>>

    @Transaction
    @Query("SELECT * FROM work_day WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun getRange(from: String, to: String): List<DayWithEntries>

    @Upsert
    suspend fun upsertDay(day: WorkDayEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM work_day WHERE date = :date)")
    suspend fun dayExists(date: String): Boolean

    // --- sessions -----------------------------------------------------------

    @Insert
    suspend fun insertSession(session: WorkSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkSessionEntity)

    @Delete
    suspend fun deleteSession(session: WorkSessionEntity)

    @Query("SELECT * FROM work_session WHERE date = :date ORDER BY sortOrder, id")
    suspend fun sessionsOn(date: String): List<WorkSessionEntity>

    /** The session of [mode] still running on [date], if any. */
    @Query(
        "SELECT * FROM work_session WHERE date = :date AND mode = :mode " +
            "AND startMin IS NOT NULL AND endMin IS NULL ORDER BY startMin DESC LIMIT 1",
    )
    suspend fun openSession(date: String, mode: String): WorkSessionEntity?

    @Query("SELECT * FROM work_session WHERE date = :date AND jobLocationId = :jobLocationId ORDER BY sortOrder, id")
    suspend fun sessionsForJobLocation(date: String, jobLocationId: Long): List<WorkSessionEntity>

    /** The visit to [jobLocationId] still running on [date], if any. */
    @Query(
        "SELECT * FROM work_session WHERE date = :date AND jobLocationId = :jobLocationId " +
            "AND startMin IS NOT NULL AND endMin IS NULL ORDER BY startMin DESC LIMIT 1",
    )
    suspend fun openSessionForJobLocation(date: String, jobLocationId: Long): WorkSessionEntity?

    // --- activities ---------------------------------------------------------

    @Insert
    suspend fun insertActivity(activity: ActivityEntity): Long

    @Update
    suspend fun updateActivity(activity: ActivityEntity)

    @Query("SELECT * FROM activity WHERE id = :id")
    suspend fun activityById(id: Long): ActivityEntity?

    @Query("DELETE FROM activity WHERE id = :id")
    suspend fun deleteActivity(id: Long)

    // --- backup/restore: whole-table read and replace -----------------------

    @Query("SELECT * FROM work_day ORDER BY date")
    suspend fun allDays(): List<WorkDayEntity>

    @Query("SELECT * FROM work_session ORDER BY id")
    suspend fun allSessions(): List<WorkSessionEntity>

    @Query("SELECT * FROM activity ORDER BY id")
    suspend fun allActivities(): List<ActivityEntity>

    @Insert
    suspend fun insertDays(days: List<WorkDayEntity>)

    @Insert
    suspend fun insertSessions(sessions: List<WorkSessionEntity>)

    @Insert
    suspend fun insertActivities(activities: List<ActivityEntity>)

    @Query("DELETE FROM activity")
    suspend fun clearActivities()

    @Query("DELETE FROM work_session")
    suspend fun clearSessions()

    @Query("DELETE FROM work_day")
    suspend fun clearDays()
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE isHidden = 0 ORDER BY sortOrder")
    fun observeVisible(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Query("SELECT * FROM category ORDER BY sortOrder")
    suspend fun all(): List<CategoryEntity>

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM category")
    suspend fun clear()
}

@Dao
interface ProjectDao {

    @Query("SELECT * FROM project ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM project WHERE isArchived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM project ORDER BY sortOrder, name")
    suspend fun all(): List<ProjectEntity>

    @Query("SELECT COUNT(*) FROM project")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM activity WHERE projectId = :projectId")
    suspend fun activityCount(projectId: Long): Int

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Insert
    suspend fun insertAll(projects: List<ProjectEntity>)

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM project WHERE id = :projectId")
    suspend fun deleteById(projectId: Long)

    @Query("DELETE FROM project")
    suspend fun clear()
}

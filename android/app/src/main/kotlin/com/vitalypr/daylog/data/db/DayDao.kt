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

data class ActivityWithCategory(
    @Embedded val activity: ActivityEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity,
)

data class DayWithEntries(
    @Embedded val day: WorkDayEntity,
    @Relation(parentColumn = "date", entityColumn = "date")
    val fieldJobs: List<FieldJobEntity>,
    @Relation(entity = ActivityEntity::class, parentColumn = "date", entityColumn = "date")
    val activities: List<ActivityWithCategory>,
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

    @Insert
    suspend fun insertFieldJob(job: FieldJobEntity): Long

    @Update
    suspend fun updateFieldJob(job: FieldJobEntity)

    @Delete
    suspend fun deleteFieldJob(job: FieldJobEntity)

    @Insert
    suspend fun insertActivity(activity: ActivityEntity): Long

    @Update
    suspend fun updateActivity(activity: ActivityEntity)

    @Query("DELETE FROM activity WHERE id = :id")
    suspend fun deleteActivity(id: Long)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE isHidden = 0 ORDER BY sortOrder")
    fun observeVisible(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)
}

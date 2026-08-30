package com.vitalypr.daylog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobLocationDao {

    @Query("SELECT * FROM job_location ORDER BY name")
    fun observeAll(): Flow<List<JobLocationEntity>>

    @Query("SELECT * FROM job_location WHERE isActive = 1")
    suspend fun activeLocations(): List<JobLocationEntity>

    @Query("SELECT * FROM job_location WHERE id = :id")
    suspend fun byId(id: Long): JobLocationEntity?

    @Insert
    suspend fun insert(location: JobLocationEntity): Long

    @Update
    suspend fun update(location: JobLocationEntity)

    @Delete
    suspend fun delete(location: JobLocationEntity)

    @Insert
    suspend fun insertAll(locations: List<JobLocationEntity>)

    @Query("DELETE FROM job_location")
    suspend fun clear()
}

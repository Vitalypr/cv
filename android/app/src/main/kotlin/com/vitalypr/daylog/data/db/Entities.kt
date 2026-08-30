package com.vitalypr.daylog.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** The logical day. Worked time lives in its [WorkSessionEntity] rows (v2.0). */
@Entity(tableName = "work_day")
data class WorkDayEntity(
    @PrimaryKey val date: String,
    val notes: String = "",
    val dayType: String = "WORK",
    val reportedAt: Long? = null,
    val editedAfterReport: Boolean = false,
)

/**
 * A stretch of work in one mode (BASE / HOME / FIELD). A day can hold several,
 * and each carries its own activities — the same day can mix time at the base,
 * from home and on a client site.
 */
@Entity(
    tableName = "work_session",
    foreignKeys = [
        ForeignKey(
            entity = WorkDayEntity::class,
            parentColumns = ["date"], childColumns = ["date"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("date")],
)
data class WorkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val mode: String,
    val startMin: Int? = null,
    val endMin: Int? = null,
    val title: String = "",
    val locationText: String? = null,
    val startSource: String = "MANUAL",
    val endSource: String = "MANUAL",
    /** The geofence visit behind the start was under an hour — shown amber. */
    val startUncertain: Boolean = false,
    /** Set when a job-location fence opened this session. */
    val jobLocationId: Long? = null,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "activity",
    foreignKeys = [
        ForeignKey(
            entity = WorkSessionEntity::class,
            parentColumns = ["id"], childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"], childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT, // categories are hidden, never deleted (spec F5)
        ),
    ],
    indices = [Index("sessionId"), Index("categoryId"), Index("projectId")],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** An activity always belongs to a session, and through it to a day. */
    val sessionId: Long,
    val categoryId: Long,
    /** Mandatory — an activity always belongs to a project. */
    val projectId: Long,
    /** Half-hour steps, null = not stated. */
    val durationMin: Int? = null,
    /** Free-text detail — what was actually done. */
    val note: String = "",
    val sortOrder: Int = 0,
)

/**
 * A project an activity is booked against. Archived rather than deleted once
 * used, so past days keep rendering — the same rule categories follow.
 */
@Entity(tableName = "project")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String? = null,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "job_location")
data class JobLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Int = 2000,
    val isActive: Boolean = true,
)

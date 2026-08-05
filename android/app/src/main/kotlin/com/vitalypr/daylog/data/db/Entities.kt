package com.vitalypr.daylog.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room entities per spec §6.3. Dates are ISO yyyy-MM-dd; times are minutes from midnight (may exceed 1440). */

@Entity(tableName = "work_day")
data class WorkDayEntity(
    @PrimaryKey val date: String,
    val arrivalMin: Int? = null,
    val departureMin: Int? = null,
    val arrivalSource: String = "MANUAL",
    val departureSource: String = "MANUAL",
    val notes: String = "",
    val dayType: String = "WORK",
    val reportedAt: Long? = null,
    val editedAfterReport: Boolean = false,
)

@Entity(
    tableName = "field_job",
    foreignKeys = [ForeignKey(
        entity = WorkDayEntity::class,
        parentColumns = ["date"], childColumns = ["date"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("date")],
)
data class FieldJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val title: String,
    val locationText: String? = null,
    val startMin: Int? = null,
    val endMin: Int? = null,
)

@Entity(
    tableName = "activity",
    foreignKeys = [
        ForeignKey(
            entity = WorkDayEntity::class,
            parentColumns = ["date"], childColumns = ["date"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"], childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT, // categories are hidden, never deleted (spec F5)
        ),
    ],
    indices = [Index("date"), Index("categoryId")],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val categoryId: Long,
    val startMin: Int? = null,
    val endMin: Int? = null,
    val note: String = "",
    val result: String = "",
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

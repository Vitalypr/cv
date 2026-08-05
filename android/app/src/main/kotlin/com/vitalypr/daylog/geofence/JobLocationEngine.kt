package com.vitalypr.daylog.geofence

import com.vitalypr.daylog.data.repo.JobLocationRepository
import com.vitalypr.daylog.di.Now
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Thin clock adapter over the tracking rules in JobLocationRepository (spec §6.6b). */
@Singleton
class JobLocationEngine @Inject constructor(
    private val repository: JobLocationRepository,
    @Now private val now: () -> LocalDateTime,
) {
    suspend fun onEnter(locationId: Long) =
        repository.onEnter(locationId, now().toLocalDate(), nowMinutes())

    suspend fun onExit(locationId: Long) =
        repository.onExit(locationId, now().toLocalDate(), nowMinutes())

    private fun nowMinutes(): Int = now().toLocalTime().toSecondOfDay() / 60
}

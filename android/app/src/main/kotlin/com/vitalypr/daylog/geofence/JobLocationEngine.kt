package com.vitalypr.daylog.geofence

import com.vitalypr.daylog.data.repo.JobLocationRepository
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.geo.GeofenceRules
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Job-site tracking (spec §6.6b) under the same ordering invariants as the office
 * engine: occupancy decides whether an exit is real, and the event's own
 * timestamp decides which day it lands on. Without occupancy a catch-up EXIT
 * created a field job that consisted of nothing but an end time.
 */
@Singleton
class JobLocationEngine @Inject constructor(
    private val repository: JobLocationRepository,
    private val fenceState: FenceStateStore,
    @Now private val now: () -> LocalDateTime,
) {

    suspend fun onEnter(locationId: Long, eventAt: LocalDateTime? = null) {
        val at = eventAt ?: now()
        if (GeofenceRules.isStale(at, now())) return
        val fence = fenceId(locationId)
        if (fenceState.insideSince(fence) != null) return // already inside — not a new arrival
        fenceState.markInside(fence, at)
        repository.onEnter(locationId, at.toLocalDate(), at.toLocalTime().toSecondOfDay() / 60)
    }

    suspend fun onExit(locationId: Long, eventAt: LocalDateTime? = null) {
        val at = eventAt ?: now()
        val fence = fenceId(locationId)
        val insideSince = fenceState.insideSince(fence) ?: return // phantom exit
        fenceState.markOutside(fence)
        // Same-day only: a visit we lost track of across midnight is not reconstructed.
        if (insideSince.toLocalDate() != at.toLocalDate()) return
        repository.onExit(locationId, at.toLocalDate(), at.toLocalTime().toSecondOfDay() / 60)
    }

    private fun fenceId(locationId: Long) = "${GeofenceManager.JOB_PREFIX}$locationId"
}

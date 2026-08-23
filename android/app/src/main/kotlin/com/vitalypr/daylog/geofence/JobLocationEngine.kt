package com.vitalypr.daylog.geofence

import com.vitalypr.daylog.data.repo.JobLocationRepository
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.geo.FenceEvent
import com.vitalypr.daylog.domain.geo.FenceState
import com.vitalypr.daylog.domain.geo.GeofenceRules
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Job-site tracking (spec §6.6b) under the same ordering invariants as the
 * office fence: occupancy decides whether an exit is real, and the event's own
 * timestamp decides which day it lands on. Simpler than the office machine
 * because a job visit prompts nothing — it only fills the amber מוצע times.
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
        // Already inside — a duplicate delivery is not a new arrival.
        if (fenceState.state(fence) !is FenceState.Outside) return
        fenceState.save(fence, FenceState.Inside(at))
        repository.onEnter(locationId, at.toLocalDate(), minutesOf(at))
    }

    suspend fun onExit(locationId: Long, eventAt: LocalDateTime? = null) {
        val at = eventAt ?: now()
        val fence = fenceId(locationId)
        val state = fenceState.state(fence)
        val since = when (state) {
            is FenceState.Inside -> state.since
            is FenceState.Leaving -> state.since
            FenceState.Outside -> return // phantom exit — we never saw the arrival
        }
        fenceState.save(fence, FenceState.Outside)
        // Same-day only: a visit we lost track of across midnight is not reconstructed.
        if (since.toLocalDate() != at.toLocalDate()) return
        repository.onExit(locationId, at.toLocalDate(), minutesOf(at), visitStartMin = minutesOf(since))
    }

    private fun minutesOf(at: LocalDateTime) = at.toLocalTime().toSecondOfDay() / 60

    private fun fenceId(locationId: Long) = "${GeofenceManager.JOB_PREFIX}$locationId"
}

package com.vitalypr.daylog

import com.vitalypr.daylog.domain.geo.FenceState
import com.vitalypr.daylog.geofence.FenceStateStore

/** DataStore is a process singleton and leaks between tests — engines take the interface. */
class InMemoryFenceStateStore : FenceStateStore {
    private val states = mutableMapOf<String, FenceState>()

    override suspend fun state(fenceId: String): FenceState = states[fenceId] ?: FenceState.Outside
    override suspend fun save(fenceId: String, state: FenceState) { states[fenceId] = state }
}

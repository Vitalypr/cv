package com.vitalypr.daylog

import com.vitalypr.daylog.geofence.FenceStateStore
import java.time.LocalDateTime

/** DataStore is a process singleton and leaks between tests — engines take the interface. */
class InMemoryFenceStateStore : FenceStateStore {
    private val inside = mutableMapOf<String, LocalDateTime>()

    override suspend fun insideSince(fenceId: String): LocalDateTime? = inside[fenceId]
    override suspend fun markInside(fenceId: String, at: LocalDateTime) { inside[fenceId] = at }
    override suspend fun markOutside(fenceId: String) { inside.remove(fenceId) }
}

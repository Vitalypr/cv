package com.vitalypr.daylog.geofence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vitalypr.daylog.domain.geo.FenceState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Persists where we believe the user is for each fence.
 *
 * This is the piece that was missing originally: without it an EXIT delivered
 * out of order (Play Services flushes transitions when it next gets a fix,
 * which can be the next morning) was indistinguishable from a real one, so
 * arriving at the office produced a departure suggestion. It also carries the
 * entry time across process deaths, which is what lets the visit's length —
 * and therefore whether it was a real stay — be measured at all.
 */
interface FenceStateStore {
    suspend fun state(fenceId: String): FenceState
    suspend fun save(fenceId: String, state: FenceState)
}

private val Context.fenceStore by preferencesDataStore(name = "geofence_state")

@Singleton
class DataStoreFenceStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : FenceStateStore {

    override suspend fun state(fenceId: String): FenceState {
        val prefs = context.fenceStore.data.first()
        val since = prefs[sinceKey(fenceId)]?.let(::decode) ?: return FenceState.Outside
        val exitAt = prefs[exitKey(fenceId)]?.let(::decode)
        return if (exitAt == null) FenceState.Inside(since) else FenceState.Leaving(since, exitAt)
    }

    override suspend fun save(fenceId: String, state: FenceState) {
        context.fenceStore.edit { prefs ->
            when (state) {
                FenceState.Outside -> {
                    prefs.remove(sinceKey(fenceId))
                    prefs.remove(exitKey(fenceId))
                }
                is FenceState.Inside -> {
                    prefs[sinceKey(fenceId)] = encode(state.since)
                    prefs.remove(exitKey(fenceId))
                }
                is FenceState.Leaving -> {
                    prefs[sinceKey(fenceId)] = encode(state.since)
                    prefs[exitKey(fenceId)] = encode(state.exitAt)
                }
            }
        }
    }

    // Wall-clock values, so they round-trip through a fixed offset, not a zone.
    private fun encode(at: LocalDateTime) = at.toEpochSecond(ZoneOffset.UTC)
    private fun decode(seconds: Long) = LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.UTC)

    private fun sinceKey(fenceId: String) = longPreferencesKey("inside_$fenceId")
    private fun exitKey(fenceId: String) = longPreferencesKey("leaving_$fenceId")
}

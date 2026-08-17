package com.vitalypr.daylog.geofence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Remembers which fences we believe we are inside of, and since when.
 *
 * This is the piece that was missing: without it an EXIT delivered out of order
 * (Play Services flushes transitions when it next gets a fix, which can be the
 * next morning) was indistinguishable from a real one, so arriving at the office
 * produced a departure suggestion. An exit is only acted on when we saw the
 * matching entry, and the dwell between them is what tells a visit from a
 * drive-past. Persisted, because the process dies between transitions.
 */
interface FenceStateStore {
    suspend fun insideSince(fenceId: String): LocalDateTime?
    suspend fun markInside(fenceId: String, at: LocalDateTime)
    suspend fun markOutside(fenceId: String)
}

private val Context.fenceStore by preferencesDataStore(name = "geofence_state")

@Singleton
class DataStoreFenceStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : FenceStateStore {

    override suspend fun insideSince(fenceId: String): LocalDateTime? =
        context.fenceStore.data.first()[key(fenceId)]
            ?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }

    override suspend fun markInside(fenceId: String, at: LocalDateTime) {
        context.fenceStore.edit { it[key(fenceId)] = at.toEpochSecond(ZoneOffset.UTC) }
    }

    override suspend fun markOutside(fenceId: String) {
        context.fenceStore.edit { it.remove(key(fenceId)) }
    }

    // Wall-clock value, so it round-trips through a fixed offset rather than a zone.
    private fun key(fenceId: String) = longPreferencesKey("inside_$fenceId")
}

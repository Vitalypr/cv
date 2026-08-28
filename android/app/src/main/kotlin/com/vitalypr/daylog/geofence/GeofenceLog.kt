package com.vitalypr.daylog.geofence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A rolling record of what the geofence subsystem actually did.
 *
 * Field failures here are unreproducible by construction — they depend on when
 * Play Services felt like delivering a transition. Without a trail the only
 * diagnosis available was guesswork, so the app now keeps the last
 * [CAPACITY] events and shows them in Settings.
 */
@Singleton
class GeofenceLog @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val entries: Flow<List<String>> = context.logStore.data.map { prefs ->
        prefs[KEY]?.lines()?.filter { it.isNotBlank() }?.asReversed().orEmpty()
    }

    suspend fun record(at: LocalDateTime, line: String) {
        val stamp = "%02d.%02d %02d:%02d".format(
            at.dayOfMonth, at.monthValue, at.hour, at.minute,
        )
        context.logStore.edit { prefs ->
            val kept = prefs[KEY]?.lines()?.filter { it.isNotBlank() }.orEmpty()
                .takeLast(CAPACITY - 1)
            prefs[KEY] = (kept + "$stamp  $line").joinToString("\n")
        }
    }

    suspend fun clear() = context.logStore.edit { it.remove(KEY) }.let { }

    private companion object {
        val KEY = stringPreferencesKey("geofence_log")
        const val CAPACITY = 50
    }
}

private val Context.logStore by preferencesDataStore(name = "geofence_log")

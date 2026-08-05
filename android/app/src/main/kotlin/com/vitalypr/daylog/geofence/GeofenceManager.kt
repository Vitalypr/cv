package com.vitalypr.daylog.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.vitalypr.daylog.data.settings.SettingsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Registers the single office geofence (spec §6.6): ENTER|EXIT transitions,
 * INITIAL TRIGGERS DISABLED — setting the office while sitting in it must not
 * fire a bogus prompt. Re-registered on boot and on settings changes.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsSource,
    private val jobLocationRepository: com.vitalypr.daylog.data.repo.JobLocationRepository,
) {

    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    @SuppressLint("MissingPermission")
    suspend fun sync() {
        val settings = settingsRepository.settings.first()
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val fences = mutableListOf<Geofence>()
        if (settings.officeLat != null && settings.officeLon != null) {
            fences += fence(FENCE_ID, settings.officeLat, settings.officeLon, settings.officeRadiusM)
        }
        // Job locations (spec §6.6b): wide client-site fences, request id "job_<id>".
        jobLocationRepository.activeLocations().forEach { loc ->
            fences += fence("$JOB_PREFIX${loc.id}", loc.lat, loc.lon, loc.radiusM)
        }

        if (!settings.geofenceEnabled || !hasPermission || fences.isEmpty()) {
            runCatching { client.removeGeofences(pendingIntent()) }
            return
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // deliberately no initial trigger
            .addGeofences(fences)
            .build()

        runCatching {
            client.removeGeofences(pendingIntent())
            client.addGeofences(request, pendingIntent())
        }
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        RC_GEOFENCE,
        Intent(context, GeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun fence(id: String, lat: Double, lon: Double, radiusM: Int): Geofence =
        Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(lat, lon, radiusM.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

    companion object {
        const val FENCE_ID = "office"
        const val JOB_PREFIX = "job_"
        private const val RC_GEOFENCE = 310
    }
}

package com.vitalypr.daylog.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.vitalypr.daylog.data.settings.SettingsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Why registration can fail on-device even though the logic is correct:
 * Android 10+ requires ACCESS_BACKGROUND_LOCATION ("allow all the time") for
 * geofencing, and GMS reports failures asynchronously. This manager therefore
 * exposes an observable [status] the Settings screen renders, so a silent
 * failure is impossible by construction.
 */
sealed interface GeofenceStatus {
    data object Unknown : GeofenceStatus
    data object Disabled : GeofenceStatus
    data object NoPermission : GeofenceStatus
    data object NoBackgroundPermission : GeofenceStatus
    data object NoLocations : GeofenceStatus
    data class Active(val fenceCount: Int) : GeofenceStatus
    data class Error(val message: String) : GeofenceStatus
}

@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsSource,
    private val jobLocationRepository: com.vitalypr.daylog.data.repo.JobLocationRepository,
) {

    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    private val _status = MutableStateFlow<GeofenceStatus>(GeofenceStatus.Unknown)
    val status: StateFlow<GeofenceStatus> = _status.asStateFlow()

    @SuppressLint("MissingPermission")
    suspend fun sync() {
        val settings = settingsRepository.settings.first()
        val jobs = jobLocationRepository.activeLocations()

        val precheck = precheck(
            enabled = settings.geofenceEnabled,
            hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
            hasBackground = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            hasOffice = settings.officeLat != null && settings.officeLon != null,
            jobCount = jobs.size,
        )
        if (precheck !is GeofenceStatus.Active) {
            runCatching { client.removeGeofences(pendingIntent()).await() }
            _status.value = precheck
            return
        }

        val fences = buildList {
            if (settings.officeLat != null && settings.officeLon != null) {
                // Office: immediate delivery — the event time becomes the recorded arrival/departure.
                add(fence(FENCE_ID, settings.officeLat, settings.officeLon, settings.officeRadiusM, responsivenessMs = 0))
            }
            // Job fences: 2 km radius makes ±2 min immaterial, so let GMS batch
            // transitions instead of waking immediately (battery, spec N6).
            jobs.forEach { loc ->
                add(fence("$JOB_PREFIX${loc.id}", loc.lat, loc.lon, loc.radiusM, responsivenessMs = JOB_RESPONSIVENESS_MS))
            }
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // deliberately no initial trigger (spec §6.6)
            .addGeofences(fences)
            .build()

        _status.value = try {
            runCatching { client.removeGeofences(pendingIntent()).await() }
            client.addGeofences(request, pendingIntent()).await() // surfaces real GMS failures
            GeofenceStatus.Active(fences.size)
        } catch (e: Exception) {
            GeofenceStatus.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun fence(id: String, lat: Double, lon: Double, radiusM: Int, responsivenessMs: Int): Geofence =
        Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(lat, lon, radiusM.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setNotificationResponsiveness(responsivenessMs)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        RC_GEOFENCE,
        Intent(context, GeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    companion object {
        const val FENCE_ID = "office"
        const val JOB_PREFIX = "job_"
        private const val RC_GEOFENCE = 310
        private const val JOB_RESPONSIVENESS_MS = 2 * 60 * 1000

        /** Pure gate logic — unit-tested; Active(count) means "go register". */
        fun precheck(
            enabled: Boolean,
            hasFine: Boolean,
            hasBackground: Boolean,
            hasOffice: Boolean,
            jobCount: Int,
        ): GeofenceStatus = when {
            !enabled -> GeofenceStatus.Disabled
            !hasFine -> GeofenceStatus.NoPermission
            !hasBackground -> GeofenceStatus.NoBackgroundPermission
            !hasOffice && jobCount == 0 -> GeofenceStatus.NoLocations
            else -> GeofenceStatus.Active((if (hasOffice) 1 else 0) + jobCount)
        }
    }
}

package com.vitalypr.daylog.geofence

import com.vitalypr.daylog.domain.geo.GeofenceRules
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The gate that decides whether fences register — every blocking state is explicit. */
class GeofencePrecheckTest {

    @Test fun `disabled toggle blocks everything`() {
        assertEquals(
            GeofenceStatus.Disabled,
            GeofenceManager.precheck(enabled = false, hasFine = true, hasBackground = true, hasOffice = true, jobCount = 2),
        )
    }

    @Test fun `missing fine permission blocks`() {
        assertEquals(
            GeofenceStatus.NoPermission,
            GeofenceManager.precheck(enabled = true, hasFine = false, hasBackground = false, hasOffice = true, jobCount = 0),
        )
    }

    @Test fun `missing background permission blocks - the on-device root cause`() {
        assertEquals(
            GeofenceStatus.NoBackgroundPermission,
            GeofenceManager.precheck(enabled = true, hasFine = true, hasBackground = false, hasOffice = false, jobCount = 3),
        )
    }

    @Test fun `no locations at all reports NoLocations`() {
        assertEquals(
            GeofenceStatus.NoLocations,
            GeofenceManager.precheck(enabled = true, hasFine = true, hasBackground = true, hasOffice = false, jobCount = 0),
        )
    }

    @Test fun `office plus jobs counts fences`() {
        assertEquals(
            GeofenceStatus.Active(3),
            GeofenceManager.precheck(enabled = true, hasFine = true, hasBackground = true, hasOffice = true, jobCount = 2),
        )
    }

    @Test fun `jobs alone are enough - office not required`() {
        assertEquals(
            GeofenceStatus.Active(2),
            GeofenceManager.precheck(enabled = true, hasFine = true, hasBackground = true, hasOffice = false, jobCount = 2),
        )
    }

    /**
     * A job pin dropped on the office would make every office arrival look like a
     * client-site arrival too, inventing a field job each working day.
     */
    @Test fun `a job pin on top of the office is not tracked separately`() {
        val officeLat = 32.0
        val officeLon = 34.8
        val radiusM = 150
        val onSite = GeofenceRules.distanceMeters(officeLat, officeLon, 32.0009, 34.8) // ~100 m
        val elsewhere = GeofenceRules.distanceMeters(officeLat, officeLon, 32.05, 34.8) // ~5.5 km
        assertTrue(onSite <= radiusM, "expected the pin to fall inside the office fence")
        assertTrue(elsewhere > radiusM, "a genuine client site must stay tracked")
    }
}

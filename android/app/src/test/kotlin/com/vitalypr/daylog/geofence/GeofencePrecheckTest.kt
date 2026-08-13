package com.vitalypr.daylog.geofence

import org.junit.Test
import kotlin.test.assertEquals

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
}

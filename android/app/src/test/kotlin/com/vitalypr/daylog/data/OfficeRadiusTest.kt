package com.vitalypr.daylog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.settings.SettingsRepository
import com.vitalypr.daylog.domain.geo.GeofenceRules
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The office fence radius is the user's choice from a fixed ladder (100 m …
 * 2 km). What matters here is that the stored value and the value the Settings
 * screen can show are always the same one — a fence running at a size no chip
 * represents is exactly the kind of silent mismatch that makes the feature feel
 * broken.
 */
@RunWith(RobolectricTestRunner::class)
class OfficeRadiusTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `every offered radius round-trips unchanged`() = runTest {
        val settings = SettingsRepository(context)
        GeofenceRules.OFFICE_RADIUS_OPTIONS.forEach { meters ->
            settings.setOfficeRadius(meters)
            assertEquals(meters, settings.settings.first().officeRadiusM)
        }
    }

    @Test fun `the default is one of the offered values`() = runTest {
        val settings = SettingsRepository(context)
        settings.setOfficeRadius(GeofenceRules.DEFAULT_OFFICE_RADIUS_M)
        val stored = settings.settings.first().officeRadiusM
        assertEquals(GeofenceRules.DEFAULT_OFFICE_RADIUS_M, stored)
        assertEquals(true, stored in GeofenceRules.OFFICE_RADIUS_OPTIONS)
    }

    /** A radius saved before the ladder existed (150 m) must land on an offered one. */
    @Test fun `a legacy radius is snapped up to the next offered value`() = runTest {
        val settings = SettingsRepository(context)
        settings.setOfficeRadius(150)
        assertEquals(300, settings.settings.first().officeRadiusM)
    }
}

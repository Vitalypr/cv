package com.vitalypr.daylog

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec N3 (revised v0.7 by explicit product-owner decision — the map location
 * picker needs OSM tiles): INTERNET exists SOLELY for map tiles. The
 * no-analytics/no-backend posture is unchanged; this test documents the
 * exception so any further network-facing change is a conscious one.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestGuardTest {

    private val permissions: List<String>
        get() {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
            return info.requestedPermissions?.toList() ?: emptyList()
        }

    @Test
    fun `INTERNET present for the map picker only - documented N3 revision`() {
        assertTrue(android.Manifest.permission.INTERNET in permissions)
    }

    @Test
    fun `required permissions are present`() {
        assertTrue(android.Manifest.permission.POST_NOTIFICATIONS in permissions)
        assertTrue(android.Manifest.permission.RECEIVE_BOOT_COMPLETED in permissions)
        assertTrue(android.Manifest.permission.ACCESS_FINE_LOCATION in permissions)
        assertTrue(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION in permissions)
    }
}

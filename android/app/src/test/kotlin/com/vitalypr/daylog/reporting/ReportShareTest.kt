package com.vitalypr.daylog.reporting

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spec §6.4: prefer WhatsApp, then WhatsApp Business, else system chooser. */
@RunWith(RobolectricTestRunner::class)
class ReportShareTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun installFakeHandler(pkg: String) {
        val pm = shadowOf(context.packageManager)
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(pkg)
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.Share"
            }
        }
        pm.addResolveInfoForIntent(intent, info)
    }

    @Test fun `prefers whatsapp when installed`() {
        installFakeHandler(ReportShare.WHATSAPP)
        installFakeHandler(ReportShare.WHATSAPP_BUSINESS)
        val intent = ReportShare.intentFor(context, "דוח")
        assertEquals(ReportShare.WHATSAPP, intent.`package`)
        assertEquals("דוח", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test fun `falls back to whatsapp business`() {
        installFakeHandler(ReportShare.WHATSAPP_BUSINESS)
        val intent = ReportShare.intentFor(context, "דוח")
        assertEquals(ReportShare.WHATSAPP_BUSINESS, intent.`package`)
    }

    @Test fun `falls back to chooser when neither installed`() {
        val intent = ReportShare.intentFor(context, "דוח")
        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        val inner = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertTrue(inner.`package` == null)
    }
}

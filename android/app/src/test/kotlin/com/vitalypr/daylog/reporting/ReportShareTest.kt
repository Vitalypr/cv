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

    // --- styled-PDF share (spec §2.4 v0.5) ---

    private fun pdfFile(): java.io.File {
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        return java.io.File(dir, "daylog-2026-08-04.pdf").apply { writeText("%PDF-fake") }
    }

    /**
     * Single method: FileProvider caches its path strategy statically, so both
     * pdf scenarios must share one test context (chooser first, then targeted).
     */
    @Test fun `pdf share - chooser fallback, then whatsapp targeting with stream, caption, grant`() {
        // No WhatsApp installed → chooser, grant flag preserved.
        val fallback = ReportShare.pdfIntent(context, pdfFile(), "דוח")
        assertEquals(Intent.ACTION_CHOOSER, fallback.action)
        assertTrue(fallback.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("application/pdf", fallback.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.type)

        // Install WhatsApp → targeted intent with stream + caption.
        installFakeHandlerPdf(ReportShare.WHATSAPP)
        val share = ReportShare.pdfIntent(context, pdfFile(), "‏דוח יומי")
        assertEquals(ReportShare.WHATSAPP, share.`package`)
        assertEquals("application/pdf", share.type)
        assertTrue(share.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) != null)
        assertEquals("‏דוח יומי", share.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    private fun installFakeHandlerPdf(pkg: String) {
        val pm = shadowOf(context.packageManager)
        val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").setPackage(pkg)
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Share" }
        }
        pm.addResolveInfoForIntent(intent, info)
    }
}

package com.vitalypr.daylog.reporting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.domain.model.ActivityEntry
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.model.WorkSession
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The PdfDocument container itself cannot run on Robolectric's JVM, so the
 * drawing layer is tested against a bitmap canvas (same Canvas API the PDF
 * page exposes); the container is exercised on-device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReportPdfTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun draw(day: DaySnapshot): Bitmap {
        val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        ReportPdf(context).drawReport(Canvas(bitmap), day)
        return bitmap
    }

    @Test fun `full day draws ledger layout`() {
        val day = DaySnapshot(
            date = LocalDate.of(2026, 8, 4),
            sessions = listOf(
                WorkSession(
                    mode = WorkMode.BASE, startMin = 492, endMin = 1055,
                    activities = listOf(ActivityEntry("רובוטיקה", "התקנה", 150, "חיווט לוח")),
                ),
                WorkSession(mode = WorkMode.FIELD, startMin = 600, endMin = 810, title = "תחנת משנה אקמה"),
                WorkSession(mode = WorkMode.HOME, startMin = 1140, endMin = 1260),
            ),
            notes = "הערה",
        )
        val bitmap = draw(day)
        // Top double rule is brand petrol (#0B6E6A).
        assertEquals(Color.rgb(0x0B, 0x6E, 0x6A), bitmap.getPixel(297, 41))
        // Page ground stays white (ledger, not banner).
        assertEquals(Color.WHITE, bitmap.getPixel(297, 20))
        // Body area received ink.
        val bodyHasInk = (130..800 step 10).any { y ->
            (50..545 step 15).any { x -> bitmap.getPixel(x, y) != Color.WHITE }
        }
        kotlin.test.assertTrue(bodyHasInk, "nothing drawn in the body area")
    }

    @Test fun `empty day still draws rules, title and summary box`() {
        val bitmap = draw(DaySnapshot(date = LocalDate.of(2026, 8, 5)))
        assertEquals(Color.rgb(0x0B, 0x6E, 0x6A), bitmap.getPixel(297, 41))
        // Summary box border row exists (non-white pixel along the box top edge).
        val boxBorder = (140..160).any { y -> bitmap.getPixel(297, y) != Color.WHITE }
        kotlin.test.assertTrue(boxBorder, "summary box border missing")
        assertNotEquals(Color.WHITE, bitmap.getPixel(297, 41))
    }

    /** The header names the report and who it is by (product-owner request). */
    @Test fun `the header carries the report title and the owner`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        assertEquals(
            "דוח עבודה יומי | ויטלי פורטנוב",
            context.getString(com.vitalypr.daylog.R.string.pdf_daily_title),
        )
        // …and it is what the page draws: the title band receives ink.
        val bitmap = draw(DaySnapshot(date = LocalDate.of(2026, 8, 4)))
        val titleHasInk = (60..92 step 4).any { y ->
            (60..545 step 10).any { x -> bitmap.getPixel(x, y) != Color.WHITE }
        }
        kotlin.test.assertTrue(titleHasInk, "nothing drawn in the title band")
    }
}

package com.vitalypr.daylog.reporting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.domain.model.ActivityEntry
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.FieldJob
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
            arrivalMin = 492, departureMin = 1055,
            fieldJobs = listOf(FieldJob("תחנת משנה אקמה", null, 600, 810)),
            activities = listOf(ActivityEntry("התקנה", 540, 690, "חיווט לוח", "הושלם")),
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
}

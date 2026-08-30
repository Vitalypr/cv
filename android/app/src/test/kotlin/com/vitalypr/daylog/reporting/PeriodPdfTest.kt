package com.vitalypr.daylog.reporting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.domain.stats.PeriodSummary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Period summary PDF drawing (same Ledger system as the daily report). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PeriodPdfTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `monthly summary draws ledger layout`() {
        val summary = PeriodSummary(
            label = "סיכום חודשי — אוגוסט 2026",
            workDays = 21, totalMinutes = 186 * 60 + 30,
            baseMinutes = 150 * 60, homeMinutes = 20 * 60, fieldMinutes = 16 * 60 + 30,
            fieldDays = 6, offDays = 1, holidays = 2,
            avgArrivalMin = 8 * 60 + 24, avgDepartureMin = 17 * 60 + 38,
            categoryCounts = listOf("פיתוח" to 14, "התקנה" to 9, "דיון" to 7),
            projectCounts = listOf("רובוטיקה" to 18, "AI למחלקה" to 12),
        )
        val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        PeriodPdf(context).drawSummary(Canvas(bitmap), summary)

        // Petrol top rule; white page ground; ink in the body.
        assertEquals(Color.rgb(0x0B, 0x6E, 0x6A), bitmap.getPixel(297, 41))
        assertEquals(Color.WHITE, bitmap.getPixel(297, 20))
        val bodyHasInk = (110..600 step 10).any { y ->
            (50..545 step 15).any { x -> bitmap.getPixel(x, y) != Color.WHITE }
        }
        assertTrue(bodyHasInk, "nothing drawn in the body area")
    }
}

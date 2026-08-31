package com.vitalypr.daylog.reporting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.vitalypr.daylog.domain.stats.PeriodSummary
import com.vitalypr.daylog.domain.time.formatDuration
import com.vitalypr.daylog.domain.time.formatMinutes
import com.vitalypr.daylog.reporting.LedgerPdf.C
import com.vitalypr.daylog.reporting.LedgerPdf.CONTENT_W
import com.vitalypr.daylog.reporting.LedgerPdf.MARGIN
import com.vitalypr.daylog.reporting.LedgerPdf.PAGE_H
import com.vitalypr.daylog.reporting.LedgerPdf.PAGE_W
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Seam for tests — the PdfDocument container cannot run under Robolectric. */
fun interface PeriodPdfRenderer {
    fun render(summary: PeriodSummary): File
}

/** Period summary (week/month/quarter/year) in the same Ledger design as the daily PDF. */
@Singleton
class PeriodPdf @Inject constructor(
    @ApplicationContext private val context: Context,
) : PeriodPdfRenderer {

    override fun render(summary: PeriodSummary): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        drawSummary(page.canvas, summary)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "daylog-summary.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** All drawing, PdfDocument-free — bitmap-tested. */
    fun drawSummary(canvas: Canvas, s: PeriodSummary) {
        LedgerPdf.drawTopRules(canvas)
        LedgerPdf.rtlText(
            canvas,
            context.getString(com.vitalypr.daylog.R.string.pdf_period_title),
            70f,
            LedgerPdf.textPaint(15f, C.inkSecondary, bold = true),
        )
        LedgerPdf.rtlText(canvas, s.label, 96f, LedgerPdf.textPaint(20f, C.ink, bold = true))

        var y = LedgerPdf.summaryBox(
            canvas, 118f,
            listOf(
                "ימי עבודה" to "${s.workDays}",
                "סה״כ שעות" to formatDuration(s.totalMinutes),
                "ממוצע ליום" to if (s.workDays > 0) formatDuration(s.totalMinutes / s.workDays) else "—",
                "ימי שטח" to "${s.fieldDays}",
            ),
        )

        y = LedgerPdf.sectionLabel(canvas, "ממוצעים", y + 30f)
        y = tableRow(
            canvas,
            "כניסה ממוצעת ${s.avgArrivalMin?.let(::formatMinutes) ?: "—"} · " +
                "יציאה ממוצעת ${s.avgDepartureMin?.let(::formatMinutes) ?: "—"}",
            "", y,
        )
        if (s.offDays > 0 || s.holidays > 0) {
            val special = buildString {
                if (s.offDays > 0) append("ימי חופש: ${s.offDays}")
                if (s.offDays > 0 && s.holidays > 0) append(" · ")
                if (s.holidays > 0) append("חגים: ${s.holidays}")
            }
            y = tableRow(canvas, special, "", y)
        }

        // Where the hours went. Built from the activity durations, so whatever is
        // undescribed is named rather than missing (§5.3).
        if (s.projectMinutes.isNotEmpty()) {
            y = LedgerPdf.sectionLabel(canvas, "שעות לפי פרויקט", y + 24f)
            s.projectMinutes.take(10).forEach { (name, minutes) ->
                y = tableRow(canvas, name, formatDuration(minutes), y)
            }
            if (s.unallocatedMinutes > 0) {
                y = tableRow(canvas, "לא שויך", formatDuration(s.unallocatedMinutes), y)
            }
        }

        if (s.categoryCounts.isNotEmpty()) {
            y = LedgerPdf.sectionLabel(canvas, "פעילויות לפי קטגוריה", y + 24f)
            s.categoryCounts.take(12).forEach { (name, count) ->
                y = tableRow(canvas, name, "$count", y)
            }
        }

        LedgerPdf.footer(
            canvas,
            "בסיס ${formatDuration(s.baseMinutes)} · בית ${formatDuration(s.homeMinutes)} · " +
                "שטח ${formatDuration(s.fieldMinutes)}",
        )
    }

    /** Hairline row: text at the right, optional green count at the left. */
    private fun tableRow(canvas: Canvas, text: String, left: String, top: Float): Float {
        val layout = LedgerPdf.staticLayout(text, LedgerPdf.textPaint(12f, C.ink), (CONTENT_W - 60f).toInt())
        val rowH = maxOf(layout.height.toFloat() + 12f, 26f)
        canvas.save()
        canvas.translate(MARGIN + 60f, top + 4f)
        layout.draw(canvas)
        canvas.restore()
        if (left.isNotBlank()) {
            val leftPaint = LedgerPdf.textPaint(11f, C.sendGreen, bold = true).apply { textAlign = Paint.Align.LEFT }
            canvas.drawText(left, MARGIN, top + 15f, leftPaint)
        }
        LedgerPdf.hairlineUnder(canvas, top + rowH)
        return top + rowH
    }
}

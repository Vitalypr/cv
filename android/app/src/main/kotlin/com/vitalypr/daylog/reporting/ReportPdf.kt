package com.vitalypr.daylog.reporting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.time.formatDate
import com.vitalypr.daylog.domain.time.formatDuration
import com.vitalypr.daylog.domain.time.formatMinutes
import com.vitalypr.daylog.domain.time.formatRange
import com.vitalypr.daylog.domain.time.hebrewDayNameFull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Seam for tests — Robolectric cannot run the native PdfDocument writer. */
fun interface DailyPdfRenderer {
    fun render(day: DaySnapshot): File
}

/**
 * Styled PDF of the daily report (spec §2.4 v0.5) in the approved "Ledger"
 * design: petrol double rule, title block, boxed summary row, hairline table
 * with time/activity/result columns, notes callout, numbered footer. Native
 * android.graphics.pdf — offline, dependency-free. RTL via StaticLayout.
 */
@Singleton
class ReportPdf @Inject constructor(
    @ApplicationContext private val context: Context,
) : DailyPdfRenderer {

    private object C {
        val petrol = Color.rgb(0x0B, 0x6E, 0x6A)
        val ink = Color.rgb(0x1B, 0x27, 0x33)
        val inkSecondary = Color.rgb(0x5A, 0x6B, 0x77)
        val inkMuted = Color.rgb(0x8A, 0x99, 0xA4)
        val line = Color.rgb(0xD8, 0xE1, 0xE5)
        val hairline = Color.rgb(0xED, 0xF1, 0xF3)
        val sendGreen = Color.rgb(0x17, 0x8A, 0x4C)
    }

    private companion object {
        const val PAGE_W = 595 // A4 points
        const val PAGE_H = 842
        const val MARGIN = 48f
        const val CONTENT_W = PAGE_W - 2 * MARGIN
        const val COL_TIME = 104f // right column: time ranges
        const val COL_RESULT = 74f // left column: results
        const val COL_GAP = 10f
    }

    override fun render(day: DaySnapshot): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        drawReport(page.canvas, day)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "daylog-${day.date}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** All drawing, PdfDocument-free — unit-tested against a bitmap canvas. */
    fun drawReport(canvas: Canvas, day: DaySnapshot) {
        var y = drawRulesAndTitle(canvas, day)
        y = drawSummaryBox(canvas, day, y + 26f)

        if (day.fieldJobs.isNotEmpty()) {
            y = drawSectionLabel(canvas, "עבודות שטח", y + 30f)
            day.fieldJobs.forEach { job ->
                val range = job.startMin?.let { formatRange(it, job.endMin) } ?: ""
                val text = job.title + (job.locationText?.let { " · $it" } ?: "")
                y = drawTableRow(canvas, range, text, result = "", y)
            }
        }

        val acts = day.activities.sortedWith(compareBy(nullsLast()) { it.startMin })
        if (acts.isNotEmpty()) {
            y = drawSectionLabel(canvas, "פעילויות", y + 30f)
            acts.forEach { a ->
                val range = a.startMin?.let { formatRange(it, a.endMin) } ?: ""
                val text = a.category + (if (a.note.isNotBlank()) " — ${a.note.trim()}" else "")
                y = drawTableRow(canvas, range, text, a.result.trim(), y)
            }
        }

        if (day.notes.isNotBlank()) {
            y = drawSectionLabel(canvas, "הערות", y + 30f)
            y = drawNotes(canvas, day.notes.trim(), y + 4f)
        }

        drawFooter(canvas, day)
    }

    private fun drawRulesAndTitle(canvas: Canvas, day: DaySnapshot): Float {
        // Double rule: 3pt + 1pt petrol.
        canvas.drawRect(MARGIN, 40f, PAGE_W - MARGIN, 43f, fill(C.petrol))
        canvas.drawRect(MARGIN, 48f, PAGE_W - MARGIN, 49f, fill(C.petrol))

        // Title at the right, date block at the left.
        rtlText(canvas, "דוח עבודה יומי", 68f, textPaint(27f, C.ink, bold = true), CONTENT_W)
        val datePaint = textPaint(16f, C.petrol, bold = true).apply { textAlign = Paint.Align.LEFT }
        canvas.drawText(formatDate(day.date), MARGIN, 88f, datePaint)
        val dayPaint = textPaint(12f, C.inkSecondary).apply { textAlign = Paint.Align.LEFT }
        canvas.drawText(hebrewDayNameFull(day.date), MARGIN, 106f, dayPaint)
        return 116f
    }

    private fun drawSummaryBox(canvas: Canvas, day: DaySnapshot, top: Float): Float {
        val boxH = 56f
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C.line; style = Paint.Style.STROKE; strokeWidth = 1f
        }
        canvas.drawRoundRect(RectF(MARGIN, top, PAGE_W - MARGIN, top + boxH), 4f, 4f, stroke)

        val fieldMin = day.fieldJobs
            .filter { it.startMin != null && it.endMin != null && it.endMin!! > it.startMin!! }
            .sumOf { it.endMin!! - it.startMin!! }
        val arr = day.arrivalMin
        val dep = day.departureMin
        val total = if (arr != null && dep != null && dep > arr) formatDuration(dep - arr) else "—"
        val cells = listOf(
            "כניסה" to (arr?.let(::formatMinutes) ?: "—"),
            "יציאה" to (dep?.let(::formatMinutes) ?: "—"),
            "סה״כ שעות" to total,
            "שטח" to (if (fieldMin > 0) formatDuration(fieldMin) else "—"),
        )
        val cellW = CONTENT_W / cells.size
        cells.forEachIndexed { i, (label, value) ->
            // RTL: first cell at the right.
            val cellRight = PAGE_W - MARGIN - i * cellW
            val cx = cellRight - cellW / 2
            centered(canvas, label, cx, top + 19f, textPaint(9.5f, C.inkMuted))
            centered(canvas, value, cx, top + 43f, textPaint(18f, C.ink, bold = true))
            if (i > 0) {
                canvas.drawRect(cellRight - 0.5f, top + 8f, cellRight + 0.5f, top + boxH - 8f, fill(C.hairline))
            }
        }
        return top + boxH
    }

    private fun drawSectionLabel(canvas: Canvas, label: String, top: Float): Float {
        // No letterSpacing: it breaks Hebrew glyph shaping (Latin-caps convention only).
        rtlText(canvas, label, top, textPaint(10.5f, C.inkMuted, bold = true), CONTENT_W)
        return top + 18f
    }

    /** One hairline table row: time (right col), text (middle), result (left col, green). */
    private fun drawTableRow(canvas: Canvas, time: String, text: String, result: String, top: Float): Float {
        val textW = (CONTENT_W - COL_TIME - COL_RESULT - 2 * COL_GAP).toInt()
        val layout = staticLayout(text, textPaint(12f, C.ink), textW)
        val rowH = maxOf(layout.height.toFloat() + 12f, 26f)

        if (time.isNotBlank()) {
            val timePaint = textPaint(11f, C.inkSecondary).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(time, PAGE_W - MARGIN, top + 15f, timePaint)
        }
        canvas.save()
        canvas.translate(MARGIN + COL_RESULT + COL_GAP, top + 4f)
        layout.draw(canvas)
        canvas.restore()
        if (result.isNotBlank()) {
            val resPaint = textPaint(11f, C.sendGreen, bold = true).apply { textAlign = Paint.Align.LEFT }
            canvas.drawText(result, MARGIN, top + 15f, resPaint)
        }
        canvas.drawRect(MARGIN, top + rowH - 1f, PAGE_W - MARGIN, top + rowH - 0.25f, fill(C.hairline))
        return top + rowH
    }

    private fun drawNotes(canvas: Canvas, notes: String, top: Float): Float {
        val textW = (CONTENT_W - 16f).toInt()
        val layout = staticLayout(notes, textPaint(12f, C.ink), textW)
        // Petrol callout bar on the reading (right) side.
        canvas.drawRect(PAGE_W - MARGIN - 3f, top, PAGE_W - MARGIN, top + layout.height + 8f, fill(C.petrol))
        canvas.save()
        canvas.translate(MARGIN, top + 4f)
        layout.draw(canvas)
        canvas.restore()
        return top + layout.height + 8f
    }

    private fun drawFooter(canvas: Canvas, day: DaySnapshot) {
        canvas.drawRect(MARGIN, PAGE_H - 46f, PAGE_W - MARGIN, PAGE_H - 45.5f, fill(C.line))
        val right = textPaint(9f, C.inkMuted).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("יומן עבודה · DayLog", PAGE_W - MARGIN, PAGE_H - 30f, right)
        val left = textPaint(9f, C.inkMuted).apply { textAlign = Paint.Align.LEFT }
        canvas.drawText("דוח ${day.date.dayOfYear}/${day.date.year}", MARGIN, PAGE_H - 30f, left)
    }

    // --- primitives ---

    private fun rtlText(canvas: Canvas, text: String, top: Float, paint: TextPaint, width: Float) {
        val layout = staticLayout(text, paint, width.toInt())
        canvas.save()
        canvas.translate(MARGIN, top - paint.textSize)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun centered(canvas: Canvas, text: String, cx: Float, baseline: Float, paint: TextPaint) {
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, cx, baseline, paint)
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3f, 1f)
            .build()

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
    }
}

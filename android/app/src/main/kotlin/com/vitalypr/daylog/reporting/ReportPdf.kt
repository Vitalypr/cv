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
import com.vitalypr.daylog.domain.time.hebrewDayName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Styled PDF rendering of the daily report (spec §2.4 v0.5). Native
 * android.graphics.pdf — offline, dependency-free. RTL text via StaticLayout
 * with the RTL heuristic; brand tokens mirror ui/theme (petrol/ink/ground).
 */
/** Seam for tests — Robolectric cannot run the native PdfDocument writer. */
fun interface DailyPdfRenderer {
    fun render(day: DaySnapshot): File
}

@Singleton
class ReportPdf @Inject constructor(
    @ApplicationContext private val context: Context,
) : DailyPdfRenderer {

    private object C {
        val petrol = Color.rgb(0x0B, 0x6E, 0x6A)
        val petrolDeep = Color.rgb(0x08, 0x52, 0x50)
        val ink = Color.rgb(0x1B, 0x27, 0x33)
        val inkSecondary = Color.rgb(0x5A, 0x6B, 0x77)
        val card = Color.rgb(0xF2, 0xF6, 0xF7)
        val sendGreen = Color.rgb(0x17, 0x8A, 0x4C)
        val white = Color.WHITE
    }

    private companion object {
        const val PAGE_W = 595 // A4 points
        const val PAGE_H = 842
        const val MARGIN = 44f
        const val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()
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
        var y = drawHeader(canvas, day)

        y = drawTimesRow(canvas, day, y + 24f)

        if (day.fieldJobs.isNotEmpty()) {
            y = drawSection(canvas, "🚗  עבודות שטח", y + 20f)
            day.fieldJobs.forEach { job ->
                val js = job.startMin
                val range = if (js != null) " (${formatRange(js, job.endMin)})" else ""
                y = drawBody(canvas, "• ${job.title}$range", y + 6f)
            }
        }

        val acts = day.activities.sortedWith(compareBy(nullsLast()) { it.startMin })
        if (acts.isNotEmpty()) {
            y = drawSection(canvas, "✅  פעילויות", y + 20f)
            acts.forEach { a ->
                val s0 = a.startMin
                var line = "• ${a.category}"
                line += if (s0 != null) " (${formatRange(s0, a.endMin)})" else ""
                if (a.note.isNotBlank()) line += " — ${a.note.trim()}"
                y = drawBody(canvas, line, y + 6f)
                if (a.result.isNotBlank()) {
                    y = drawBody(canvas, "   תוצאה: ${a.result.trim()}", y + 2f, color = C.sendGreen)
                }
            }
        }

        if (day.notes.isNotBlank()) {
            y = drawSection(canvas, "📝  הערות", y + 20f)
            y = drawBody(canvas, day.notes.trim(), y + 6f)
        }

        drawFooter(canvas)
    }

    private fun drawHeader(canvas: Canvas, day: DaySnapshot): Float {
        val headerH = 110f
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), headerH, fill(C.petrol))
        canvas.drawRect(0f, headerH, PAGE_W.toFloat(), headerH + 4f, fill(C.petrolDeep))

        rtlText(canvas, "דוח יומי", MARGIN, 30f, textPaint(24f, C.white, bold = true))
        rtlText(
            canvas, "${hebrewDayName(day.date)} ${formatDate(day.date)}",
            MARGIN, 66f, textPaint(14f, Color.argb(230, 255, 255, 255)),
        )
        return headerH + 4f
    }

    private fun drawTimesRow(canvas: Canvas, day: DaySnapshot, top: Float): Float {
        val cardH = 74f
        canvas.drawRoundRect(
            RectF(MARGIN, top, PAGE_W - MARGIN, top + cardH), 12f, 12f, fill(C.card),
        )
        val third = (PAGE_W - 2 * MARGIN) / 3f
        val arr = day.arrivalMin
        val dep = day.departureMin
        val dur = if (arr != null && dep != null && dep > arr) formatDuration(dep - arr) else "—"
        val cells = listOf(
            "כניסה" to (day.arrivalMin?.let(::formatMinutes) ?: "—"),
            "יציאה" to (day.departureMin?.let(::formatMinutes) ?: "—"),
            "סה״כ" to dur,
        )
        // RTL order: first cell at the right.
        cells.forEachIndexed { i, (label, value) ->
            val cellRight = PAGE_W - MARGIN - i * third
            val cx = cellRight - third / 2
            centeredText(canvas, label, cx, top + 24f, textPaint(11f, C.inkSecondary))
            centeredText(canvas, value, cx, top + 52f, textPaint(20f, C.petrolDeep, bold = true))
        }
        return top + cardH
    }

    private fun drawSection(canvas: Canvas, title: String, top: Float): Float {
        rtlText(canvas, title, MARGIN, top, textPaint(14f, C.petrol, bold = true))
        return top + 22f
    }

    private fun drawBody(canvas: Canvas, text: String, top: Float, color: Int = C.ink): Float {
        val paint = textPaint(12f, color)
        val layout = staticLayout(text, paint)
        canvas.save()
        canvas.translate(MARGIN, top)
        layout.draw(canvas)
        canvas.restore()
        return top + layout.height
    }

    private fun drawFooter(canvas: Canvas) {
        centeredText(
            canvas, "הופק על ידי יומן עבודה · DayLog",
            PAGE_W / 2f, PAGE_H - 30f, textPaint(9f, C.inkSecondary),
        )
    }

    private fun rtlText(canvas: Canvas, text: String, x: Float, top: Float, paint: TextPaint) {
        val layout = staticLayout(text, paint)
        canvas.save()
        canvas.translate(x, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun centeredText(canvas: Canvas, text: String, cx: Float, baseline: Float, paint: TextPaint) {
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, cx, baseline, paint)
    }

    private fun staticLayout(text: String, paint: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_W)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1f)
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

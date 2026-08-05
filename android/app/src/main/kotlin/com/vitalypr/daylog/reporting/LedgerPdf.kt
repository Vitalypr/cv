package com.vitalypr.daylog.reporting

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

/**
 * Shared primitives of the approved "Ledger" PDF design — one visual system for
 * the daily report and the period summaries. A4 geometry, petrol double rule,
 * boxed summary cells, hairline rows, footer rule.
 */
internal object LedgerPdf {

    const val PAGE_W = 595 // A4 points
    const val PAGE_H = 842
    const val MARGIN = 48f
    const val CONTENT_W = PAGE_W - 2 * MARGIN

    object C {
        val petrol = Color.rgb(0x0B, 0x6E, 0x6A)
        val ink = Color.rgb(0x1B, 0x27, 0x33)
        val inkSecondary = Color.rgb(0x5A, 0x6B, 0x77)
        val inkMuted = Color.rgb(0x8A, 0x99, 0xA4)
        val line = Color.rgb(0xD8, 0xE1, 0xE5)
        val hairline = Color.rgb(0xED, 0xF1, 0xF3)
        val sendGreen = Color.rgb(0x17, 0x8A, 0x4C)
    }

    fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    fun textPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        // Never letterSpacing here — it breaks Hebrew shaping (docs/dev/gotchas.md).
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
    }

    fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3f, 1f)
            .build()

    /** Petrol double rule at the page top. */
    fun drawTopRules(canvas: Canvas) {
        canvas.drawRect(MARGIN, 40f, PAGE_W - MARGIN, 43f, fill(C.petrol))
        canvas.drawRect(MARGIN, 48f, PAGE_W - MARGIN, 49f, fill(C.petrol))
    }

    fun rtlText(canvas: Canvas, text: String, top: Float, paint: TextPaint, width: Float = CONTENT_W) {
        val layout = staticLayout(text, paint, width.toInt())
        canvas.save()
        canvas.translate(MARGIN, top - paint.textSize)
        layout.draw(canvas)
        canvas.restore()
    }

    fun centered(canvas: Canvas, text: String, cx: Float, baseline: Float, paint: TextPaint) {
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, cx, baseline, paint)
    }

    fun sectionLabel(canvas: Canvas, label: String, top: Float): Float {
        rtlText(canvas, label, top, textPaint(10.5f, C.inkMuted, bold = true))
        return top + 18f
    }

    /** Bordered N-cell summary row; first cell renders at the RIGHT (RTL). */
    fun summaryBox(canvas: Canvas, top: Float, cells: List<Pair<String, String>>): Float {
        val boxH = 56f
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C.line; style = Paint.Style.STROKE; strokeWidth = 1f
        }
        canvas.drawRoundRect(RectF(MARGIN, top, PAGE_W - MARGIN, top + boxH), 4f, 4f, stroke)
        val cellW = CONTENT_W / cells.size
        cells.forEachIndexed { i, (label, value) ->
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

    fun hairlineUnder(canvas: Canvas, y: Float) {
        canvas.drawRect(MARGIN, y - 1f, PAGE_W - MARGIN, y - 0.25f, fill(C.hairline))
    }

    fun footer(canvas: Canvas, leftText: String) {
        canvas.drawRect(MARGIN, PAGE_H - 46f, PAGE_W - MARGIN, PAGE_H - 45.5f, fill(C.line))
        val right = textPaint(9f, C.inkMuted).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("יומן עבודה · DayLog", PAGE_W - MARGIN, PAGE_H - 30f, right)
        val left = textPaint(9f, C.inkMuted).apply { textAlign = Paint.Align.LEFT }
        canvas.drawText(leftText, MARGIN, PAGE_H - 30f, left)
    }
}

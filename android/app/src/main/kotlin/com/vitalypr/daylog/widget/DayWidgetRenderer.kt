package com.vitalypr.daylog.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.RemoteViews
import com.vitalypr.daylog.R
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.time.formatMinutes

/**
 * What the widget shows for a given day. Derived by a pure function so the
 * decision (live clock vs. recorded value vs. special day) is unit-tested
 * without inflating anything.
 */
data class WidgetState(
    val specialDay: DayType? = null,
    val arrivalMin: Int? = null,
    val departureMin: Int? = null,
) {
    val isSpecialDay: Boolean get() = specialDay != null

    companion object {
        fun of(day: DaySnapshot?): WidgetState = when {
            day == null -> WidgetState()
            day.dayType != DayType.WORK -> WidgetState(specialDay = day.dayType)
            else -> WidgetState(arrivalMin = day.arrivalMin, departureMin = day.departureMin)
        }
    }
}

/** Builds the RemoteViews tree. Kept separate from the provider so it is testable. */
object DayWidgetRenderer {

    fun render(context: Context, state: WidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_day).apply {
            if (state.isSpecialDay) {
                // חופש/חג: no hours may be logged, so the actions are replaced entirely.
                setViewVisibility(R.id.widget_special_day, View.VISIBLE)
                setViewVisibility(R.id.widget_arrival, View.GONE)
                setViewVisibility(R.id.widget_departure, View.GONE)
                val label = context.getString(
                    if (state.specialDay == DayType.HOLIDAY) R.string.holiday else R.string.day_off,
                )
                setTextViewText(R.id.widget_special_day, context.getString(R.string.widget_special_day, label))
                return@apply
            }

            setViewVisibility(R.id.widget_special_day, View.GONE)
            setViewVisibility(R.id.widget_arrival, View.VISIBLE)
            setViewVisibility(R.id.widget_departure, View.VISIBLE)

            bindSlot(context, state.arrivalMin, R.id.widget_arrival_clock, R.id.widget_arrival_value)
            bindSlot(context, state.departureMin, R.id.widget_departure_clock, R.id.widget_departure_value)

            setOnClickPendingIntent(R.id.widget_arrival, actionIntent(context, WidgetActionReceiver.ACTION_ARRIVE))
            setOnClickPendingIntent(R.id.widget_departure, actionIntent(context, WidgetActionReceiver.ACTION_LEAVE))
        }

    /**
     * Unrecorded → live clock (the time a tap would write). Recorded → that value
     * with a ✓, so the home screen answers "did I log it?" at a glance. Tapping
     * always overwrites with the real current time either way.
     */
    private fun RemoteViews.bindSlot(context: Context, minutes: Int?, clockId: Int, valueId: Int) {
        if (minutes == null) {
            setViewVisibility(clockId, View.VISIBLE)
            setViewVisibility(valueId, View.GONE)
        } else {
            setViewVisibility(clockId, View.GONE)
            setViewVisibility(valueId, View.VISIBLE)
            setTextViewText(valueId, recordedLabel(context, minutes))
        }
    }

    /** The ✓ is a marker, not a headline — it stays subordinate to the time itself. */
    private fun recordedLabel(context: Context, minutes: Int): CharSequence {
        val text = context.getString(R.string.widget_recorded, formatMinutes(minutes))
        val mark = text.indexOf(CHECK)
        if (mark < 0) return text
        return SpannableString(text).apply {
            setSpan(RelativeSizeSpan(0.7f), mark, mark + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                ForegroundColorSpan(context.getColor(R.color.on_accent_muted)),
                mark, mark + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private const val CHECK = '✓'

    private fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (action == WidgetActionReceiver.ACTION_ARRIVE) RC_ARRIVE else RC_LEAVE,
        Intent(context, WidgetActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val RC_ARRIVE = 410
    private const val RC_LEAVE = 411
}

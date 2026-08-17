package com.vitalypr.daylog.domain.model

/**
 * Activities are logged as a duration in half-hour steps, not as a clock range
 * (v0.9, product-owner decision): what a day's work consisted of matters, the
 * minute it started does not, and stepping is faster than two time pickers.
 *
 * null means "not stated" — a bare category is still a valid entry (spec F4).
 */
object ActivityDuration {

    const val STEP_MINUTES = 30
    const val MAX_MINUTES = 12 * 60

    /** Unset → one step; then up by a step, capped. */
    fun increase(current: Int?): Int =
        ((current ?: 0) + STEP_MINUTES).coerceAtMost(MAX_MINUTES)

    /** Down by a step; below one step it returns to unset. */
    fun decrease(current: Int?): Int? =
        ((current ?: 0) - STEP_MINUTES).takeIf { it >= STEP_MINUTES }

    /** Snaps a stored value onto the step grid (migrated data, manual edits). */
    fun snap(minutes: Int?): Int? {
        if (minutes == null || minutes <= 0) return null
        val steps = Math.round(minutes.toDouble() / STEP_MINUTES).toInt().coerceAtLeast(1)
        return (steps * STEP_MINUTES).coerceAtMost(MAX_MINUTES)
    }
}

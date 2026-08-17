package com.vitalypr.daylog.domain.geo

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure rules that decide whether a geofence transition means anything.
 *
 * Why these exist: Play Services delivers transitions when it next gets a
 * location fix, not when the boundary was physically crossed. An exit that
 * happened at 17:30 can therefore land the next morning, out of order with the
 * arrival that follows it. Deciding from "now" alone produced exit suggestions
 * while the user was walking INTO the office. Every rule below keys off the
 * event's own timestamp and off whether we ever saw the matching entry.
 */
object GeofenceRules {

    /** Below this, a visit is a drive-past, not a work session. */
    val MIN_OFFICE_DWELL: Duration = Duration.ofMinutes(5)

    /**
     * "Did you work today?" is only a sensible question after a real stay. GPS
     * drifts badly indoors and can report an exit while the user sits at their
     * desk; without this floor that drift asked them to log a day they had just
     * started. A genuine forgot-to-log day is hours long, so nothing is lost.
     */
    val MIN_WORKDAY_DWELL: Duration = Duration.ofMinutes(30)

    /** Job fences are 2 km wide; crossing one takes minutes, visiting takes longer. */
    const val MIN_JOB_DWELL_MINUTES = 15

    /** A transition older than this is a catch-up delivery we cannot act on. */
    val MAX_EVENT_AGE: Duration = Duration.ofMinutes(60)

    /**
     * The logical day an exit belongs to, or null when the exit must be ignored.
     *
     * - No recorded entry → phantom exit (the delivery we never had an arrival for).
     * - Same day as the entry → that day.
     * - Entered yesterday, leaving before 04:00, yesterday still open → yesterday,
     *   matching the §6.2 past-midnight day-assignment rule.
     * - Anything else → a stale catch-up that belongs to no day we can trust.
     */
    fun exitDate(
        insideSince: LocalDateTime?,
        at: LocalDateTime,
        previousDayHasOpenArrival: Boolean,
    ): LocalDate? {
        if (insideSince == null) return null
        val entered = insideSince.toLocalDate()
        val left = at.toLocalDate()
        return when {
            entered == left -> left
            entered == left.minusDays(1) && at.hour < 4 && previousDayHasOpenArrival -> entered
            else -> null
        }
    }

    /** A visit too short to be real — the user drove past the office. */
    fun isDriveBy(insideSince: LocalDateTime, at: LocalDateTime): Boolean =
        Duration.between(insideSince, at) < MIN_OFFICE_DWELL

    /** Too old (catch-up delivery) or implausibly in the future (bad fix). */
    fun isStale(at: LocalDateTime, now: LocalDateTime): Boolean =
        at.isBefore(now.minus(MAX_EVENT_AGE)) || at.isAfter(now.plusMinutes(5))

    /**
     * Great-circle distance in metres. Used to drop job fences that sit on top of
     * the office — otherwise arriving at work also "arrives" at a client site.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a).coerceAtMost(1.0))
    }

    private const val EARTH_RADIUS_M = 6_371_008.8
}

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

    /**
     * The shortest stay that counts as having worked somewhere (product-owner
     * rule, v1.0). Below it the app never suggests a leaving time: crossing a
     * fence — a 2 km job radius especially — takes minutes, and indoor GPS drift
     * reports exits while the user sits at their desk. The arrival still stands,
     * flagged, because a brief visit might have been real; the departure does not,
     * because a departure the user did not make corrupts the day's hours.
     */
    val MIN_VISIT: Duration = Duration.ofHours(1)

    /** A transition older than this is a catch-up delivery we cannot act on. */
    val MAX_EVENT_AGE: Duration = Duration.ofMinutes(60)

    /** How long an exit waits for a re-entry before it is believed (spec §5.5). */
    val DEBOUNCE: Duration = Duration.ofMinutes(10)

    /**
     * Longer than this and the stored visit cannot be real: Play Services misses
     * exits routinely (Doze, OEM battery managers, a lost fix), and without this
     * bound one missed EXIT left the fence "inside" for ever — every later
     * arrival was written off as a duplicate and the feature went silent.
     */
    val MAX_VISIT: Duration = Duration.ofHours(14)

    /**
     * True when [at] cannot belong to the visit that began at [since] — a
     * different day, or an implausibly long stay. Such an entry opens a new visit
     * instead of being swallowed as a duplicate delivery.
     */
    fun startsNewVisit(since: LocalDateTime, at: LocalDateTime): Boolean =
        since.toLocalDate() != at.toLocalDate() || Duration.between(since, at) > MAX_VISIT

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

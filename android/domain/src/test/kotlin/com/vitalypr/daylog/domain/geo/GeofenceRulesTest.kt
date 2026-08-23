package com.vitalypr.daylog.domain.geo

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules that stop a late-delivered exit from being treated as a real one. */
class GeofenceRulesTest {

    private val mon = LocalDate.of(2026, 8, 3)
    private fun at(d: LocalDate, h: Int, m: Int) = LocalDateTime.of(d, java.time.LocalTime.of(h, m))

    // --- exitDate -----------------------------------------------------------

    @Test
    fun `exit without a recorded entry is ignored`() {
        // The reported bug: a catch-up EXIT arrives as the user walks in.
        assertNull(GeofenceRules.exitDate(insideSince = null, at = at(mon, 8, 20), previousDayHasOpenArrival = false))
    }

    @Test
    fun `normal same-day exit belongs to that day`() {
        assertEquals(
            mon,
            GeofenceRules.exitDate(at(mon, 8, 12), at(mon, 17, 35), previousDayHasOpenArrival = false),
        )
    }

    @Test
    fun `overnight shift attributes the exit to the day that is still open`() {
        assertEquals(
            mon,
            GeofenceRules.exitDate(at(mon, 22, 0), at(mon.plusDays(1), 1, 30), previousDayHasOpenArrival = true),
        )
    }

    @Test
    fun `next-morning delivery of yesterday's visit is dropped, not applied to today`() {
        // Entered yesterday morning; the exit only reaches us at 08:20 today.
        assertNull(
            GeofenceRules.exitDate(at(mon, 8, 12), at(mon.plusDays(1), 8, 20), previousDayHasOpenArrival = true),
        )
    }

    @Test
    fun `past-midnight exit is dropped when the previous day has no open arrival`() {
        assertNull(
            GeofenceRules.exitDate(at(mon, 22, 0), at(mon.plusDays(1), 1, 30), previousDayHasOpenArrival = false),
        )
    }

    @Test
    fun `exit long after midnight is not attributed backwards`() {
        assertNull(
            GeofenceRules.exitDate(at(mon, 22, 0), at(mon.plusDays(1), 6, 0), previousDayHasOpenArrival = true),
        )
    }

    // --- dwell & staleness --------------------------------------------------

    @Test
    fun `an hour-old transition is stale`() {
        assertTrue(GeofenceRules.isStale(at = at(mon, 7, 0), now = at(mon, 8, 20)))
    }

    @Test
    fun `a fresh transition is actionable`() {
        assertFalse(GeofenceRules.isStale(at = at(mon, 8, 12), now = at(mon, 8, 20)))
    }

    @Test
    fun `a timestamp from the future is rejected`() {
        assertTrue(GeofenceRules.isStale(at = at(mon, 9, 0), now = at(mon, 8, 20)))
    }

    // --- distance -----------------------------------------------------------

    @Test
    fun `distance between two points is metres`() {
        // Tel Aviv (32.0853, 34.7818) → Jerusalem (31.7683, 35.2137) ≈ 54 km.
        val d = GeofenceRules.distanceMeters(32.0853, 34.7818, 31.7683, 35.2137)
        assertTrue(d in 52_000.0..56_000.0, "expected ~54 km, got $d")
    }

    @Test
    fun `distance to itself is zero`() {
        assertEquals(0.0, GeofenceRules.distanceMeters(32.0, 34.8, 32.0, 34.8))
    }

    @Test
    fun `a job pin inside the office radius is near enough to be the same place`() {
        // 100 m north of the office, inside a 150 m office fence.
        val d = GeofenceRules.distanceMeters(32.0000, 34.8000, 32.0009, 34.8000)
        assertTrue(d < 150, "expected under 150 m, got $d")
    }
}

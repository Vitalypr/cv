package com.vitalypr.daylog.geofence

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * `triggeringLocation.time` is the age of the FIX, not of the crossing. Trusting
 * it wrote arrivals minutes early and — past the staleness bound — dropped the
 * transition altogether, which is one of the ways recording went silent.
 */
@RunWith(RobolectricTestRunner::class)
class EventTimeTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 4, 8, 20)
    private fun millis(at: LocalDateTime) = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun `a fresh fix is used as the event time`() {
        val fix = now.minusMinutes(2)
        assertEquals(fix, eventTime(millis(fix), now))
    }

    @Test fun `a stale cached fix falls back to delivery time, not a wrong hour`() {
        assertEquals(now, eventTime(millis(now.minusMinutes(45)), now))
    }

    @Test fun `an hours-old fix is no longer dropped - it records at delivery time`() {
        assertEquals(now, eventTime(millis(now.minusHours(3)), now))
    }

    @Test fun `a fix dated in the future is rejected`() {
        assertEquals(now, eventTime(millis(now.plusMinutes(30)), now))
    }

    @Test fun `no fix at all still yields an event time`() {
        assertEquals(now, eventTime(null, now))
        assertEquals(now, eventTime(0L, now))
    }
}

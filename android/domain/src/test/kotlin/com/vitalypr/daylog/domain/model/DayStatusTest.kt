package com.vitalypr.daylog.domain.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Status derivation per spec §6.3 — derived, never stored. */
class DayStatusTest {

    private val base = DaySnapshot(date = LocalDate.of(2026, 8, 4))

    @Test fun `empty day`() = assertEquals(DayStatus.EMPTY, base.status())

    @Test fun `any fact makes it logged`() {
        assertEquals(DayStatus.LOGGED, base.copy(arrivalMin = 492).status())
        assertEquals(DayStatus.LOGGED, base.copy(notes = "x").status())
        assertEquals(DayStatus.LOGGED, base.copy(activities = listOf(ActivityEntry("דיון"))).status())
    }

    @Test fun `reported and reported-edited`() {
        assertEquals(DayStatus.REPORTED, base.copy(arrivalMin = 1, reported = true).status())
        assertEquals(
            DayStatus.REPORTED_EDITED,
            base.copy(arrivalMin = 1, reported = true, editedAfterReport = true).status(),
        )
    }

    @Test fun `day type wins over everything`() {
        assertEquals(DayStatus.OFF, base.copy(dayType = DayType.OFF, reported = true).status())
        assertEquals(DayStatus.HOLIDAY, base.copy(dayType = DayType.HOLIDAY, arrivalMin = 1).status())
    }
}

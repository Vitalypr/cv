package com.vitalypr.daylog.reminder

import com.vitalypr.daylog.data.settings.Settings
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/** Next-eligible-day logic per spec F7/S4. Sun–Thu work-week (Israel default). */
class ReminderSchedulerTest {

    private val settings = Settings() // reportTime 17:45, workDays Sun–Thu
    private val noData: suspend (LocalDate) -> Boolean = { false }

    // 2026-08-04 is a Tuesday.
    @Test fun `before report time on a workday fires today`() = runTest {
        val now = LocalDateTime.of(2026, 8, 4, 12, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 4, 17, 45),
            ReminderScheduler.nextReminderAt(now, settings, noData),
        )
    }

    @Test fun `after report time rolls to next workday`() = runTest {
        val now = LocalDateTime.of(2026, 8, 4, 18, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 5, 17, 45),
            ReminderScheduler.nextReminderAt(now, settings, noData),
        )
    }

    // 2026-08-06 is a Thursday; Friday+Saturday are off → next is Sunday 09.08.
    @Test fun `weekend skipped when no data`() = runTest {
        val now = LocalDateTime.of(2026, 8, 6, 18, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 9, 17, 45),
            ReminderScheduler.nextReminderAt(now, settings, noData),
        )
    }

    @Test fun `weekend day WITH data gets its reminder`() = runTest {
        val now = LocalDateTime.of(2026, 8, 6, 18, 0)
        val friday = LocalDate.of(2026, 8, 7)
        val hasData: suspend (LocalDate) -> Boolean = { it == friday }
        assertEquals(
            LocalDateTime.of(2026, 8, 7, 17, 45),
            ReminderScheduler.nextReminderAt(now, settings, hasData),
        )
    }
}

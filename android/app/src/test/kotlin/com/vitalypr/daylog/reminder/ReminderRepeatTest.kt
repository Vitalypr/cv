package com.vitalypr.daylog.reminder

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.FakeSettingsSource
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Spec §5.5: the +2h repeat is skipped past 23:30 and never crosses midnight. */
@RunWith(RobolectricTestRunner::class)
class ReminderRepeatTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: DayLogDb
    private var nowDt: LocalDateTime = LocalDateTime.of(2026, 8, 4, 17, 45)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java).allowMainThreadQueries().build()
    }

    @After fun teardown() = db.close()

    private fun scheduler() = ReminderScheduler(
        context,
        FakeSettingsSource(),
        DayRepository(db.dayDao(), db.categoryDao()) { Instant.now() },
    ) { nowDt }

    @Suppress("DEPRECATION")
    private fun nextScheduledAlarm(): Long? {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // peek, not get — getNextScheduledAlarm() removes the alarm from the shadow.
        return shadowOf(am).peekNextScheduledAlarm()?.triggerAtTime
    }

    @Test fun `repeat at 17_45 schedules 19_45`() {
        scheduler().scheduleRepeat()
        val expected = LocalDateTime.of(2026, 8, 4, 19, 45)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val actual = nextScheduledAlarm()
        assertNotNull(actual)
        assertEquals(expected, actual)
    }

    @Test fun `repeat past 23_30 cutoff is skipped`() {
        nowDt = LocalDateTime.of(2026, 8, 4, 22, 0) // +2h = 00:00 next day > 23:30
        scheduler().scheduleRepeat()
        assertNull(nextScheduledAlarm())
    }
}

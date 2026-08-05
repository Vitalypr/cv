package com.vitalypr.daylog.reminder

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.FakeSettingsSource
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.notifications.Channels
import com.vitalypr.daylog.notifications.Notifier
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One test per row of the §5.5 report-time decision table. */
@RunWith(RobolectricTestRunner::class)
class ReminderEngineTest {

    private lateinit var db: DayLogDb
    private lateinit var repo: DayRepository
    private lateinit var engine: ReminderEngine
    private lateinit var nm: NotificationManager

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 8, 4)
    private val nowDt = LocalDateTime.of(2026, 8, 4, 17, 45)

    @Before fun setup() {
        Channels.ensure(context)
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        repo = DayRepository(db.dayDao(), db.categoryDao()) { Instant.parse("2026-08-04T14:45:00Z") }
        val scheduler = ReminderScheduler(context, FakeSettingsSource(), repo) { nowDt }
        engine = ReminderEngine(repo, Notifier(context), scheduler) { nowDt }
        nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @After fun teardown() = db.close()

    private fun postedTitles(): List<String> =
        shadowOf(nm).allNotifications.map { shadowOf(it).contentTitle.toString() }

    @Test fun `arrival and departure set - report-ready with send action`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        repo.setDeparture(today, 1055, TimeSource.MANUAL)
        engine.onReminderFired(isRepeat = false)
        assertEquals(listOf("הדוח היומי מוכן"), postedTitles())
        val actions = shadowOf(nm).allNotifications.single().actions.map { it.title.toString() }
        assertTrue("שליחה לוואטסאפ" in actions)
    }

    @Test fun `arrival only - still at office variant`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        engine.onReminderFired(isRepeat = false)
        assertEquals(listOf("עדיין במשרד?"), postedTitles())
    }

    @Test fun `nothing logged - complete your log`() = runTest {
        engine.onReminderFired(isRepeat = false)
        assertEquals(listOf("השלם את יומן היום"), postedTitles())
    }

    @Test fun `day off posts nothing`() = runTest {
        repo.setDayType(today, DayType.OFF)
        engine.onReminderFired(isRepeat = false)
        assertTrue(postedTitles().isEmpty())
    }

    @Test fun `holiday posts nothing`() = runTest {
        repo.setDayType(today, DayType.HOLIDAY)
        engine.onReminderFired(isRepeat = false)
        assertTrue(postedTitles().isEmpty())
    }

    @Test fun `already reported posts nothing`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        repo.setDeparture(today, 1055, TimeSource.MANUAL)
        repo.markReported(today)
        engine.onReminderFired(isRepeat = false)
        assertTrue(postedTitles().isEmpty())
    }

    @Test fun `reported but edited afterwards nags again`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        repo.setDeparture(today, 1055, TimeSource.MANUAL)
        repo.markReported(today)
        repo.setNotes(today, "עוד משהו")
        engine.onReminderFired(isRepeat = false)
        assertEquals(listOf("הדוח היומי מוכן"), postedTitles())
    }
}

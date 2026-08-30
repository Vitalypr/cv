package com.vitalypr.daylog.geofence

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.FakeSettingsSource
import com.vitalypr.daylog.InMemoryFenceStateStore
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.report.ReportBuilder
import com.vitalypr.daylog.domain.stats.StatsCalculator
import com.vitalypr.daylog.notifications.Channels
import com.vitalypr.daylog.notifications.Notifier
import com.vitalypr.daylog.widget.WidgetActions
import com.vitalypr.daylog.widget.WidgetState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The ordinary day, end to end: arrive in the morning, leave in the evening, and
 * everything downstream — the widget, the report, the day total — agrees.
 *
 * This is the integration seam the v2.0 session model had to keep working: the
 * office fence writes hours at the **base and nowhere else**, home and field
 * work stay something the user enters by hand, and nothing the fence does may
 * touch a session the user typed.
 */
@RunWith(RobolectricTestRunner::class)
class WorkdayFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: DayLogDb
    private lateinit var repo: DayRepository
    private lateinit var engine: GeofenceEngine
    private lateinit var widget: WidgetActions
    private lateinit var settings: FakeSettingsSource
    private lateinit var nm: NotificationManager

    // Tuesday, a working day.
    private val today = LocalDate.of(2026, 8, 4)
    private var nowDt = LocalDateTime.of(2026, 8, 4, 8, 12)

    @Before fun setup() {
        Channels.ensure(context)
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        repo = DayRepository(db.dayDao(), db.categoryDao()) { Instant.now() }
        settings = FakeSettingsSource() // silentGeofence = true, the v1.1 default
        engine = GeofenceEngine(
            context, repo, settings, Notifier(context), InMemoryFenceStateStore(),
            GeofenceLog(context), com.vitalypr.daylog.widget.DayWidgetRefresher(context),
        ) { nowDt }
        widget = WidgetActions(repo) { nowDt }
        nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @After fun teardown() = db.close()

    private fun at(hour: Int, minute: Int, date: LocalDate = today) =
        LocalDateTime.of(date, LocalTime.of(hour, minute))

    private suspend fun arriveAt(hour: Int, minute: Int) {
        nowDt = at(hour, minute)
        engine.onEnter(nowDt)
    }

    private suspend fun leaveAt(hour: Int, minute: Int) {
        nowDt = at(hour, minute)
        engine.onExitDetected(nowDt)
        engine.onExitConfirmedByDebounce()
    }

    private suspend fun day(): DaySnapshot = repo.getDay(today)!!

    /**
     * Morning in, evening out: one base session from the fence alone, booked in
     * quarter hours — 08:12 in becomes 08:00, 17:35 out becomes 17:45.
     */
    @Test fun `arriving in the morning and leaving in the evening records the day`() = runTest {
        arriveAt(8, 12)
        leaveAt(17, 35)

        val session = day().sessions.single()
        assertEquals(WorkMode.BASE, session.mode)
        assertEquals(8 * 60, session.startMin)
        assertEquals(17 * 60 + 45, session.endMin)
        assertEquals(TimeSource.GEOFENCE, session.startSource)
        assertEquals(9 * 60 + 45, StatsCalculator.dayMinutes(day()).total)
    }

    /** Suggestion mode: nothing is written until the user taps אישור. */
    @Test fun `in suggestion mode the same day is offered, then confirmed`() = runTest {
        settings.update { it.copy(silentGeofence = false) }

        arriveAt(8, 12)
        assertTrue(repo.getDay(today) == null || day().sessions.isEmpty())
        // The prompt offers the quarter it will actually book, not the raw fix.
        assertEquals(listOf("הגעת למשרד?"), postedTitles())
        engine.confirmArrival(today, 8 * 60)

        leaveAt(17, 35)
        assertTrue(postedTitles().any { it == "יציאה 17:45?" })
        engine.confirmDeparture(today, 17 * 60 + 45)

        val session = day().sessions.single()
        assertEquals(8 * 60 to 17 * 60 + 45, session.startMin to session.endMin)
    }

    /** The fence speaks for the base only — home and field work stay manual. */
    @Test fun `the office fence never invents home or field work`() = runTest {
        arriveAt(8, 12)
        leaveAt(17, 35)
        assertTrue(day().sessions.all { it.mode == WorkMode.BASE })
    }

    /** Evening work from home is added by hand and the fence leaves it alone. */
    @Test fun `a manual home session coexists with the recorded base day`() = runTest {
        arriveAt(8, 12)
        leaveAt(17, 35)

        repo.addSession(today, WorkMode.HOME, startMin = 20 * 60, endMin = 22 * 60)
        // A late catch-up exit must not reach into the home session.
        nowDt = at(23, 0)
        engine.onExitDetected(nowDt)
        engine.onExitConfirmedByDebounce()

        val home = day().sessions.first { it.mode == WorkMode.HOME }
        assertEquals(20 * 60 to 22 * 60, home.startMin to home.endMin)
        assertEquals(17 * 60 + 45, day().sessions.first { it.mode == WorkMode.BASE }.endMin)

        val minutes = StatsCalculator.dayMinutes(day())
        assertEquals(9 * 60 + 45, minutes.base)
        assertEquals(2 * 60, minutes.home)
        assertEquals(11 * 60 + 45, minutes.total)
        assertTrue(ReportBuilder.daily(day()).contains("סה״כ 11:45 — בסיס 9:45 · בית 2:00"))
    }

    /** The home screen answers "did it log?" from the same base session. */
    @Test fun `the widget shows what the fence recorded`() = runTest {
        arriveAt(8, 12)
        assertEquals(8 * 60, WidgetState.of(day()).arrivalMin)
        assertNull(WidgetState.of(day()).departureMin) // still a live clock

        leaveAt(17, 35)
        val state = WidgetState.of(day())
        assertEquals(8 * 60, state.arrivalMin)
        assertEquals(17 * 60 + 45, state.departureMin)
    }

    /** A widget tap is the correction path: MANUAL wins and the fence stops touching it. */
    @Test fun `a widget tap corrects a geofence time and locks it`() = runTest {
        arriveAt(8, 12)

        nowDt = at(8, 37)
        widget.record(arrival = true) // "no, I actually got in later"
        assertEquals(8 * 60 + 30, day().sessions.single().startMin) // booked down

        assertEquals(TimeSource.MANUAL, day().sessions.single().startSource)

        nowDt = at(16, 52)
        widget.record(arrival = false)
        leaveAt(18, 0) // a later fence exit must not move a hand-logged leaving time

        val session = day().sessions.single()
        assertEquals(17 * 60, session.endMin) // booked up

        assertEquals(TimeSource.MANUAL, session.endSource)
    }

    private fun postedTitles(): List<String> =
        shadowOf(nm).allNotifications.map { shadowOf(it).contentTitle.toString() }
}

# Testing

TDD is the workflow, not a phase: failing test → implementation → green → refactor. A spec rule without a named test doesn't exist.

## Pyramid & commands

| Gate | Command | Framework |
|---|---|---|
| Domain rules | `gradle :domain:test` | JUnit4 + kotlin.test — seconds-fast, run constantly |
| Data / VM / system | `gradle :app:testDebugUnitTest` | Robolectric (Room in-memory, shadows for AlarmManager/PendingIntent), Turbine for Flows, `kotlinx-coroutines-test` with injected dispatchers + fixed `Clock` |
| Visual | same task, Roborazzi | screenshots recorded to `app/src/test/snapshots/`; verify mode in CI-like runs; re-record ONLY for approved design changes |

## Spec traceability (keep current)

| Spec | Tests |
|---|---|
| §2.4 report template, RLM, omit-empty, activity fragments | `domain` `ReportBuilderTest` (golden strings) |
| F4 activity durations (30-min steps, unset) | `ActivityDurationTest`, `TimesTest` |
| F4a projects (mandatory, archived when used) | `ProjectRepositoryTest`, `TodayViewModelTest` |
| F12a full backup round trip (every table + setting) | `BackupRoundTripTest` |
| F10a deleting a day (nothing orphaned, gone from range queries) | `DayRepositoryTest`, `TodayViewModelTest` |
| §5.3 quarter period, past-period navigation | `StatsViewModelTest` |
| §5.3 hours per project + unallocated remainder | domain `StatsCalculatorTest`, `ReportBuilderPeriodTest`; `StatsViewModelTest`, `StatsScreenshotTest` |
| §2.5 period summaries, hours rule | `StatsCalculatorTest`, `ReportBuilderPeriodTest` |
| §6.2 quarter-hour booking (start down, end up, idempotent, past midnight) | domain `WorkTimeStepTest`; `DayRepositoryTest`, `WorkdayFlowTest`, `WidgetActionsTest` |
| §6.2 time model >24h, day boundary | `TimesTest`, `ReportBuilderOvernightTest` |
| §6.3 schema, DayWithEntries, status derivation | `app` `DayRepositoryTest`, domain `DayStatusTest` |
| §6.3 v2.0 clean break (older DB rebuilt + re-seeded, never a crash) | `app` `SchemaResetTest` |
| §6.3 Migration 6→7 (result retired; rows, ids and links survive) | `app` `MigrationTest` |
| F1 v2.0 work sessions (several a day, per mode; second visit ≠ overwrite) | `DayRepositoryTest`, domain `StatsCalculatorTest`, `GeofenceEngineTest` |
| §5.1 time budget (worked vs. allocated vs. left, over-allocation) | domain `TimeBudgetTest`, `TodayViewModelTest`; `TodayScreenshotTest` — `today_full` (filling), `today_balanced` (green), `today_over_allocated` (red) |
| §5.5 notification variants table | `ReminderVariantsTest` — one test per table row |
| §6.6 the ordinary day end to end (fence → widget → report), base-only writes | `WorkdayFlowTest` |
| §6.6 geofence decision table + invariants | `GeofenceEngineTest` — one test per row, incl. never-overwrite-MANUAL and second-visit-opens-a-second-session |
| §6.6 recovery from dropped transitions (missed exit, stranded debounce, two visits) | domain `FenceRecoveryTest`; `GeofenceEngineTest` automatic-mode rows; `EventTimeTest` (fix clamping) |
| §6.6 office decision table as a state machine (all states x events, sequences) | domain `FenceMachineTest` (~35 cases incl. replayed days) |
| §6.6 ordering invariants (occupancy, event day, staleness, dwell) | domain `GeofenceRulesTest`; `GeofenceEngineTest` (phantom exit, drift, overnight, drive-past), `JobLocationEngineTest` |
| §6.4/§6.5 share intent, trampoline activity, alarm re-arm | `ReportShareTest`, `SendReportActivityTest`, `ReminderSchedulerTest`, `BootReceiverTest` |
| §5.6 widget: override rules, special day, state selection | `WidgetActionsTest`, `WidgetStateTest`, `DayWidgetScreenshotTest` (inflates the real RemoteViews at 250x40dp) |
| F12a full backup (every table + setting, atomic restore, bad file refused) | `BackupRoundTripTest` |
| N3 no INTERNET | `ManifestGuardTest` (parses merged manifest) |
| UI per mockup | Roborazzi snapshots per screen × config |

## Visual configs (every screen)

1. **S23 Ultra**: Robolectric qualifiers `w384dp-h832dp-xxxhdpi` + `RuntimeEnvironment` locale `he-IL`, `LayoutDirection.Rtl` (S23U: 1440×3088 @ ~500dpi ⇒ ~412dp width class — matches standard phone width class, which is why w384–412dp is the right proxy).
2. **Small phone**: `w320dp-h640dp-xhdpi` — layout must not clip.
Screens are snapshotted per state (empty day / full day with base+home+field / short visit / over-allocated / off day / reported).

## Device checklist — Galaxy S23 Ultra (Phase 8, manual)

- [ ] Install debug APK (`adb install`), Hebrew locale: full RTL mirroring incl. back-swipe affordances
- [ ] One UI: all 4 notification channels visible & configurable; heads-up shows action buttons
- [ ] Battery: app set to "Unrestricted" → geofence prompts survive overnight Doze; document this step in Settings help text
- [ ] Geofence field test: office entry/exit prompts, lunch re-entry cancels pending suggestion
- [ ] WhatsApp handoff: report → share → group → send; Hebrew renders (RLM check) in the actual group
- [ ] Reminder fires within 10 min of configured time with screen off
- [ ] 120 Hz: History/Stats scroll without jank; charts crisp at 1440p
- [ ] Edge screen: no touch targets within 16dp of curved edges on Today's steppers

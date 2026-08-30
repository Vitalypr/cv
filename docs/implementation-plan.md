# DayLog — Implementation Plan

**Source of truth:** `docs/daylog-spec.md` (v0.4, approved). This plan turns it into phased, test-driven implementation. Every phase has an explicit definition of done and its own test gate; a phase is not complete until its gate passes.

## Engineering principles

1. **TDD per feature:** write the failing test first for every domain rule and ViewModel behavior; UI gets state tests + screenshot tests. No feature merges without its tests.
2. **Modular by dependency direction:** `:domain` is pure Kotlin (zero Android imports) — business rules compile and test on the JVM in seconds. `:app` contains Android layers (data/Room, UI/Compose, system services) and depends on `:domain`, never the reverse.
3. **No patches:** when a test exposes a design flaw, fix the design (RCA → fix → re-validate), don't special-case the test.
4. **UI is replaceable:** all screens render from immutable `UiState` data classes produced by ViewModels; changing visual design touches only `ui/` composables and `ui/theme/`. No business logic in composables.
5. **Every rule in the spec has a named test.** The traceability table in `docs/dev/testing.md` maps spec sections → test classes.

## Test pyramid

| Layer | Framework | Runs on | What it proves |
|---|---|---|---|
| Domain rules | JUnit4 + kotlin.test | JVM (`:domain:test`) | report golden strings (RLM/RTL), time model incl. >24h, stats math |
| Data layer | Robolectric + Room in-memory | JVM (`:app:testDebugUnitTest`) | DAO queries, migrations, repository flows |
| ViewModels | JUnit + Turbine + coroutines-test | JVM | state transitions, day lifecycle |
| System glue | Robolectric shadows | JVM | alarm scheduling, boot receiver, share-intent construction, notification variants |
| Visual | Roborazzi + Robolectric RTL/Hebrew config | JVM screenshots | every screen at S23 Ultra metrics (1440×3088 @ 3.5x, sw411dp) + a small-phone config |
| Manual/device | checklist | Galaxy S23 Ultra | geofence field test, WhatsApp handoff, One UI notification behavior |

## Phases

**Phase 0 — Infrastructure** *(gate: `gradle :domain:test :app:assembleDebug` green)*
Gradle multi-module build (version catalog), Android SDK 36, Kotlin 2.2/KSP2, Compose BOM, Hilt, Room, Robolectric+Roborazzi wiring, CLAUDE.md + discipline docs, CI-able commands, merged-manifest check that INTERNET permission is absent (spec N3).

**Phase 1 — Domain core** *(gate: `:domain:test` green, 100% of report/stats rules covered)*
Time model (minutes-from-midnight, >1440 = past midnight), Hebrew formatting, `ReportBuilder` (daily + period summaries, golden-string tested against spec §2.4/§2.5 verbatim), `StatsCalculator` (hours rule: office span + non-overlapping field time via interval merge; averages; off/holiday counts), day-status derivation.

**Phase 2 — Data layer** *(gate: DAO + repository + migration tests green)*
Room schema per spec §6.3 (schema version 1, exported schemas committed), DAOs with `DayWithEntries` transaction query, `DayRepository`, DataStore settings (work-week, report time, office location, toggles), seed of 8 Hebrew categories on first run.

**Phase 3 — Today screen** *(gate: ViewModel tests + Roborazzi snapshots approved)*
TodayViewModel (state machine incl. day types, edited-after-report), Today UI: time card + ±5 steppers, חופש/חג chips, activity entries (times/note/result), field jobs bottom sheet, notes, live report preview, share intent handoff (`<queries>`, com.whatsapp → w4b → chooser fallback, `SendReportActivity` marks reportedAt).

**Phase 4 — Reminder subsystem** *(gate: Robolectric alarm/receiver tests green)*
`ReminderScheduler` (AlarmManager `setAndAllowWhileIdle`, next-eligible-day logic incl. weekend-with-data), boot/timezone re-arm, all §5.5 report-time notification variants, +2h repeat with 23:30 cutoff, notification channels.

**Phase 5 — History + Statistics** *(gate: ViewModel tests + snapshots)*
History list with month strip + status badges, Day Editor for past dates + re-send, day-off/holiday marking; Stats screen: period selector, KPI tiles, stacked office/field Canvas chart (validated palette #00897B/#9E6410, RTL axis, average line, tap tooltips), activity breakdown, period share.

**Phase 6 — Geofencing** *(gate: receiver decision-table tests green — every row of §5.5)*
GeofencingClient registration (initial triggers off), receiver implementing the full decision table + invariants (never overwrite MANUAL, event-time confirms, 10-min debounce, re-enter cancels), progressive permission flow with background-location settings redirect, current-location office capture.

**Phase 7 — Settings, export, polish** *(gate: full test suite + lint green, release build assembles)*
Settings screen complete, category editor, JSON/CSV export via FileProvider, Auto Backup rules + toggle, app icon, R8 release config.

**Phase 8 — Device validation (S23 Ultra)** *(gate: manual checklist in `docs/dev/testing.md` §device)*
Install path (APK), Samsung-specific checks: One UI notification channels, battery-optimization exemption guidance for geofence reliability, 120Hz scrolling, edge display insets, WhatsApp handoff on device.

## Deviation log

- **Work sessions replace arrival/departure + field jobs (v2.0, product-owner request):** a day is now a list of **work sessions**, each with a mode (בסיס / בית / שטח), its own hours and its own activities — the same day can hold four hours at the base, two from home and three on a site, and the day total is their sum (9:00). This replaced `WorkDay.arrivalMin/departureMin` and the whole `field_job` table with `work_session`, and moved `activity.date` to `activity.sessionId`. Consequences worth naming:
  - **No migration chain.** The product owner explicitly waived back-compatibility ("I will input all the data from scratch"), so schema v6 is a clean break: `fallbackToDestructiveMigration`, `ALL_MIGRATIONS` empty, old exported schemas removed, `MigrationTest` replaced by `SchemaResetTest` (an older database must be rebuilt and re-seeded, never crash on open). That test immediately caught a real upgrade bug: Room does not call `onCreate` after a destructive rebuild, so the seed now also runs on open when a table is empty — without it every upgraded install would have come back with no categories and no projects, unable to log anything.
  - **Report order (product-owner rule):** activity lines read **project · category — note (duration) · תוצאה**, under a per-session header, with a `סה״כ 9:00 — בסיס 4:00 · בית 2:00 · שטח 3:00` line at the top.
  - **Time budget, screen only:** the Today screen shows how much of each session's (and the day's) hours the logged activities account for and how much is left — "מולאו 2:00 מתוך 4:00 · נותרו 2:00" — and turns red when the activities claim more time than was worked. Modelled as `TimeBudget` in `:domain`; deliberately absent from the report, which states facts rather than progress. Needed one new token, `Warn`, because Amber already means "unconfirmed", not "wrong".
  - **Second visits are second sessions.** The geofence context now describes *the visit in progress* rather than "the day's arrival", so a return after lunch opens a new session instead of being read as "already arrived" (the reported "only the last visit is recorded" bug). Job-site fences follow the same rule per location; §6.6b's first-enter/last-exit merging is gone with the single field-job row it existed for.
  - **Stale-write protection:** sessions and activities are edited by id and re-read inside the repository lock (`editSession` / `editActivity`), so two quick edits — start then end, or repeated ±½ taps — can no longer write through a captured stale row.
  - **Statistics** gained a third series (`ChartHome`, indigo, re-validated with teal/ochre for CVD) and a mode-split line in text, per the rule that a chart is never the only source.
- **UI copy and icons trimmed (v2.0, product-owner feedback):** the mode emoji (🏢/🏠/🚗) are gone from the app's own chrome — session headers and the add-session buttons name the mode in words — and the explanatory placeholders went with them ("(לא חובה)" everywhere, the "add a work session to start logging" line, the sentence describing what the send button does). The report preview card now renders the actual report text instead of a description of it. The report and PDF keep the approved §2.4 format, emoji included; changing what the WhatsApp group receives is a separate call.
- **Workday integration test (v2.0):** `WorkdayFlowTest` walks the ordinary day end to end — morning ENTER, evening EXIT, in both automatic and suggestion mode — and asserts what the rest of the app then sees: one base session, the widget's two frozen times, the day total, a manually added home session left untouched, and a widget tap overriding a geofence time so a later exit cannot move it. The office fence writes BASE and nothing else; home work is always manual, and field visits only come from job locations the user saved themselves.
- **Configurable office radius (v2.0, product-owner request):** the office fence radius is chosen from 100 / 300 / 500 m / 1 / 1.5 / 2 km instead of the old four tight values, with the default moving 150 m → 300 m. The ladder and the rounding live in `GeofenceRules`, and `SettingsRepository` snaps whatever is stored onto it (upward — a fence too small misses arrivals), so a value saved before the ladder existed can never leave the fence running at a size no chip represents. Changing the radius re-registers the fence.
- **Quarter-hour booking (v2.1, product-owner rule):** every session time lands on a 15-minute step, a start rounding **down** and an end rounding **up** (08:12–17:35 is booked 08:00–17:45), so the rounding always widens the stretch of work rather than trimming it. The rule is a pure `WorkTimeStep` in `:domain` and is applied in `DayRepository` — the single gateway every writer already goes through — rather than at each call site, so the fence, the widget, the הגעתי/יצאתי buttons and the time picker cannot disagree. Geofence prompts offer the snapped value so what is offered is what is stored, and the Today screen's ±5 nudges became ±15. Backup restore is exempt: it re-inserts stored rows in bulk, not through the write path.
- **Category editor (F5, Should) deferred to v1.1** — the 8 seeded Hebrew categories cover the spec's use cases; the hide/rename UI is scaffolded in the data layer (isHidden, no hard delete) but has no Settings surface yet.
- **Auto Backup toggle (F13, Should) deferred to v1.1** — Auto Backup itself is on via `allowBackup`; the in-app off switch is not yet exposed.
- **Chart tooltip** renders as a selection-detail line under the plot instead of a floating bubble — same information, simpler and more reliable on touch; exact values also live in the KPI tiles per the accessibility relief rule.
- **2026-08-05 day-name fix**: spec prose examples called 05.08.2026 a Tuesday; it is a Wednesday. Code and tests follow `LocalDate` — the calendar, not the prose.
- **Spec §2.2 example dates** in CONOPS narrative retained as-is (cosmetic).
- **Activity durations replace clock times (v0.9, product-owner decision):** an activity now carries an optional duration in 30-minute steps instead of start/end times. Schema v3 rebuilds the `activity` table; an existing start+end pair migrates to its span rounded to the nearest half hour (minimum one step) so no logged work is lost. Report, PDF, Today editor and JSON export (`schemaVersion` 2) all follow; activities render in logged order, since there is no longer anything to sort by.
- **Projects + full backup (v1.2, product-owner request):** activities are now booked against a project (mandatory at creation; three Hebrew defaults seeded; user-managed in Settings). Schema v5 makes `activity.projectId` NOT NULL, and the migration parks pre-existing activities under a clearly-named "ללא שיוך" project rather than misfiling them under a real one. Removal archives a project that has logged work and deletes one that does not. Backup is a first-class subsystem, not an export bolt-on: `BackupCodec` + `BackupRepository` capture every table and every setting into one versioned JSON document and restore it atomically; `BackupRoundTripTest` fails if any table or setting is dropped, which is what keeps it complete as the app grows.
- **Geofence reliability overhaul (v1.1, field-reported):** recording sometimes did nothing, sometimes logged a wrong hour, and kept only one of two daily visits. Six root causes, written up in `docs/dev/geofence-review.md`: a missed EXIT left the fence permanently "inside" (every later day silently ignored); a missed debounce alarm did the same; nothing was written at all unless a notification was tapped; the fix timestamp was trusted unconditionally (wrong hour) and dropped past 60 min (nothing recorded); registration could lapse with no recovery; and automatic mode never applied the last-exit-wins update. Fixes: new-visit detection, stranded-exit settling, automatic recording by default, timestamp clamping, registration self-heal, and a diagnostics log in Settings.
- **Minimum-visit rule + explicit fence state machine (v1.0, product-owner rule):** passing within a fence for a few minutes was still read as a day at work. A leaving time is now only ever suggested after a stay of at least an hour; below that the arrival is kept but marked amber ("ביקור קצר") so the user can accept or ignore it. The office decision table moved into a pure `OfficeFenceMachine` in `:domain` — states Outside/Inside/Leaving, events Enter/Exit/DebounceElapsed, actions as data — so every ordering and edge case is testable without Android. Two defects surfaced while building it: any re-entry (not just one during the debounce window) must retire a standing departure suggestion, and the job-site dwell was measured from the day's first arrival rather than the current visit, so an evening drive-past dragged a real 17:00 departure later.
- **Geofence ordering invariants (v0.9, field-reported defect):** arriving at the office produced a departure suggestion. Root cause: transitions were stamped with `now()` and acted on unconditionally, while Play Services delivers them late and out of order. Added occupancy state per fence, event-time/event-day binding, staleness and dwell floors, same-place fence suppression, and removal of the create-a-field-job-from-an-exit fallback. Documented as spec §6.6 "Ordering invariants".
- **Home-screen widget (v0.8, product-owner request):** a 4x1 widget with one-tap כניסה/יציאה buttons that log the real clock time with MANUAL source (overriding whatever is stored, including geofence suggestions). Added as spec §5.6; it schedules nothing of its own — the live time comes from a system `TextClock` and redraws are event-driven (N6).
- **Reset arrival/departure (v0.8):** F1's "manual entry/edit always possible" now includes clearing a logged time back to unset; clearing resets the source to MANUAL so the value can be re-suggested.
- **N3 revised (v0.7):** the product owner requested a map pin picker for office/job locations; map tiles require network, so INTERNET was added for OSM tiles only (osmdroid, no API keys). ManifestGuardTest flipped from "INTERNET absent" to "INTERNET documented".

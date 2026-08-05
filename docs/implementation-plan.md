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

- **Category editor (F5, Should) deferred to v1.1** — the 8 seeded Hebrew categories cover the spec's use cases; the hide/rename UI is scaffolded in the data layer (isHidden, no hard delete) but has no Settings surface yet.
- **Auto Backup toggle (F13, Should) deferred to v1.1** — Auto Backup itself is on via `allowBackup`; the in-app off switch is not yet exposed.
- **Chart tooltip** renders as a selection-detail line under the plot instead of a floating bubble — same information, simpler and more reliable on touch; exact values also live in the KPI tiles per the accessibility relief rule.
- **2026-08-05 day-name fix**: spec prose examples called 05.08.2026 a Tuesday; it is a Wednesday. Code and tests follow `LocalDate` — the calendar, not the prose.
- **Spec §2.2 example dates** in CONOPS narrative retained as-is (cosmetic).
- **N3 revised (v0.7):** the product owner requested a map pin picker for office/job locations; map tiles require network, so INTERNET was added for OSM tiles only (osmdroid, no API keys). ManifestGuardTest flipped from "INTERNET absent" to "INTERNET documented".

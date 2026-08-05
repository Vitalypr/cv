# DayLog (יומן עבודה)

Hebrew-first, RTL Android app for a consultant's daily work log: arrival/departure, field jobs, activities (times/note/result), daily WhatsApp report via share intent, weekly/monthly/yearly statistics. Single user, offline-only, no backend.

**Source of truth:** @docs/daylog-spec.md (v0.4, approved). Build order & gates: @docs/implementation-plan.md. Deviations go in the plan's deviation log, never silently.

## Layout

- `android/` — the app. `:domain` = pure Kotlin (models, ReportBuilder, StatsCalculator — zero Android imports). `:app` = Android (Room data layer, Compose UI, reminder/geofence/reporting system glue).
- `docs/daylog-mockup.html` — approved interactive design mockup; UI must match it.
- `docs/dev/` — discipline guides (see below).

## Commands (from `android/`)

```bash
export ANDROID_HOME=/opt/android-sdk        # this container; local.properties also sets it
gradle :domain:test                         # fast domain TDD loop (pure JVM)
gradle :app:testDebugUnitTest               # Robolectric: data/VM/system/screenshot tests
gradle :app:testDebugUnitTest -Proborazzi.test.verify=true   # verify screenshots
gradle :app:testDebugUnitTest -Proborazzi.test.record=true   # re-record after approved UI change
gradle :app:assembleDebug                   # APK: app/build/outputs/apk/debug/
gradle :app:lintDebug
```

## Immutable rules

1. **TDD:** failing test first for every domain rule and ViewModel behavior. Every spec section maps to named tests (@docs/dev/testing.md).
2. **Dependency direction:** `:domain` never imports Android. UI renders only from ViewModel `UiState`; no logic in composables (@docs/dev/architecture.md).
3. **Hebrew/RTL:** every report line starts with RLM (U+200F); golden-string tests are canonical — never "fix" a golden to match broken output (@docs/dev/ui-guidelines.md).
4. **Network = map tiles only.** INTERNET exists solely for the OSM location picker (spec N3 rev v0.7); no analytics/backends ever. `ManifestGuardTest` documents this — any new network use is a conscious spec change.
5. **Geofence invariants** (spec §6.6): confirmations write event time; never overwrite MANUAL-source values; suggestions commit only on user confirmation.
6. **Time model:** minutes-from-midnight `Int`, may exceed 1440 (past midnight, renders "(למחרת)"). Never store `LocalTime` for arrival/departure.

## Discipline guides

- @docs/dev/architecture.md — layers, state flow, DI, module rules
- @docs/dev/structure.md — package map, where new code goes
- @docs/dev/testing.md — pyramid, spec traceability, S23 Ultra device checklist
- @docs/dev/ui-guidelines.md — design tokens, RTL rules, chart palette, how to restyle safely
- @docs/dev/gotchas.md — hard-won constraints (WhatsApp intents, Doze, bidi, Samsung)
- @docs/dev/tools.md — toolchain versions and why, environment setup, screenshot workflow

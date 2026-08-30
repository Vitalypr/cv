# Project structure — where code goes

```
android/
  settings.gradle.kts, build.gradle.kts, gradle.properties
  gradle/libs.versions.toml        # ALL versions live here; never inline a version in a module
  domain/                          # pure Kotlin JVM
    src/main/kotlin/com/vitalypr/daylog/domain/
      model/       Day.kt (DayType, TimeSource, WorkMode, WorkSession, DayStatus, DaySnapshot,
                   ActivityEntry, TimeBudget)
      time/        Times.kt (formatMinutes, formatDuration, Hebrew day/date formatting)
      geo/         GeofenceRules.kt (day-attribution, staleness, MIN_VISIT, distance)
                   FenceMachine.kt (office state machine: states/events/actions)
      report/      ReportBuilder.kt (daily + period golden-tested renderers)
      stats/       StatsCalculator.kt (hours rule, averages, PeriodSummary)
    src/test/kotlin/...            # mirrors main; golden strings live here
  app/
    src/main/kotlin/com/vitalypr/daylog/
      DayLogApp.kt  MainActivity.kt
      di/           Hilt modules
      data/         db/ (entities, daos, DayLogDb), repo/, settings/, backup/, export/
      ui/           theme/ (Color.kt, Type.kt, Theme.kt — ALL design tokens)
                    components/ (shared: TimeSlot, CategoryChips, StatusBadge, cards)
                    today/  history/  stats/  settings/   # screen + ViewModel + UiState each
      reporting/    ReportShare.kt, SendReportActivity.kt
      reminder/     ReminderScheduler.kt, ReminderReceiver.kt, BootReceiver.kt
      geofence/     GeofenceManager.kt, GeofenceReceiver.kt
      widget/       DayWidgetProvider.kt, DayWidgetRenderer.kt, WidgetActions.kt (rules), DayWidgetRefresher.kt
      notifications/ Channels.kt, Notifier.kt
    src/main/res/   values/strings.xml is HEBREW (default locale); backup rules in xml/
    src/test/       Robolectric + Roborazzi tests, package-mirrored
    schemas/        exported Room schemas — COMMIT these; from v6 forward every bump ships a Migration + test
                    (v1–v5 were removed with the v2.0 clean break)
```

Rules of placement:
- New business rule → `:domain` with its test, then wire upward.
- New screen → `ui/<feature>/` = `<Feature>Screen.kt` (stateless) + `<Feature>ViewModel.kt` + `<Feature>UiState.kt` + snapshot test.
- Anything visual you might want to restyle → tokens in `ui/theme/`, geometry in the screen file. Never hardcode a color/size outside `theme/` (see ui-guidelines).
- New string → `res/values/strings.xml` (Hebrew). No hardcoded user-visible strings in Kotlin.

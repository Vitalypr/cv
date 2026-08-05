# Gotchas — constraints that will bite if forgotten

## WhatsApp / share
- **No API posts to groups.** Share-intent handoff is a hard platform constraint (spec §2.2), not a TODO.
- Android 11+ package visibility: `<queries>` for `com.whatsapp` AND `com.whatsapp.w4b` or `resolveActivity` returns null.
- `setPackage` + missing app → `ActivityNotFoundException`; catch → `Intent.createChooser`.
- Android 12+ notification trampoline ban: the notification's Send action must launch `SendReportActivity` (transparent) directly, which writes `reportedAt` then fires the share intent. No broadcast-then-startActivity.
- The group cannot be pre-selected; `wa.me` links are individuals-only.

## Time
- Arrival/departure/activity/field times = minutes-from-midnight `Int`; **may exceed 1440** (overnight ⇒ "01:30 (למחרת)"). `LocalTime` cannot represent this — don't "simplify" back to it.
- 00:00–04:00 departure offers attachment to yesterday's open day (spec §6.2).
- All wall-clock in device-local zone at event time; re-arm alarm on `TIMEZONE_CHANGED`/`TIME_CHANGED`; only `reportedAt` is an `Instant`.
- 2026-08-05 is a **Wednesday** (spec/mockup prose examples said Tuesday — do not propagate; day names must come from `LocalDate`, never hardcoded).

## Doze / reminders
- WorkManager is deferrable → can slip hours in Doze. The reminder uses `AlarmManager.setAndAllowWhileIdle` (±10 min, acceptable; SCHEDULE_EXACT_ALARM deliberately not requested).
- Alarms die on reboot → `BOOT_COMPLETED` receiver re-arms (also re-registers geofence); self-heal re-arm on every app open.
- **Samsung**: aggressive battery management can still kill geofence/alarms; device checklist requires "Unrestricted" battery for the app; Settings screen should link users there.

## Geofence
- Register with **initial triggers disabled** or setting office-while-at-office fires a bogus ENTER.
- `ACCESS_BACKGROUND_LOCATION` on Android 11+ cannot be granted in-app — must deep-link to system settings ("Allow all the time"); Play Console declaration required.
- Invariants (§6.6): confirm writes EVENT time; never overwrite MANUAL values; 10-min exit debounce; re-enter cancels pending suggestion. The receiver is a decision table — every row has a test.

## Bidi / Hebrew
- Lines starting with emoji/digits flip in WhatsApp without leading RLM (`‏`). ReportBuilder owns RLM; nothing else appends it.
- Mixed Hebrew + Latin (client names) inside a line is fine once the line has RLM; ranges like `10:00–13:30` must be en-dash between complete LTR runs.

## Build / environment (this container)
- `ANDROID_HOME=/opt/android-sdk`; system Gradle 8.14.3 at `/opt/gradle/bin/gradle` (no wrapper download). JVM proxy/truststore comes from `JAVA_TOOL_OPTIONS` — don't unset it.
- AGP is pinned to the 8.x line to match Gradle 8.14 (AGP 9 needs Gradle 9). Version bumps go through `libs.versions.toml` + a full test run.
- No INTERNET permission: `ManifestGuardTest` fails if a dependency merges it in — fix by excluding the dep, not deleting the test.
- Room schema exports are committed under `app/schemas/`; every schema bump ships a Migration + MigrationTest.

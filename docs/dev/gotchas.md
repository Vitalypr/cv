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
- **Android 10+ `addGeofences` silently fails without background location** — the GMS Task rejects asynchronously, so a fire-and-forget `runCatching` hides it completely. `GeofenceManager` must `await()` the Task and publish a `GeofenceStatus` the Settings screen renders; `precheck()` is the pure, unit-tested gate (Disabled / NoPermission / NoBackgroundPermission / NoLocations / Active).
- Invariants (§6.6): confirm writes EVENT time; never overwrite MANUAL values; 10-min exit debounce; re-enter cancels pending suggestion. The receiver is a decision table — every row has a test.
- **Transitions arrive late and OUT OF ORDER.** GMS reports a crossing when it next gets a fix, so the morning drive to work can deliver yesterday's EXIT *before* today's ENTER. Never decide from `now()`: use `event.triggeringLocation.time` and the event's logical day, and never act on an EXIT without a recorded ENTER (`FenceStateStore`). This is what made arriving at the office suggest a departure.
- Indoor GPS drifts past a 150 m fence while the user sits at their desk. The 10-min debounce only helps if a re-ENTER follows; the dwell floors (`MIN_OFFICE_DWELL`, `MIN_WORKDAY_DWELL`) are what stop drift from generating prompts. Don't lower them to "make suggestions faster".
- Re-registering geofences resets the platform's inside/outside belief and loses in-flight transitions — `GeofenceManager` skips registration when the fence set is unchanged. The fingerprint is per-process on purpose, so a reboot still re-registers.
- Cancel alarms with `FLAG_NO_CREATE`; `FLAG_UPDATE_CURRENT` rewrites the pending intent's extras first, which can race a firing alarm into using the placeholder value.

## Battery (design invariants — spec N6)
- The app is fully event-driven: NO `requestLocationUpdates`, no wakelocks, no foreground services, no repeating alarms, no periodic WorkManager jobs — ever. Location = OS-managed geofences + one explicit one-shot fix when the user taps "קבע למיקום הנוכחי".
- Job fences (2 km) set `setNotificationResponsiveness(2 min)` so GMS batches transitions instead of waking immediately; the office fence stays at 0 because its event time is the recorded arrival/departure. Don't "fix" job suggestion timing by dropping the responsiveness — 2 km of radius already dwarfs 2 min.
- osmdroid MapView must get `onDetach()` when the picker dialog closes or tile threads keep running.

## Home-screen widget
- RemoteViews inflates **only remotable classes** — a bare `<View>` (used as a spacer) throws "Class not allowed to be inflated" and the launcher shows "Problem loading widget". Use margins for gaps; `TextView`/`TextClock`/`ImageView`/`LinearLayout`/`FrameLayout` are safe. `DayWidgetScreenshotTest` inflates the real layout, so this class of error fails the build instead of the home screen.
- The "time that will be recorded" is a system `TextClock` (`format12Hour` AND `format24Hour` both `HH:mm`, so it stays 24h regardless of device setting) — never a self-scheduled refresh.
- Widget colors live in `res/values/colors.xml`, a mirror of `ui/theme/Color.kt` (RemoteViews can't read Compose). Change both together.
- **A 1-cell-tall widget must survive 40dp**: that is the declared floor (`ceil((minHeight+30)/70)` must stay 1 cell, so minHeight can't be raised), even though launchers really hand out ~70dp. The renderer reads `OPTION_APPWIDGET_MIN_HEIGHT` and drops the labels + shrinks the time below 56dp; `DayWidgetScreenshotTest` captures both sizes. Don't hard-code one text size — it either clips at the floor or looks tiny at real size.
- Label above time, not beside it: side-by-side caps the time at ~20sp on a 4-cell width before the digits clip.
- Redraws are event-driven via `DayWidgetRefresher`, called from exactly three places: widget taps, geofence writes, and `MainActivity.onStop` (covers all in-app edits). A new write path for arrival/departure must call it too.

## Bidi / Hebrew
- Lines starting with emoji/digits flip in WhatsApp without leading RLM (`‏`). ReportBuilder owns RLM; nothing else appends it.
- Mixed Hebrew + Latin (client names) inside a line is fine once the line has RLM; ranges like `10:00–13:30` must be en-dash between complete LTR runs.

## Build / environment (this container)
- `ANDROID_HOME=/opt/android-sdk`; system Gradle 8.14.3 at `/opt/gradle/bin/gradle` (no wrapper download). JVM proxy/truststore comes from `JAVA_TOOL_OPTIONS` — don't unset it.
- AGP is pinned to the 8.x line to match Gradle 8.14 (AGP 9 needs Gradle 9). Version bumps go through `libs.versions.toml` + a full test run.
- Network = OSM map tiles only (N3 rev v0.7): `ManifestGuardTest` asserts INTERNET exists for the map picker and documents the exception — any other network use is a conscious spec change, not a dependency accident.
- Room schema exports are committed under `app/schemas/`; every schema bump ships a Migration + MigrationTest.

## PDF / typography
- **Never set letterSpacing on Hebrew text** — it breaks glyph shaping (renders as split words). Latin-caps convention only.
- PdfDocument cannot run under Robolectric ("document is closed") — drawing is bitmap-tested via ReportPdf.drawReport; the container path is device-verified.
- FileProvider caches its path strategy statically — multiple Robolectric tests hitting it must share one test method/context.
- MigrationTestHelper cannot see app assets under Robolectric (instrumentation.context serves framework assets only) — MigrationTest builds a real v1 DB from the exported schema SQL and lets Room migrate+validate on open instead.

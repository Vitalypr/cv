# Gotchas — constraints that will bite if forgotten

## WhatsApp / share
- **No API posts to groups.** Share-intent handoff is a hard platform constraint (spec §2.2), not a TODO.
- Android 11+ package visibility: `<queries>` for `com.whatsapp` AND `com.whatsapp.w4b` or `resolveActivity` returns null.
- `setPackage` + missing app → `ActivityNotFoundException`; catch → `Intent.createChooser`.
- Android 12+ notification trampoline ban: the notification's Send action must launch `SendReportActivity` (transparent) directly, which writes `reportedAt` then fires the share intent. No broadcast-then-startActivity.
- The group cannot be pre-selected; `wa.me` links are individuals-only.

## Work sessions (v2.0)
- A day holds **many sessions**; nothing hangs off the day row any more. Code that asks "what time did he arrive?" must say *which visit* — `firstStartMin`/`lastEndMin` are the day's outer bounds, not "the arrival".
- A geofence ENTER decides from **the visit in progress**, never from "does the day have an arrival". Getting that wrong is exactly what made a second visit vanish into the first.
- `endSession` only closes a session that is open; `recordDeparture` also moves the last visit's end (last-exit-wins) and is what confirm/automatic writes use.
- **Never write through a captured entity.** UI state is a snapshot; `editSession`/`editActivity` re-read by id inside the write lock. Two quick edits through one stale row silently undo each other (start-then-end, or repeated ±½ taps).
- Schema v6 is a **clean break** — no migrations, `fallbackToDestructiveMigration`. Room does **not** call `onCreate` after a destructive rebuild, and `onDestructiveMigration` fires *before* the tables exist, so seeding lives in `onOpen` guarded by an emptiness check. Without it an upgraded install has no categories and no projects and cannot log anything.

## Time
- **Session times are quarter hours**: `DayRepository` snaps every start down and every end up (`WorkTimeStep`) on the way in, so no caller needs to remember it — and no caller may bypass the repository to write a raw minute. A prompt or a preview must show the snapped value, or the app offers one time and stores another.
- Session start/end times = minutes-from-midnight `Int`; **may exceed 1440** (overnight ⇒ "01:30 (למחרת)"). `LocalTime` cannot represent this — don't "simplify" back to it.
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
- **A missed transition must never be permanent.** Play Services drops ENTER/EXIT routinely. An ENTER while already `Inside` starts a NEW visit when the stored entry is from another day or older than `MAX_VISIT`; an ENTER long after a pending exit settles that exit first. Without those two escapes one missed EXIT left the fence "inside" for ever and the feature went silent — the single worst bug this subsystem has had.
- `triggeringLocation.time` is the age of the FIX, not of the crossing. Clamp it (`eventTime()`): older than 10 min or in the future → use delivery time. Trusting it wrote wrong hours; dropping stale events lost days.
- Automatic recording (`silentGeofence`) is ON by default since v1.1: a suggestion that is never tapped is data lost. GEOFENCE writes still never overwrite MANUAL.
- Registration can lapse without any callback (location toggled, GMS update). The fingerprint skip expires after 30 min, `PROVIDERS_CHANGED`/boot call `resync()`, and a GMS error event triggers one too.
- The office radius is one of `GeofenceRules.OFFICE_RADIUS_OPTIONS` (100 m … 2 km, default 300 m); `SettingsRepository` snaps whatever is stored onto that ladder so the fence and the Settings chips can never disagree. Changing it calls `resync()` — a new size is a new fence.
- A wide office radius swallows nearby job locations: `GeofenceManager` deliberately does not track a job pin that falls inside the office fence (otherwise every office arrival opens a field visit too). At 2 km that is most of a small town.
- `GeofenceLog` keeps the last 50 transitions and what each did, shown in Settings → אבחון מעקב. Field failures here are otherwise unreproducible; ask for that list first.
- **The office decision table lives in `OfficeFenceMachine` (`:domain`), not in the engine.** The engine only resolves the day, loads state, and performs the returned actions. Add a rule there, with a case in `FenceMachineTest` — never as an `if` in the engine.
- A leaving time is never suggested for a stay under `GeofenceRules.MIN_VISIT` (1 h); the arrival survives but is flagged `arrivalUncertain` (amber "ביקור קצר"). Job-site dwell is measured over the CURRENT visit, not from the day's first arrival, or an evening pass-by drags a real departure later.
- Indoor GPS drifts past a tight fence while the user sits at their desk. The 10-min debounce only helps if a re-ENTER follows; `MIN_VISIT` is what stops drift from generating a departure. Don't lower it to "make suggestions faster".
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

## Projects & backup
- `activity.projectId` is NOT NULL: an activity cannot exist without a project. The Today screen asks which one *before* creating the row, and skips the question when only one project exists.
- A project with logged work is **archived**, never deleted — deleting it would orphan history. `ProjectRepository.remove` decides; re-adding an archived name revives it instead of duplicating.
- **`BackupRepository` must know about every table and every setting.** Adding either means adding it to `BackupCodec` AND `SettingsRepository.replaceAll` — `BackupRoundTripTest` compares a full export before and after a wipe+restore, so an omission fails the build rather than silently losing the user's data.
- Restore replaces inside one `withTransaction`, children cleared first and parents inserted first; ids are preserved so activity→project links survive.

## Bidi / Hebrew
- Lines starting with a digit flip in WhatsApp without a leading RLM (`‏`). ReportBuilder owns RLM; nothing else appends it. (The reports carry no emoji since v2.1 — don't re-add "just one".)
- In a Kotlin template, `"$rlmבסיס"` parses as ONE identifier: Hebrew letters are valid in identifiers. Golden strings need `"${rlm}בסיס"`.
- Mixed Hebrew + Latin (client names) inside a line is fine once the line has RLM; ranges like `10:00–13:30` must be en-dash between complete LTR runs.

## Build / environment (this container)
- `ANDROID_HOME=/opt/android-sdk`; system Gradle 8.14.3 at `/opt/gradle/bin/gradle` (no wrapper download). JVM proxy/truststore comes from `JAVA_TOOL_OPTIONS` — don't unset it.
- AGP is pinned to the 8.x line to match Gradle 8.14 (AGP 9 needs Gradle 9). Version bumps go through `libs.versions.toml` + a full test run.
- Network = OSM map tiles only (N3 rev v0.7): `ManifestGuardTest` asserts INTERNET exists for the map picker and documents the exception — any other network use is a conscious spec change, not a dependency accident.
- Room schema exports are committed under `app/schemas/`; from v6 forward every schema bump ships a Migration + test (v1–v5 went with the v2.0 clean break).

## PDF / typography
- **Never set letterSpacing on Hebrew text** — it breaks glyph shaping (renders as split words). Latin-caps convention only.
- PdfDocument cannot run under Robolectric ("document is closed") — drawing is bitmap-tested via ReportPdf.drawReport; the container path is device-verified.
- FileProvider caches its path strategy statically — multiple Robolectric tests hitting it must share one test method/context.
- MigrationTestHelper cannot see app assets under Robolectric (instrumentation.context serves framework assets only) — MigrationTest builds a real v1 DB from the exported schema SQL and lets Room migrate+validate on open instead.

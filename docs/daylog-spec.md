# DayLog — Consultant Daily Work Logger

**Document type:** CONOPS + Technical Specification
**Status:** Draft v0.1 — for review
**Platform:** Android (native)
**Author:** Vitaly (product owner) with Claude (co-author)

---

## 1. Purpose & Vision

DayLog is a personal Android app for an independent consultant to document each working day — arrival and departure times, out-of-office field jobs, and the activities performed (discussion, installation, testing, development, design, etc.) — and to produce a clean daily report that is sent to a WhatsApp group at the end of the day with minimal friction.

**Design principles (in priority order):**

1. **Zero-thought logging** — capturing a fact about the day must take one tap or less (geofence suggestions).
2. **The report is the product** — everything the app collects exists to produce one tidy WhatsApp message per day.
3. **Private by default** — all data lives on the device; no accounts, no backend, no analytics.
4. **Honest automation** — automate what platforms allow (reminders, pre-filled report), never fight platform rules (no unofficial WhatsApp APIs).

**Explicit non-goals (v1):** multi-user support, client/project billing tags, invoicing, cloud sync, iOS.

---

## 2. CONOPS — Concept of Operations

### 2.1 Actor

A single consultant who works most days from a primary office, with occasional out-of-office field jobs (client sites, installations, commissioning). At end of day, they report a summary to a WhatsApp group (e.g., with the client or their own management).

### 2.2 Key constraint: WhatsApp delivery

WhatsApp provides **no API for posting to a group automatically**. The official Business Cloud API can only message individual opted-in numbers, and unofficial automation risks a number ban. Therefore DayLog uses the **reminder + share-intent** pattern:

> At the configured time, DayLog posts a notification with the finished report. One tap opens the Android share sheet → WhatsApp → the group (WhatsApp lists frequent chats first) → Send.

Total human effort: **2–3 taps per day**. This is the maximum automation possible without violating WhatsApp's terms of service. The spec treats this as a hard constraint, not a temporary limitation.

### 2.3 Operational scenarios

**S1 — Typical office day**
1. 08:12 — Consultant enters the office geofence. Notification: *"Arrived at office? Log 08:12"* → tap **Confirm** (or open app to adjust the time).
2. During the day — opens the app 2–3 times for ~15 seconds each: taps activity category chips (e.g., **Development**, **Discussion**) and optionally adds a one-line note per activity.
3. 17:35 — leaves the geofence. Notification: *"Departed 17:35?"* → **Confirm**.
4. 17:45 (configured report time) — Notification shows the rendered report with a **Send to WhatsApp** action. Tap → share sheet → group → Send. Day is marked **Reported** ✓.

**S2 — Field-job day**
1. Consultant taps **+ Field Job** in the app: enters location/client name, start time; end time on return. (Geofence still logs office arrival/departure if they pass through the office.)
2. Field jobs appear as a distinct 🚗 section in the report.
3. Report flow identical to S1.

**S3 — Forgot to log**
1. Report time arrives but the day has no arrival time → the reminder notification says *"Complete today's log"* and opens the **Day Editor** instead of sending.
2. All times and activities are freely editable for **any past day**; a past day's report can be regenerated and sent late (report header carries the day's date, so late sending is unambiguous).

**S4 — Non-working day**
No geofence prompts and no reminder fire on days outside the configured work-week (default Mon–Fri; configurable, e.g., Sun–Thu). A manual "log this day anyway" path exists for exceptional weekend work.

**S5 — Reviewing history**
Consultant opens the **History** tab: a calendar/list of past days with status badges (Reported ✓ / Logged but unsent / Empty). Tapping a day opens it read-only with **Edit** and **Re-send report** actions. Monthly summary (total hours, field-job count, activity breakdown) supports timesheet cross-checking.

### 2.4 The daily report (the product)

Plain text (WhatsApp-friendly, no markdown dependency), rendered from a template:

```
📋 Daily Report — Tue, 05 Aug 2026
🕗 In: 08:12   Out: 17:35   (9h 23m)
🚗 Field: Acme Substation — commissioning (10:00–13:30)
✅ Activities:
  • Installation — cabinet wiring, bay 4
  • Testing — relay acceptance tests
  • Discussion — punch-list review with site manager
📝 Notes: spare CT ordered, ETA Thursday
```

Template rules:
- Sections with no content are omitted entirely (no "Field: —").
- Activity lines are `Category — note`; a category with no note renders as the category alone.
- Header date always present → safe to send late.
- Language of the fixed labels is a setting (v1 ships English; label strings are resources, so adding languages is trivial).

---

## 3. Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| F1 | Record arrival and departure time per day; manual entry/edit always possible | Must |
| F2 | Geofence around a user-defined office location suggests arrival/departure via confirmable notifications; manual values always override | Must |
| F3 | Log zero or more field jobs per day: title/client, optional location text, start/end times | Must |
| F4 | Log zero or more activities per day from a category list (Discussion, Installation, Testing, Development, Design, Documentation, Support, Other), each with an optional free-text note | Must |
| F5 | Category list is user-editable (add/rename/hide) | Should |
| F6 | Free-text daily notes field | Must |
| F7 | Daily reminder notification at a configured time on configured workdays, showing the rendered report with a Send action | Must |
| F8 | Send action opens Android share sheet with the report text targeted at WhatsApp | Must |
| F9 | Day status tracking: Empty / In progress / Reported; re-send possible any time | Must |
| F10 | History view: browse, edit, and re-send any past day | Must |
| F11 | Monthly summary: total hours, days worked, field jobs, activity category counts | Should |
| F12 | Export all data as JSON and CSV via share sheet (backup, spreadsheet analysis) | Must |
| F13 | Automatic local backup file + participate in Android Auto Backup | Should |
| F14 | Works fully without location permission (geofencing simply off) | Must |

## 4. Non-Functional Requirements

- **N1 Simplicity:** every daily interaction reachable in ≤ 2 taps from app open; no login, no onboarding beyond a 3-step setup (office location, work-week, report time).
- **N2 Reliability:** reminder must fire even after reboot and under Doze (WorkManager + boot receiver); geofence misfire must never corrupt data — suggestions are only committed on user confirmation.
- **N3 Privacy:** no network calls at all in v1 (the app has no INTERNET permission except what Play services require implicitly); location used only for geofence triggers, never stored as coordinates in history.
- **N4 Performance:** cold start < 1.5 s on a mid-range device; the Today screen renders from a single DB query.
- **N5 Data safety:** Room DB with export; schema migrations versioned from day one.
- **N6 Battery:** geofencing via Play Services GeofencingClient (OS-managed, near-zero battery), never continuous location polling.

---

## 5. UX Specification

Three-tab bottom navigation. Visual language: Material 3, dynamic color, large touch targets, no decoration that doesn't serve logging speed.

### 5.1 Today (home tab)
- **Time card:** big `In —:—` / `Out —:—` values; tap a value to set/adjust via time picker; **Arrived now** / **Leaving now** one-tap buttons when unset.
- **Field jobs card:** list + `+ Field Job` button (bottom-sheet form: title, optional location, start/end).
- **Activities card:** category chips in a flow row; tapping a chip adds the activity and expands an inline one-line note field; long-press removes.
- **Notes card:** single free-text field.
- **Report preview card:** live-rendered report text + **Send to WhatsApp** button + status badge.

### 5.2 History (tab)
- Month calendar strip with status dots, list of day cards below (date, hours, first activity categories, status badge).
- Day tap → Day Editor (same layout as Today, bound to that date).
- Month header shows the monthly summary (F11).

### 5.3 Settings (tab)
- Office location (map picker) + geofence radius (default 150 m) + geofence on/off.
- Work-week day toggles; report reminder time.
- Arrival/departure confirmation behavior: confirm-via-notification (default) or auto-log silently (opt-in, for users who accept occasional misfires).
- Manage activity categories.
- Report template preview; export JSON/CSV; about.

### 5.4 Notifications
| Trigger | Content | Actions |
|---|---|---|
| Geofence enter (workday, no arrival set) | "Arrived? Log 08:12" | **Confirm** / **Adjust** (opens app) |
| Geofence exit ≥ 20 min after arrival | "Departed 17:35?" | **Confirm** / **Adjust** |
| Report time (day has data) | Rendered report preview | **Send to WhatsApp** / **Edit day** |
| Report time (day empty/incomplete) | "Complete today's log" | **Open** |
| Report time + 2 h, still unsent | Single gentle repeat | same |

All notifications use distinct channels so each type can be tuned/muted in system settings.

---

## 6. Technical Architecture

### 6.1 Stack

| Concern | Choice | Rationale |
|---|---|---|
| Language | Kotlin | platform standard |
| UI | Jetpack Compose + Material 3 | modern, fast iteration |
| Architecture | MVVM per Android's official architecture guidance: UI → ViewModel → Repository → Room/DataStore; unidirectional data flow with Kotlin Flow | "architecturally correct" per Google's app-architecture guide without over-engineering a single-developer app |
| DI | Hilt | standard, testable |
| Persistence | Room (SQLite) | relational fits day/entries model; migrations |
| Preferences | Jetpack DataStore (Proto or Preferences) | settings |
| Background work | WorkManager (daily reminder), BroadcastReceiver (geofence events, boot) | survives Doze/reboot |
| Location | Play Services GeofencingClient | battery-safe OS geofencing |
| Navigation | Compose Navigation, 3 top-level destinations | simple |
| Testing | JUnit + Turbine (ViewModel/Flow), Room in-memory tests, one Compose UI smoke test | proportional coverage |

Single Gradle module, package-by-feature (`today`, `history`, `settings`, `reporting`, `geofence`, `data`). Multi-module is deliberate over-engineering at this scale.

### 6.2 Data model (Room)

```
WorkDay
  date: LocalDate  (PK, ISO yyyy-MM-dd)
  arrival: LocalTime?
  departure: LocalTime?
  notes: String
  reportedAt: Instant?          // null = not yet sent
  arrivalSource: MANUAL | GEOFENCE
  departureSource: MANUAL | GEOFENCE

FieldJob
  id: Long (PK)
  date: LocalDate (FK → WorkDay, indexed)
  title: String                 // client / site
  locationText: String?
  start: LocalTime?
  end: LocalTime?

Activity
  id: Long (PK)
  date: LocalDate (FK → WorkDay, indexed)
  categoryId: Long (FK → Category)
  note: String
  sortOrder: Int

Category
  id: Long (PK)
  name: String
  emoji: String?
  isHidden: Boolean
  sortOrder: Int                // seeded with the 8 defaults on first run
```

`WorkDay` is created lazily on first fact logged for a date. A single `@Transaction` query (`DayWithEntries`) feeds the Today/Day-Editor screen. Status is derived, not stored: Empty (no row) / In progress (`reportedAt == null`) / Reported.

### 6.3 Report generation

`ReportBuilder` is a pure function `(DayWithEntries, Settings) -> String` — trivially unit-testable and shared by the preview card, the notification, and the share intent. Sending uses:

```kotlin
Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, reportText)
    setPackage("com.whatsapp")      // fall back to chooser if not installed
}
```

Note: Android cannot pre-select the *group* — WhatsApp opens its own chat picker (frequent chats on top). `wa.me` deep links only work for individual numbers, not groups; this is accepted (§2.2). On successful launch of the intent, the app marks `reportedAt` (with an undo snackbar, since we can't observe the actual Send tap inside WhatsApp).

### 6.4 Reminder subsystem

- `WorkManager` unique periodic work is deliberately avoided (its ±15 min drift is fine, but day-of-week logic is cleaner with one-shot chaining): schedule a **one-time `ReminderWorker`** for the next configured workday/time; on completion it schedules the next one.
- `BOOT_COMPLETED` receiver re-schedules after reboot; rescheduling also runs on app open (self-healing).
- The worker inspects the day's data and posts the appropriate notification variant (§5.4).

### 6.5 Geofence subsystem

- One geofence (office, configurable radius, `ENTER | EXIT` transitions) registered via `GeofencingClient`; re-registered on boot and when the office location/setting changes.
- `GeofenceReceiver` (BroadcastReceiver) filters events: workday? arrival already set? exit ≥ 20 min after arrival (debounce against lunch-run misfires)? → posts confirmation notification. **Confirm** action writes the time via a `NotificationActionReceiver`; nothing is written without confirmation (unless silent mode is opted into, §5.3).
- Permissions: `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION`, requested progressively with an explanation screen, only when the user enables geofencing in Settings/setup. Denial leaves the app fully functional (F14).

### 6.6 Export & backup

- **Export:** JSON (full fidelity, re-importable in a future version) and CSV (one row per day + flattened activity summary) generated with `kotlinx.serialization`, shared via `FileProvider` + `ACTION_SEND`.
- **Backup:** Android Auto Backup enabled (DB + DataStore < 25 MB by orders of magnitude); documented caveat that device-local data still warrants a periodic JSON export to Drive/email.

### 6.7 Permissions summary

| Permission | Why | When asked |
|---|---|---|
| POST_NOTIFICATIONS | reminders, geofence confirmations | first run (core) |
| ACCESS_FINE_LOCATION → ACCESS_BACKGROUND_LOCATION | geofencing only | only when enabling geofence feature |
| RECEIVE_BOOT_COMPLETED | re-arm reminder & geofence | manifest, no prompt |
| SCHEDULE_EXACT_ALARM | **not requested** — WorkManager's inexact timing (± minutes) is acceptable for the reminder | — |

---

## 7. Milestones

| # | Deliverable | Definition of done |
|---|---|---|
| M1 | Core logging + report | Today screen, manual times, activities, field jobs, notes; report preview; share to WhatsApp; Room + tests for `ReportBuilder` |
| M2 | Reminder | Scheduled reminder with all notification variants, boot-safe; day status lifecycle |
| M3 | History | History tab, day editor for past dates, re-send |
| M4 | Geofencing | Setup flow, confirm notifications, debounce, permission UX |
| M5 | Polish | Settings complete, category editor, monthly summary, export, Auto Backup, app icon |

M1+M2 alone already deliver the core value (log + reminded send) and ship-ready; M4 is the only risky milestone and is deliberately isolated.

## 8. Risks & open questions

| Risk | Mitigation |
|---|---|
| WhatsApp changes share-intent behavior | Report is plain text via standard `ACTION_SEND`; worst case falls back to the generic share sheet — flow survives |
| Geofence unreliability (OEM battery killers) | Suggestions-only design means a missed geofence costs one manual tap, never data corruption; manual path is always primary |
| OEM notification throttling of reminder | Notification channel set to high importance; self-healing rescheduling on app open |
| Marking day "Reported" on intent launch, not actual send | Undo snackbar + re-send always available; acceptable inaccuracy |

**Open questions for the product owner:**
1. Report language — English labels OK for v1, or is another language needed for the group from day one?
2. Work-week default — Mon–Fri or Sun–Thu?
3. Should the monthly summary (F11) also be sendable to WhatsApp as a text message?
4. Any need to attach photos to field jobs (site evidence) in a future version? (Affects nothing in v1 but worth noting in the data model early.)

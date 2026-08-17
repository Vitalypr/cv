# DayLog (יומן עבודה) — Consultant Daily Work Logger

**Document type:** CONOPS + Technical Specification
**Status:** Draft v0.7 — v0.6 + period PDFs, unified day-total rule, map picker (N3 revised)
**Platform:** Android (native)
**Language & locale:** Hebrew UI and reports, full RTL; Israel defaults (Sun–Thu work-week, Asia/Jerusalem, dd.MM.yyyy)
**Author:** Vitaly (product owner) with Claude (co-author)

---

## 1. Purpose & Vision

DayLog is a personal Android app for an independent consultant working in Israel to document each working day — arrival and departure times, out-of-office field jobs, and the activities performed (discussion, installation, testing, development, design, etc.) — and to produce a clean daily report in Hebrew that is sent to a WhatsApp group at the end of the day with minimal friction. The entire app — UI, notifications, and reports — is in Hebrew with proper right-to-left layout.

**Design principles (in priority order):**

1. **Zero-thought logging** — capturing a fact about the day must take one tap or less (geofence suggestions).
2. **The report is the product** — everything the app collects exists to produce one tidy WhatsApp message per day (plus an on-demand monthly summary).
3. **Private by default** — no backend of ours, no accounts, no analytics; data lives on the device. The only cloud touchpoint is Android's own Auto Backup to the user's Google account, which is on by default and can be disabled (§6.6).
4. **Honest automation** — automate what platforms allow (reminders, pre-filled report), never fight platform rules (no unofficial WhatsApp APIs).

**Explicit non-goals (v1):** multi-user support, client/project billing tags, invoicing, cloud sync, iOS.

---

## 2. CONOPS — Concept of Operations

### 2.1 Actor

A single consultant who works most days (Sunday–Thursday) from a primary office, with occasional out-of-office field jobs (client sites, installations, commissioning). At end of day, they report a summary in Hebrew to a WhatsApp group (e.g., with the client or their own management).

### 2.2 Key constraint: WhatsApp delivery

WhatsApp provides **no API for posting to a group automatically**. The official Business Cloud API can only message individual opted-in numbers, and unofficial automation risks a number ban. Therefore DayLog uses the **reminder + share-intent** pattern:

> At the configured time, DayLog posts a notification with the finished report. One tap opens the Android share flow → WhatsApp → the group (WhatsApp lists frequent chats first) → Send.

Total human effort: **3 taps per day** (notification action, group, Send). This is the maximum automation possible without violating WhatsApp's terms of service. The spec treats this as a hard constraint, not a temporary limitation.

### 2.3 Operational scenarios

**S1 — Typical office day**
1. 08:12 — Consultant enters the office geofence. Notification: *"הגעת למשרד? רישום כניסה 08:12"* → tap **אישור** (or open app to adjust the time).
2. During the day — opens the app 2–3 times for ~15 seconds each: taps activity category chips (e.g., **פיתוח**, **דיון**) and optionally adds a one-line note per activity.
3. 17:35 — leaves the geofence. Notification: *"יציאה 17:35?"* → **אישור**.
4. 17:45 (configured report time) — Notification shows the rendered report with a **שליחה לוואטסאפ** action. Tap → WhatsApp → group → Send. Day is marked **נשלח** ✓.

**S2 — Field-job day**
1. Consultant taps **+ עבודת שטח** in the app: enters location/client name, start time; end time on return. (Geofence still logs office arrival/departure if they pass through the office.)
2. Field jobs appear as a distinct 🚗 section in the report.
3. Report flow identical to S1.

**S3 — Forgot to log / still at the office at report time**
- Report time arrives and the day has **no arrival** → notification *"השלם את יומן היום"* opens the **Day Editor** instead of sending.
- Report time arrives with **arrival set but no departure** (still at the office) → notification *"עדיין במשרד? השלם יציאה ושלח"* with actions **עריכה** and **שליחה בכל זאת** (report renders without the Out line and without duration).
- All times and activities are freely editable for **any past day**; a past day's report can be regenerated and sent late (report header carries the day's date, so late sending is unambiguous).

**S4 — Non-working day, days off, and holidays**
No geofence prompts and no reminder fire on days outside the configured work-week (default Sun–Thu). Exceptions, all explicit:
- **Worked on a weekend:** logging any fact for that day makes the reminder fire for it that evening like a normal day.
- **Day off on a workday:** one tap marks the day as **חופש** (vacation/day off).
- **Holiday:** one tap marks the day as **חג**.
Both special day types suppress the "complete your log" nag and geofence prompts for that date, produce no report, and are counted separately in Statistics (vacation days vs. holidays). Marking is available on the Today screen and on any past day in History.

**S5 — Reviewing history**
Consultant opens the **History** tab: a calendar/list of past days with status badges (נשלח ✓ / נרשם, לא נשלח / חופש / חג / ריק). Tapping a day opens the **Day Editor** (same layout as Today, bound to that date) with a **שליחה מחדש** action.

**S6 — Statistics**
Consultant opens the **Statistics** tab and switches between **שבוע / חודש / שנה** views. Each view shows KPI tiles (total hours, work days, field days, average day length, average arrival, average departure), a stacked office/field hours chart (per day for week/month, per month for year) with an average reference line and tap tooltips, an activity-category breakdown, and a **שיתוף** button that sends that period's text summary (§2.5) through the same WhatsApp share flow as the daily report.

### 2.4 The daily report (the product)

**Delivery format (v0.5):** the daily report is sent to WhatsApp as a **styled PDF document** (A4, Hebrew RTL, brand-styled header) with the plain-text report attached as the message caption — the group reads the text instantly and opens the PDF for the styled record. The plain text below remains the single source of truth (`ReportBuilder`); the PDF is a visual rendering of the same content (`ReportPdf`, native `android.graphics.pdf` — no new dependencies, no network). If caption support is unavailable, WhatsApp delivers the document alone; the text is always recoverable from the preview card.

Plain text in Hebrew (WhatsApp-friendly, no markdown dependency), rendered from a template:

```
‏📋 דוח יומי — יום ג׳ 05.08.2026
‏🕗 כניסה: 08:12 | יציאה: 17:35 | סה״כ 9:23
‏🚗 שטח: תחנת משנה אקמה — הרצה (10:00–13:30)
‏✅ פעילויות:
‏• התקנה (09:00–11:30) — חיווט לוח, תא 4 · תוצאה: הושלם
‏• בדיקות (11:30–13:00) — בדיקות קבלה לממסרים · תוצאה: עברו
‏• דיון — סקירת ליקויים עם מנהל האתר
‏📝 הערות: הוזמן CT רזרבי, צפי הגעה יום חמישי
```

Template rules:
- Sections with no content are omitted entirely (no "שטח: —"). Missing fragments are omitted too: no departure → the יציאה and סה״כ segments are dropped; a field job with only a start time renders `(10:00–…)`, with no times renders without parentheses.
- Activity lines are `קטגוריה (שעה–שעה) — הערה · תוצאה: …`; the time range, note, and result are each optional and each fragment is omitted when empty (a bare category renders alone; a start with no end renders `(09:00–…)`).
- Header date always present → safe to send late. Dates dd.MM.yyyy, Hebrew day names (יום א׳–ש׳), Western numerals, 24-hour times.
- **RTL correctness in WhatsApp:** every line begins with an invisible RLM (U+200F) so lines that start with emoji or digits still render right-to-left; times and number ranges are wrapped so `10:00–13:30` doesn't flip. `ReportBuilder` owns this and it is unit-tested against golden strings.
- Fixed labels are string resources (Hebrew is the app's default locale); adding another language later is trivial.

### 2.5 Period summaries (shareable on demand)

Generated in the Statistics tab for the selected week, month, or year and — like the daily report (v0.7) — sent as a **styled Ledger-design PDF with the plain text as the WhatsApp caption** (monthly example):

```
‏📊 סיכום חודשי — אוגוסט 2026
‏ימי עבודה: 21 | סה״כ שעות: 186:30
‏🚗 ימי שטח: 6
‏✅ פעילויות: פיתוח 14 · התקנה 9 · דיון 7 · בדיקות 5
```

**Hours rule:** a day's total = office span (arrival→departure) **plus** field-job time that falls outside the office span (no double counting; partial overlap adds only the outside part); a day with only field jobs = sum of field-job spans. **v0.7: this single rule feeds every daily total** — the daily report's סה״כ line, the PDF summary cell, the Today screen total, and Statistics.

---

## 3. Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| F1 | Record arrival and departure time per day; manual entry/edit always possible | Must |
| F2 | Geofence around a user-defined office location suggests arrival/departure via confirmable notifications; a geofence confirmation never overwrites a MANUAL-source value | Must |
| F3 | Log zero or more field jobs per day: title/client, optional location text, start/end times | Must |
| F4 | Log zero or more activities per day from a category list — defaults: דיון, התקנה, בדיקות, פיתוח, תכנון, תיעוד, תמיכה, אחר — each entry with optional start/end times, optional free-text note, and optional result; multiple entries per category allowed (e.g., two separate discussions) | Must |
| F5 | Category list is user-editable (add/rename/hide; no hard delete — history must keep rendering) | Should |
| F6 | Free-text daily notes field | Must |
| F7 | Daily reminder notification at a configured time, on configured workdays **or any day that has data**, with the variants of §5.4 | Must |
| F8 | Send action opens WhatsApp (or WhatsApp Business) with the report text; system chooser as fallback | Must |
| F9 | Day status (derived): ריק / נרשם, לא נשלח / נשלח / נשלח (עודכן) / חופש / חג; re-send possible any time and overwrites the sent timestamp | Must |
| F10 | History view: browse, edit, and re-send any past day; mark any day as day-off (חופש) or holiday (חג) — also available for today on the Today screen | Must |
| F11 | Statistics tab with weekly/monthly/yearly views: KPI tiles (total hours, work days, field days, avg day length, avg arrival, avg departure, off/holiday counts), stacked office/field hours chart with average reference line and tooltips, activity breakdown; each period's summary (§2.5) shareable to WhatsApp | Must |
| F12 | Export all data as JSON (full fidelity, versioned schema) and CSV via share sheet | Should |
| F13 | Android Auto Backup of DB + settings (on by default, off toggle in Settings) | Should |
| F14 | Works fully without location permission (geofencing simply off) | Must |

## 4. Non-Functional Requirements

- **N1 Simplicity:** every daily interaction reachable in ≤ 2 taps from app open; no login; setup is 3 steps (office location, work-week — default Sun–Thu, report time).
- **N2 Reliability:** the reminder fires within ~10 minutes of the configured time even under Doze (AlarmManager `setAndAllowWhileIdle`, §6.4) and survives reboot; a geofence misfire can never corrupt data — suggestions are committed only on user confirmation.
- **N3 Privacy (revised v0.7 by product-owner decision):** INTERNET is declared **solely for OpenStreetMap tile downloads in the location picker** (the merged-manifest test documents this exception); no analytics, no third-party services, no backend; location is used only for geofence triggers and coordinates are never stored in history. Documented exceptions: OSM tiles (map picker) and Android Auto Backup (F13).
- **N4 Performance:** cold start < 1.5 s on a mid-range device; the Today screen renders from a single DB query.
- **N5 Data safety:** Room DB with export; schema migrations versioned from day one.
- **N6 Battery:** geofencing via Play Services GeofencingClient (OS-managed, near-zero battery), never continuous location polling.
- **N7 Hebrew/RTL:** `android:supportsRtl="true"`; Hebrew is the default resource locale; all screens verified in RTL; reports pass RTL golden-string tests (§2.4).

---

## 5. UX Specification

Four-tab bottom navigation (היום / היסטוריה / סטטיסטיקה / הגדרות), fully RTL. Visual language: Material 3, dynamic color, large touch targets, no decoration that doesn't serve logging speed.

### 5.1 Today (היום)
- **Time card:** big `כניסה —:—` / `יציאה —:—` values; tap a value to set/adjust via time picker; **הגעתי** / **יצאתי** one-tap buttons when unset.
- **Field jobs card:** list + **+ עבודת שטח** button (bottom-sheet form: title, optional location, start/end).
- **Activities card:** category chips in a flow row; tapping a chip adds a new activity entry below — an inline row with optional start/end time pickers, a one-line note field, and an optional result field. Each entry has its own remove control; a chip is highlighted while it has at least one entry, and tapping it again adds another entry of the same category.
- **Special-day row:** compact **חופש** / **חג** toggle chips in the time card; marking either suppresses the reminder and geofence prompts for the day and replaces the report preview with a "no report today" state (S4).
- **Notes card:** single free-text field.
- **Report preview card:** live-rendered report text + **שליחה לוואטסאפ** button + status badge.
- On a non-workday the screen shows a "יום חופש" state with a **רישום יום בכל זאת** action (S4).

### 5.2 History (היסטוריה)
- Month calendar strip with status dots; list of day cards below (date, hours, first activity categories, status badge).
- Day tap → Day Editor (same layout as Today, bound to that date), with **שליחה מחדש** and **חופש / חג** marking.

### 5.3 Statistics (סטטיסטיקה)
- **Period selector:** segmented control שבוע / חודש / שנה with previous/next arrows.
- **KPI tiles** (2-column grid): סה״כ שעות, ימי עבודה, ימי שטח, ממוצע ליום, כניסה ממוצעת, יציאה ממוצעת; off/holiday counts as a secondary line.
- **Hours chart:** stacked bars — office hours (`#00897B`) + field hours (`#9E6410`), a two-hue palette validated for color-vision deficiency and 3:1 surface contrast — per day (week/month) or per month (year), right-to-left time axis, dashed average reference line, 2px surface gaps between stacked segments, tap tooltip per bar with exact values, legend above the plot. Rendered with Compose Canvas (no chart library dependency); exact values always available via the KPI tiles and share text (accessibility relief).
- **Activity breakdown:** horizontal single-hue bars with count labels per category.
- **שיתוף** button: sends the selected period's text summary (§2.5).

### 5.4 Settings (הגדרות)
- Office location: **"קבע למיקום הנוכחי"** (one-shot fix while standing at the office) **or a map pin picker** (v0.7: OpenStreetMap via osmdroid — no API keys, no Google account; requires the N3-revised INTERNET permission for tile downloads). Job locations get the same two options. Geofence radius (default 150 m) + on/off.
- Work-week day toggles (default Sun–Thu); report reminder time.
- Arrival/departure confirmation behavior: confirm-via-notification (default) or auto-log silently (opt-in; silent mode still never overwrites MANUAL values).
- Manage activity categories; Auto Backup toggle; report template preview; export JSON/CSV; about.

### 5.5 Notifications

| Trigger | Content | Actions |
|---|---|---|
| Geofence ENTER (workday, no arrival set) | "הגעת? רישום כניסה 08:12" | **אישור** / **עריכה** (opens app) |
| Geofence EXIT (arrival set, departure unset) | "יציאה 17:35?" | **אישור** / **עריכה** |
| Geofence EXIT (departure already set, GEOFENCE source) | "לעדכן יציאה ל־19:05?" | **עדכון** / dismiss |
| Geofence EXIT (no arrival set) | "לרשום את היום? יציאה 17:35" | **פתיחה** (opens Day Editor) |
| Report time, arrival+departure set | Rendered report preview | **שליחה לוואטסאפ** / **עריכה** |
| Report time, arrival set, no departure | "עדיין במשרד? השלם יציאה ושלח" | **עריכה** / **שליחה בכל זאת** |
| Report time, no arrival (and not day-off) | "השלם את יומן היום" | **פתיחה** |
| Report time + 2 h, still unsent | Single gentle repeat (not past 23:30) | same as applicable variant |

Rules: **Confirm always writes the geofence event time**, not the tap time; suggestion notifications auto-expire at midnight; a geofence re-ENTER cancels a pending departure suggestion; EXIT prompts are debounced 10 minutes against boundary jitter (an exit followed by re-entry within 10 min produces nothing). All notifications use distinct channels so each type can be tuned/muted in system settings.

### 5.6 Home-screen widget (v0.8)

A **4 x 1 widget** (horizontally resizable down to 2 cells) with two pill buttons — **כניסה** (petrol) and **יציאה** (ink) — for logging without opening the app.

- Each pill shows a **live clock in red** = the time a tap would record, i.e. still missing. Once logged, the pill shows that value **frozen, in white, with a green ✓**, so the home screen answers "did I log it?" at a glance.
- The background is **transparent** — only the two pills are drawn, over the wallpaper.
- Text scales to the slot the launcher grants (labels drop below 56dp so the times never clip).
- A tap writes the **real current time** with **MANUAL** source, which by the §6.6 source rules **overrides** whatever is stored — a geofence suggestion or an earlier tap. Tapping again re-records.
- On a **חופש/חג** day the buttons are replaced by a "לא נרשמות שעות" line (S4 — special days accept no hours).
- **Battery (N6):** the widget schedules nothing. The live time is a system `TextClock`; `updatePeriodMillis=0`, and redraws happen only on real changes (widget tap, geofence write, leaving the app) plus `DATE_CHANGED` at midnight so a previous day's ✓ never lingers.

---

## 6. Technical Architecture

### 6.1 Stack

| Concern | Choice | Rationale |
|---|---|---|
| Language | Kotlin | platform standard |
| UI | Jetpack Compose + Material 3 | modern, fast iteration; RTL comes from the locale automatically |
| Architecture | MVVM per Android's official architecture guidance: UI → ViewModel → Repository → Room/DataStore; unidirectional data flow with Kotlin Flow | "architecturally correct" per Google's app-architecture guide without over-engineering a single-developer app |
| DI | Hilt | standard, testable |
| Persistence | Room (SQLite) | relational fits day/entries model; migrations |
| Preferences | Jetpack DataStore | settings |
| Reminder scheduling | AlarmManager `setAndAllowWhileIdle` | WorkManager is deferrable and may slip hours under Doze — unacceptable for an evening-time reminder; inexact-while-idle (≈±10 min) is acceptable without the SCHEDULE_EXACT_ALARM policy burden |
| Location | Play Services GeofencingClient | battery-safe OS geofencing |
| Navigation | Compose Navigation, 3 top-level destinations | simple |
| Localization | Hebrew as default locale (`values/`), `supportsRtl`, RTL screenshot tests, report golden-string tests | Hebrew-first product |
| Testing | JUnit + Turbine (ViewModel/Flow), Room in-memory tests, ReportBuilder golden tests, one Compose UI smoke test | proportional coverage |

Single Gradle module, package-by-feature (`today`, `history`, `settings`, `reporting`, `geofence`, `data`). Multi-module is deliberate over-engineering at this scale.

### 6.2 Time model

The one place bare wall-clock times would bite is midnight and travel, so:

- `WorkDay.date` (the PK) is the **logical day** a record belongs to.
- Arrival/departure/field-job times are stored as **minutes from midnight of `date`**, and may exceed 1440: a departure at 01:30 after a day that started 05.08 is stored on 05.08 as 25:30 and **renders as `01:30 (למחרת)`**; durations compute correctly across midnight.
- Day-assignment rule: a departure logged between 00:00–04:00 is offered to the previous day if that day has an open arrival; otherwise it belongs to the current day.
- All wall-clock values are captured in the device's local timezone at event time and never converted. The reminder alarm is rescheduled on `TIMEZONE_CHANGED` / `TIME_CHANGED` broadcasts. `reportedAt` alone is an absolute `Instant` (audit value).

### 6.3 Data model (Room)

```
WorkDay
  date: LocalDate  (PK, ISO yyyy-MM-dd)     // the logical day
  arrivalMin: Int?                           // minutes from midnight of `date`
  departureMin: Int?                         // may exceed 1440 (past midnight)
  notes: String
  dayType: WORK | OFF | HOLIDAY              // OFF=חופש, HOLIDAY=חג — suppress reminder/geofence, no report (S4)
  reportedAt: Instant?                       // null = never sent; overwritten on re-send
  editedAfterReport: Boolean                 // drives the "נשלח (עודכן)" badge
  arrivalSource / departureSource: MANUAL | GEOFENCE

FieldJob
  id: Long (PK)
  date: LocalDate (FK → WorkDay, indexed)
  title: String                              // client / site
  locationText: String?
  startMin: Int?                             // MANUAL times — always win
  endMin: Int?
  jobLocationId: Long?                       // v0.6: link to JobLocation when geo-created
  suggestedStartMin: Int?                    // v0.6: first geofence ENTER of the day
  suggestedEndMin: Int?                      // v0.6: last geofence EXIT (each exit overwrites)

JobLocation                                  // v0.6
  id: Long (PK)
  name: String
  lat: Double, lon: Double
  radiusM: Int                               // default 2000
  isActive: Boolean

Activity
  id: Long (PK)
  date: LocalDate (FK → WorkDay, indexed)
  categoryId: Long (FK → Category)           // multiple rows per category allowed
  startMin: Int?                             // optional; minutes from midnight of `date`
  endMin: Int?                               // optional; may exceed 1440
  note: String
  result: String                             // optional outcome, e.g. "הושלם", "עברו"
  sortOrder: Int                             // report order: startMin when set, else sortOrder

Category
  id: Long (PK)
  name: String                               // seeded in Hebrew (F4 defaults)
  emoji: String?
  isHidden: Boolean                          // hide, never delete — history keeps rendering
  sortOrder: Int
```

`WorkDay` is created lazily on first fact logged for a date. A single `@Transaction` query (`DayWithEntries`) feeds the Today/Day-Editor screen. Status is derived: ריק (no row) / נרשם, לא נשלח (`reportedAt == null`) / נשלח / נשלח (עודכן) (`editedAfterReport`) / חופש or חג (`dayType`).

### 6.4 Report generation & WhatsApp handoff

`ReportBuilder` is a pure function `(DayWithEntries, Settings) -> String` (and `(MonthSummary, Settings) -> String` for §2.5) — unit-tested against golden Hebrew/RTL strings and shared by the preview card, the notification, and the share intent.

Sending:

```kotlin
Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, reportText)
    setPackage(resolveWhatsAppPackage())   // com.whatsapp, else com.whatsapp.w4b
}
```

- The manifest declares `<queries>` entries for `com.whatsapp` and `com.whatsapp.w4b` (required on Android 11+ for package visibility). If neither resolves, or `startActivity` throws `ActivityNotFoundException`, fall back to `Intent.createChooser`.
- Android cannot pre-select the *group* — WhatsApp opens its own chat picker (frequent chats on top); `wa.me` deep links only work for individual numbers. Accepted (§2.2).
- **Notification action path (trampoline-safe):** the "שליחה לוואטסאפ" action's PendingIntent launches a transparent `SendReportActivity` directly (no broadcast-then-launch, which Android 12+ forbids); it writes `reportedAt`, fires the share intent, and finishes. Mis-marks are covered by the always-available re-send (F9), which also clears `editedAfterReport`.

### 6.5 Reminder subsystem

- One `AlarmManager.setAndAllowWhileIdle` alarm for the next reminder-eligible day (a configured workday, or any day that already has data); on firing, the receiver posts the appropriate §5.4 variant and schedules the next alarm.
- Alarms do not survive reboot: a `BOOT_COMPLETED` receiver re-schedules (and re-registers the geofence); rescheduling also runs on app open and on timezone/time-change broadcasts (self-healing).
- The +2 h repeat is skipped past 23:30 and never crosses into the next day.

### 6.6 Geofence subsystem

- One geofence (office, configurable radius, `ENTER | EXIT`) registered via `GeofencingClient` **with initial triggers disabled** — setting the office location while sitting in the office must not fire a spurious "הגעת?" prompt. Re-registered on boot and when the office location/setting changes.
- `GeofenceReceiver` implements the §5.4 decision table with these invariants: nothing is written without user confirmation (unless silent mode is opted in); a geofence confirmation **never overwrites a MANUAL-source value**; confirm writes the event time; a 10-minute exit debounce absorbs boundary jitter; re-enter cancels a pending departure suggestion. A mid-day exit (e.g., lunch) that the user confirms simply sets a departure that a later exit offers to update — last confirmed exit wins.

**Ordering invariants (v0.9).** Play Services reports a transition when it next obtains a fix, not when the boundary was crossed, so transitions arrive late and out of order — an exit can be delivered *after* the entry that followed it. Deciding from the wall clock alone made arriving at the office produce a departure suggestion. Therefore:

| Invariant | Rule |
|---|---|
| **Occupancy** | An EXIT is acted on only if the matching ENTER was recorded (persisted per fence). An exit with no recorded entry is dropped silently — never prompted, never written. |
| **Event time** | Every decision and write uses the transition's own timestamp (`triggeringLocation.time`) and its logical day — never "now". Confirming near midnight lands on the day of the event, not the day of the tap. |
| **Day attribution** | An exit belongs to the entry's day; if it crosses midnight before 04:00 and that day is still open it stays on that day (§6.2); otherwise it belongs to no day we can trust and is dropped. |
| **Staleness** | A transition older than 60 minutes is a catch-up delivery and cannot start a suggestion. |
| **Dwell** | Under 5 minutes inside the office is a drive-past: the pending arrival suggestion is withdrawn and no exit prompt follows. "לרשום את היום?" additionally requires a 30-minute stay, so indoor GPS drift cannot ask the user to log a day they have just started. |
| **Same place** | A job location whose pin falls inside the office fence is not tracked separately — otherwise every office arrival also opens a field job. |
| **Job visits** | A job EXIT with no recorded ENTER creates nothing (it used to invent a field job holding only an end time), and an untouched visit shorter than 15 minutes inside a 2 km fence is discarded as a drive-past. |
- Office location is captured via a one-shot `FusedLocationProvider` fix ("קבע למיקום הנוכחי") — no Maps SDK, no network.
- Permissions: `ACCESS_FINE_LOCATION` in-app, then `ACCESS_BACKGROUND_LOCATION`, which on Android 11+ **cannot be granted from an in-app dialog** — the flow explains and deep-links to system settings ("אפשר תמיד"). Requested only when the user enables geofencing; denial leaves the app fully functional (F14) and reverts the toggle. Play Console background-location declaration is an M4 deliverable.

### 6.6b Job-location tracking (v0.6)

Beyond the office fence, the user saves **job locations** (client sites): name + one-shot coordinate capture, each with a **2 km radius** geofence (client-site arrival doesn't need office-grade precision; 2 km absorbs parking, gates, and large sites).

- **First-enter / last-exit per day:** the first ENTER of a day writes the field job's *suggested start*; **every EXIT overwrites the suggested end** — so a lunch exit is automatically superseded by the final exit. No dwell heuristics needed; the surviving pair is first arrival / last leave.
- On first ENTER the field job row for that day/location is **auto-created** (titled by the location name) if the user hasn't already added one.
- Suggested times live in separate columns (`suggestedStartMin/EndMin`) and render **in amber with a מוצע tag**; a manually entered time always wins and is never overwritten (same invariant as F2). Reports and statistics use manual-if-set-else-suggested.
- Job fences are registered alongside the office fence (single GeofencingClient request set, request IDs `office` / `job_<id>`), governed by the same geofence master toggle and permissions.

### 6.7 Export & backup

- **Export:** JSON via `kotlinx.serialization` (full fidelity, schema version field from day one, re-importable in a future version) and a hand-rolled CSV (one row per day: date, in, out, total, day-off, field-job count and titles `;`-joined, activity categories `;`-joined, notes) — both shared via `FileProvider` + `ACTION_SEND`.
- **Backup:** Android Auto Backup enabled by default (DB + DataStore, far below the 25 MB cap) with an off toggle (N3's documented exception); Settings recommends a periodic JSON export to Drive/email as belt-and-braces.

### 6.8 Permissions summary

| Permission | Why | When asked |
|---|---|---|
| POST_NOTIFICATIONS | reminders, geofence confirmations | first run (core) |
| ACCESS_FINE_LOCATION → ACCESS_BACKGROUND_LOCATION | geofencing only | only when enabling geofence feature; background grant via system-settings redirect (§6.6) |
| RECEIVE_BOOT_COMPLETED | re-arm alarm & geofence after reboot | manifest, no prompt |
| SCHEDULE_EXACT_ALARM | **not requested** — `setAndAllowWhileIdle` (±10 min) suffices | — |
| INTERNET + ACCESS_NETWORK_STATE | OSM map tiles in the location picker ONLY (N3 rev v0.7) | manifest, no prompt |

---

## 7. Milestones

| # | Deliverable | Definition of done |
|---|---|---|
| M1 | Core logging + report | Today screen (RTL/Hebrew), manual times incl. past-midnight, activities, field jobs, notes, day-off; report preview; WhatsApp handoff with fallbacks; Room + golden-string tests for `ReportBuilder` |
| M2 | Reminder | AlarmManager scheduling, all §5.4 report-time variants, boot/timezone-safe; day status lifecycle incl. edited-after-report |
| M3 | History + statistics | History tab, day editor for past dates, re-send, day-off/holiday marking; Statistics tab with week/month/year KPIs, stacked hours chart, activity breakdown, period share (F11) |
| M4 | Geofencing | Setup flow (current-location capture), §5.4 geofence variants, debounce/override invariants, background-location UX + Play declaration |
| M5 | Polish | Category editor, export JSON/CSV, Auto Backup + toggle, template preview, app icon |

**M1–M3 constitute the shippable product** (all Must requirements except geofencing's F2, which degrades gracefully per F14); M4 is the only risky milestone and is deliberately isolated.

## 8. Risks & open questions

| Risk | Mitigation |
|---|---|
| WhatsApp changes share-intent behavior | Report is plain text via standard `ACTION_SEND`; worst case falls back to the generic chooser — flow survives |
| Geofence unreliability (OEM battery killers) | Suggestions-only design means a missed geofence costs one manual tap, never data corruption; manual path is always primary |
| OEM throttling of reminder notifications | High-importance channel; `setAndAllowWhileIdle` + self-healing rescheduling on app open |
| Marking day "נשלח" on intent launch, not actual send | Accepted inaccuracy; re-send always available and refreshes the timestamp |
| RTL rendering glitches in WhatsApp (emoji/digit line starts) | RLM-prefixed lines, golden-string tests, manual verification on real device in M1 |

**Resolved with the product owner:** language = Hebrew (v0.2 throughout); work-week = Sun–Thu; monthly summary is shareable like the daily report (F11, §2.5); activities carry optional start/end times and an optional result, with repeated categories allowed (v0.3); daily report delivered as a styled PDF with text caption (v0.5).

**Still open:**
1. Should field jobs support photo attachments (site evidence) in a future version? Nothing in v1 changes either way, but a `FieldJobPhoto` table would be added later.
2. Category emoji: seed the 8 default categories with emoji and show them in report activity lines, or keep report lines text-only (current template)?

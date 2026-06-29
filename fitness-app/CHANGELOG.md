# Changelog

All notable changes to the fitness app. Newest first. Keep entries terse and user-facing.

## [Unreleased]

### Added
- Project documentation set: `CLAUDE.md`, `docs/{CONOPS,ARCHITECTURE,ROADMAP,TOOLING,TESTING,TDD,EVAL,DEV_LOOP,REVIEW_FINDINGS}.md`, `docs/adr/0001`, `PROGRESS.md`.
- Baseline single-file app imported under `fitness-app/index.html`.
- `.gitignore` for the Node/Vite/Capacitor toolchain.
- Playwright eval harness (`tests/`, `playwright.config.ts`) with 12 golden-flow + bug-class evals.

### Phase 0 — Hardening
- Security: escape all user-derived text in set-log, nutrition and body tables; harden
  import (type coercion, `selectedDay` clamp, 20MB guard, read-error handling) and snapshot
  previous state to `*_prev` before overwrite.
- Reliability: replace `structuredClone` with a JSON clone so the app boots on older
  WebView/Safari.
- Correctness: exercise done-flags now keyed by a stable id (not array index); show
  "(done/total)" progress; complete-workout is now a toggle.
- UX: app opens to today's scheduled workout with a "שבוע N · יום M" label; workout notes
  persist as you type and ticking an exercise updates in place (no focus/scroll loss).
- Charts: plot points by actual date (not array index) with first/last date labels; redraw
  on resize/orientation change.
- Guards: warn on same-date body overwrite; confirm before stacking a second base menu day.
- A11y: tablist/tab roles + aria-selected, checkbox role/aria-checked on exercise toggles,
  toast as aria-live status, aria-labels on icon buttons.
- Search: exercise-guide search is now case-insensitive.

### Phase 1 — PWA
- Installable + truly offline: web app manifest, cache-first service worker with offline
  navigation fallback, generated app icons (192/512/maskable + apple-touch), iOS meta tags,
  `theme-color`, and a dismissible iOS "Add to Home Screen" hint.
- Architecture: ADR-0002 — stay buildless (single file + assets + vendored libs), keep
  localStorage, wrap with Capacitor; roadmap re-sequenced.

### Phase 2 — Logging UX
- Per-exercise history: the set logger shows the previous session's weight×reps as ghost
  placeholders + a "last time" line, and a history panel of recent sessions for the
  selected exercise (aggregated across all logged days).
- Progressive overload: a set that beats the previous session (heavier, or equal weight for
  more reps) is flagged with a ▲ and a "improvement" toast.
- Rest timer: auto-starts when a set is logged; −15/+15 adjust, skip dismisses, with an
  end-of-rest beep + vibration (Android web). Default rest duration configurable in settings.
- Faster entry: `inputmode` hints on numeric fields for better mobile keypads.

### Phase 3 — Exercise media
- Real exercise photos (31, from the open Free Exercise DB, CC BY-SA) in the exercise guide
  and as thumbnails on each daily exercise row; SVG line-art remains the offline fallback.
- Attribution/license card in settings.
- Fixed a horizontal-overflow layout bug (RTL) via `minmax(0,1fr)` grid tracks; added a
  no-overflow regression guard across all tabs.

### Phase 4 — Stats
- Interactive per-exercise estimated-1RM progression chart (vendored uPlot, MIT) with an
  exercise picker in the tracking tab.
- Workout streak (with single-day forgiveness), workouts-this-week metric, and a
  personal-records table (best lift + estimated 1RM per exercise).

### Phase 5 — Samsung Health / Health Connect (code-complete + handoff)
- Feature-detected health bridge + a Settings "חיבור ל‑Samsung Health" card to (in the
  Android app) connect to Health Connect, pull steps/weight into body metrics, and push
  completed workouts. On the web it degrades gracefully ("Android app only").
- Capacitor scaffold: `capacitor.config.json`, `scripts/build-webdir.mjs` (clean web dir),
  `build:web`/`cap:sync`/`cap:open` scripts, and `docs/ANDROID_HEALTH_CONNECT.md` handoff
  (plugin, manifest permissions, privacy-policy activity, run + verify on device).
- On-device build/runtime not performed in CI (no Android SDK); marked done-with-caveat.

### Android build (installable APK)
- Committed the Capacitor 7 Android project (`fitness-app/android/`) with
  `@devmaxime/capacitor-health-connect` (reads Steps + Weight via Health Connect, which
  aggregates Samsung Health); manifest wired with health permissions, the healthdata
  `<queries>` entry, and permission-rationale intent filters; `minSdk = 26`.
- Re-wired the web health bridge to the plugin's real API (`checkAvailability`,
  `requestPermissions`, `readRecords`); "send workouts" shown as unsupported (read-only plugin).
- Added `.github/workflows/android.yml` — builds a debug APK on push and uploads it as an
  artifact for sideloading (no Android Studio required).

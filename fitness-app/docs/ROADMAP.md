# ROADMAP

Phases run in order. Each phase follows the dev loop (`DEV_LOOP.md`) and is not "done"
until its **acceptance criteria** pass via tests/evals and `PROGRESS.md` is updated.

Legend: ⬜ not started · 🔄 in progress · ✅ done · ⚠️ done-with-caveat (see notes)

---

## Phase 0 — Hardening (in the baseline single file)

Goal: fix correctness/safety/UX bugs without re-architecting, so the app is solid and
honest before the bigger migration. Low risk, high value.

Scope (from `REVIEW_FINDINGS.md`):
- Escape all user-derived text in set-log, nutrition, body tables (stored-XSS fix).
- Replace `structuredClone` with JSON-clone (or feature-detect) so old WebViews don't brick.
- Stop losing the notes textarea (and focus) on re-render: persist on input/blur.
- Key exercise `done` flags by stable id, not array index; show "X/Y done" progress.
- Auto-select today's workout from `startDate` (show "week N · day M"); manual override kept.
- Charts: plot by **date**, not index; label axes; redraw on resize/orientation.
- Guard duplicate template-day add; warn on same-date body overwrite; allow un-complete.
- Tighten import validation (type/shape coercion, clamp `selectedDay`, snapshot prev state).
- Basic a11y: input labels, tab roles, toast live region, checkbox semantics.
- Case-insensitive exercise search.

Acceptance:
- Playwright golden flows G1–G7 (see `EVAL.md`) pass.
- A backup containing `<img onerror>` in a name renders inert (XSS eval passes).
- Typing notes then logging a set preserves the notes (regression eval passes).
- Charts position points proportional to date gaps (unit test on scale mapping).

## Phase 1 — Foundation (Vite + modules + uPlot + IndexedDB)

Goal: convert the hardened single file into a typed, modular, build-based app with the
target rendering/storage/chart stack — behavior-preserving.

Scope:
- Scaffold Vite + TypeScript; `vite-plugin-singlefile` web target still emits one HTML.
- Port to `src/` per `ARCHITECTURE.md` layers; introduce Preact + signals.
- Move state to IndexedDB (`idb-keyval`) with one-time localStorage→IDB migration.
- Replace canvas charts with uPlot (date-scaled).
- Add PWA manifest + service worker + iOS meta tags.
- Wire Vitest + Playwright; port Phase 0 evals; add unit tests for domain layer.

Acceptance:
- All Phase 0 golden flows still pass against the new build.
- `npm run build` emits a working offline single HTML; Lighthouse PWA installable.
- Domain layer unit-test coverage for nutrition math, day/week calc, migrations.

## Phase 2 — Logging UX (the core loop)

Goal: best-in-class in-gym logging.

Scope:
- Single-screen, in-place set logging: per-set rows, big confirm, ≤3 taps.
- Previous-session ghost hints (placeholder `100 × 8`) per set field.
- Auto-start adjustable rest timer (+15/−15, skip) → Capacitor LocalNotifications +
  Haptics on Android; web fallback (in-page + `navigator.vibrate`).
- Per-exercise history view; progressive-overload up-arrows when beating last session.
- `inputmode=decimal`, steppers; one-handed bottom-anchored controls; haptics setting.

Acceptance:
- Eval: repeating last session logs a set in ≤3 taps, 0 mandatory keystrokes.
- Eval: rest timer starts on set confirm and counts down; adjustable.
- Eval: per-exercise history shows last session + trend; PR/▲ when exceeded.

## Phase 3 — Exercise media

Goal: real exercise illustrations replacing/augmenting the line-art.

Scope:
- Bundle Free Exercise DB (or wger API) images; map plan exercises → media.
- Lazy-load images; cache via service worker; SVG line-art remains offline fallback.
- Attribution/licenses page (CC BY-SA credit).

Acceptance:
- Each base exercise shows an illustration (or graceful SVG fallback) offline.
- Licenses page lists sources + credits; eval checks attribution present.

## Phase 4 — Stats

Goal: honest, interactive progress insight.

Scope:
- Per-exercise estimated-1RM / top-set / volume charts (uPlot, interactive).
- Streaks with forgiveness (rest days don't break; no shaming), PR badges,
  workouts-this-week, body-part frequency.

Acceptance:
- Evals for 1RM/volume computation correctness; streak forgiveness logic; PR detection.

## Phase 5 — Samsung Health (Health Connect) — code-complete + handoff

Goal: Android wrapper that reads/writes Samsung Health via Health Connect.

Scope:
- Capacitor Android project; Health Connect plugin; permission flow.
- Read steps/weight/sleep → pre-fill body metrics; write completed workouts.
- All gated behind opt-in; minimum scopes; revocable.

Acceptance (given no Android SDK here):
- TypeScript compiles; plugin wiring + permission/manifest config complete and reviewed.
- `docs/TOOLING.md` "Android handoff" has exact build/run/test steps for the user's machine.
- Marked ⚠️ until the user confirms runtime on a device.

---

## Cross-cutting (every phase)

- Keep export/import working and tested.
- Keep offline working.
- Update `PROGRESS.md`, relevant `docs/`, and add an ADR for any significant decision.

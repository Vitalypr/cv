# PROGRESS

Live status tracker for autonomous work. Update every working session. Source of truth for
phase status while building. Be honest about caveats and unverified items.

Legend: ⬜ not started · 🔄 in progress · ✅ done · ⚠️ done-with-caveat

## Current focus

**Phase 3 — Exercise media** (next: bundle real exercise images, lazy-load, attribution).

## Architecture note

ADR-0002 supersedes ADR-0001's build/stack: we stay **buildless** (single `index.html` +
`assets/` + vendored libs), keep `localStorage`, and wrap with Capacitor (webDir = folder).
Roadmap re-sequenced accordingly.

## Phase status

| Phase | Title | Status | Notes |
|-------|-------|--------|-------|
| Docs | CLAUDE.md + docs set | ✅ | + ADR-0002 (buildless pivot) |
| 0 | Hardening | ✅ | 12/12 evals green. See eval report below. |
| 1 | PWA (manifest + SW + offline + iOS) | ✅ | 14/14 evals green (added PWA-SW, PWA-OFFLINE). F7 closed. |
| 2 | Logging UX (history, rest timer, prev hints, overload) | ✅ | 18/18 evals green (P2-PREVHINT, P2-HISTORY, P2-OVERLOAD, P2-RESTTIMER). F4 closed. |
| 3 | Exercise media (bundled images) | ⬜ | |
| 4 | Stats (uPlot interactive, streaks, PRs) | ⬜ | |
| 5 | Samsung Health (Health Connect) | ⬜ | code-complete + handoff only (no Android SDK here) |
| 2 | Logging UX (in-place, rest timer, history, overload) | ⬜ | |
| 3 | Exercise media | ⬜ | |
| 4 | Stats | ⬜ | |
| 5 | Samsung Health (Health Connect) | ⬜ | code-complete + handoff only (no Android SDK here) |

## Environment

- Node 22, npm 10, Java 21, Chromium+Playwright available.
- **No Android SDK/Gradle** → Android build/runtime is a documented handoff (`docs/TOOLING.md`).

## Decision log (see docs/adr/)

- ADR-0001: Vite+TS+Preact+signals, uPlot, IndexedDB, Capacitor+Health Connect, Vitest+Playwright.

## Session log

- 2026-06-29: Multi-agent audit + research completed. Baseline imported under `fitness-app/`.
  `.gitignore` added. Full docs set authored. (Background CLAUDE.md research agent stalled
  after an MCP disconnect; proceeded from the workflow research + first-principles.)

## Eval reports

### Phase 0 — Hardening (2026-06-29) — ✅

Verification: `npx playwright test` → **12 passed**.

| Criterion | Proof | Result |
|-----------|-------|--------|
| Auto-select today's workout + week/day label | G1 | PASS |
| Log a set persists across reload | G2 | PASS |
| Notes survive a re-render (F2 mitigation) | G3 | PASS |
| Nutrition template updates totals/log | G4 | PASS |
| Body chart spaces points by date (F10) | G5 | PASS |
| Case-insensitive exercise search (F11) | G6 | PASS |
| Backup export/import round-trip | G7 | PASS |
| Stored XSS in names is inert (F1) | E-XSS | PASS |
| done-flag follows identity not index (F3) | E-IDENTITY | PASS |
| Boots without structuredClone (F12) | E-CLONE | PASS |
| Logging works offline | E-OFFLINE | PASS |
| Malformed import handled + `_prev` snapshot (F9) | E-IMPORT-GUARD | PASS |

Findings closed: F1, F3, F5, F6, F9, F10, F11, F12, F13. Mitigated: F2 (full fix P1), F8 (basics; label-for + focus-restore P1).
Gaps/deferred: F4 (P2 history), F7 (P1 PWA), F14 (P1 typed validation), F15 (P1 render fan-out).
Caveat: visual sanity confirmed via screenshot (daily + tracking); no Android runtime in this phase.

RCA: none required — no failing criteria after implementation; all evals were RED pre-fix and GREEN post-fix as designed.

### Phase 1 — PWA (2026-06-29) — ✅

Decision: ADR-0002 (stay buildless; PWA instead of Vite/Preact/TS port).
Verification: `npx playwright test` → **14 passed**.

| Criterion | Proof | Result |
|-----------|-------|--------|
| Manifest linked + valid; theme-color present | PWA-SW | PASS |
| Service worker registers and controls the page | PWA-SW | PASS |
| App shell loads with network fully offline | PWA-OFFLINE | PASS |
| All Phase 0 evals still green | G1–G7, E-* | PASS |

Added: `manifest.webmanifest`, `sw.js` (cache-first + navigation fallback), generated app
icons (192/512/maskable/apple-touch), iOS meta tags + dismissible install hint, SW
registration (guarded for `file://`). Findings closed: F7.
RCA: SVG-stroke icon generation produced blank glyphs (gradient stroke not rendering in the
headless SVG path); root cause — relied on inline-SVG `<line>` stroke rendering quirk.
Fixed by drawing the icon on a `<canvas>` and exporting via `toDataURL` (deterministic).
Prevention: icon generation now uses Canvas; verified by visual read of the output PNG.

### Phase 2 — Logging UX (2026-06-29) — ✅

Verification: `npx playwright test` → **18 passed**.

| Criterion | Proof | Result |
|-----------|-------|--------|
| Previous session shown as ghost placeholders + "last time" line | P2-PREVHINT | PASS |
| Per-exercise history lists prior sessions for selected exercise | P2-HISTORY | PASS |
| Progressive-overload set flagged (▲); non-beating set not flagged | P2-OVERLOAD | PASS |
| Rest timer auto-starts on add, +15 adjusts, countdown ticks, skip dismisses | P2-RESTTIMER | PASS |

Added: cross-log history helpers (`historyFor`/`lastSessionBest`/`isOverload`), set-logger
hints + history panel, ▲ overload indicator + toast, a fixed-position rest timer
(−15/+15/skip, WebAudio beep + `navigator.vibrate` fallback), `restDefaultSec` setting +
control, `inputmode` on numeric inputs. Findings closed: F4.
Note: rest timer's native haptics + lock-screen notification land in the Android wrapper (P5);
web fallback (vibrate + in-page beep) implemented now.
Caveat (cosmetic): the "last time" line shows "8×60" instead of "60×8" due to RTL bidi
reordering of digits around ×; values are correct. Will wrap in an LTR span in a later pass.
RCA: rest-timer eval initially failed — container lacked `data-testid="rest-timer"`.
Root cause: testid added to children but not the wrapper. Fix: add it. Prevention: eval
asserts the container visibility, which now guards the hook.

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

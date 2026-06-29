# CLAUDE.md — Fitness Training App

Authoritative working agreement for any agent (or human) working in `fitness-app/`.
Keep this file lean. Detailed guidance lives in `docs/` and is imported below.

## What this is

A personal, offline-first **fitness/training tracker**: 8-week strength + cardio
program, workout logging, nutrition, body metrics, charts, and (target state) an
Android wrapper that syncs with Samsung Health via **Health Connect**.

- Primary user: a single person, on a phone, mid-workout (one-handed, sweaty hands).
- Language/locale: **Hebrew, RTL**. All UI strings are Hebrew. Code/identifiers English.
- Privacy: data stays on-device. No server, no accounts, no analytics, no telemetry.

## Golden rules (read before editing)

1. **Docs-first, then code.** If behavior changes, update the relevant `docs/` file
   and `PROGRESS.md` in the same change.
2. **Follow the dev loop** for every feature/phase: review → research → implement →
   test → eval → RCA → fix → update docs. See `@docs/DEV_LOOP.md`.
3. **TDD.** Write/adjust the test or eval first, watch it fail, then implement.
   See `@docs/TDD.md`.
4. **Never break offline/privacy.** No network calls at runtime except optional,
   user-initiated, clearly-disclosed sync. No third-party trackers. Ever.
5. **Escape all user-derived text** before putting it in the DOM. Stored XSS is the
   #1 latent bug in the baseline. No raw template interpolation of user data.
6. **RTL-safe CSS only.** Use logical properties (`margin-inline-start`, `inset-inline-start`,
   `text-align:start`). Never physical `left/right` for layout. Never `row-reverse` to fake RTL.
7. **Data is sacred.** Workout history is irreplaceable. Never write a migration or
   import path that can silently corrupt or drop logs. Keep export/import working and
   tested. Back up state shape changes with a version bump + migration.
8. **Don't over-engineer.** Smallest change that satisfies the spec + tests. Prefer
   tiny zero/low-dependency libraries (see `@docs/TOOLING.md`).
9. **Commit in small, reviewable units** with descriptive messages. Run the full
   verification gate (below) before every commit.
10. **Be honest in `PROGRESS.md`.** If something is untested (e.g. Android runtime),
    say so explicitly. Never mark a phase done that isn't verified.

## Verification gate (run before EVERY commit)

```bash
npm run lint        # eslint + stylelint (RTL logical-property rule on)
npm run typecheck   # tsc --noEmit
npm test            # vitest unit tests
npm run test:e2e    # playwright golden-flow + eval suite (headless chromium)
npm run build       # vite build must succeed
```

A change is **not done** until all five pass (or, for the current phase, until the
phase's defined subset passes — never commit with a regression in a previously-green check).

> Note on environment: this dev container has **no Android SDK**. Android/Capacitor code
> must compile-check and be code-complete, but APK build + Health Connect runtime tests
> are a documented handoff (`@docs/TOOLING.md` → "Android handoff"). Do not claim Android
> runtime verification you cannot perform here.

## Project map

> Architecture is **buildless** per ADR-0002 (no Vite/Preact/TS). One self-contained file +
> assets + vendored libs, wrapped by Capacitor for Android.

- `index.html` — the entire app (UI, logic, state) in clear sections.
- `manifest.webmanifest`, `sw.js` — PWA (installable + offline).
- `assets/icons/` — app icons · `assets/exercises/` — bundled exercise photos (CC BY-SA) +
  `credits.json` · `assets/vendor/` — uPlot (MIT).
- `tests/` — Playwright e2e + `@eval` acceptance specs; `helpers.ts`.
- `capacitor.config.json`, `scripts/build-webdir.mjs` — Android wrapper scaffold (Phase 5).
- `docs/` — see imports below (incl. `ANDROID_HEALTH_CONNECT.md` handoff).
- `PROGRESS.md` — live phase/status tracker. Update it every working session.

## Imported docs

- @docs/CONOPS.md — who/what/why, operating context, constraints, non-goals.
- @docs/ARCHITECTURE.md — layers, data model, rendering, storage, decisions.
- @docs/ROADMAP.md — phases 0–5, scope and acceptance criteria.
- @docs/TOOLING.md — stack, commands, conventions, Android handoff.
- @docs/TESTING.md — test strategy, layers, Playwright setup.
- @docs/TDD.md — red-green-refactor adapted for agents.
- @docs/EVAL.md — golden flows, acceptance specs, self-evaluation.
- @docs/DEV_LOOP.md — the autonomous loop + RCA template.
- @docs/REVIEW_FINDINGS.md — audit findings being tracked to closure.
- @docs/adr/ — architecture decision records.

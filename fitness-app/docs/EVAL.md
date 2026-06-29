# EVAL — Acceptance evals & self-evaluation

An "eval" here is an **executable acceptance spec**: a Playwright scenario (tagged
`@eval`) that proves a CONOPS job-to-be-done works end to end, plus the bug-class guards.
Unit tests prove pieces are correct; evals prove the app *does the job*. A phase is done
only when its evals are green.

## How evals differ from unit tests

- Unit test: "`estimate1RM(100, 5)` returns ~112.5."
- Eval: "User repeats last session and logs a set in ≤3 taps with 0 mandatory keystrokes."

Evals are written from the user's intent, use stable `data-testid`/roles (not Hebrew
copy), run against the real build, and assert observable outcomes + state.

## Golden flows (must stay green every phase)

- **G1 Today:** open app → today's scheduled workout is shown (week/day label correct for
  `startDate`), without manual selection.
- **G2 Log a set:** select exercise → enter weight/reps → confirm → set appears in today's
  log and persists across reload.
- **G3 Notes survive:** type workout notes → perform another action (log set / tick
  exercise / toggle cardio) → notes are intact.
- **G4 Nutrition:** add a meal template → totals + macro bars update → persists on reload;
  adding twice does not silently double without intent.
- **G5 Body + chart:** log two body weights on different dates → weight chart draws,
  points spaced by date gap.
- **G6 Exercise guide:** search (case-insensitive) → matching exercise + illustration/icon
  shows.
- **G7 Backup round-trip:** export → reset → import the file → state matches pre-reset.

## Bug-class guards (regression evals)

- **E-XSS:** import a backup whose meal/exercise name is `<img src=x onerror=...>`; render
  every tab; assert no script executes and the text shows literally.
- **E-IDENTITY:** add 2 custom exercises, tick the 2nd done, delete the 1st; assert the
  2nd is still the one marked done (id-keyed, not index).
- **E-CLONE:** app boots and persists with `structuredClone` unavailable (shimmed off) —
  no white screen.
- **E-OFFLINE:** all golden flows pass with network routed to abort.
- **E-IMPORT-GUARD:** import a malformed backup (wrong types, `selectedDay: 999`) → app
  clamps/rejects safely, keeps a recoverable previous-state snapshot, no crash.

## Per-phase acceptance (summary; full criteria in ROADMAP.md)

- **P0:** G1–G7 + E-XSS, E-IDENTITY, E-CLONE; chart date-scaling unit test.
- **P1:** G1–G7 on the new build; PWA installable; domain unit coverage; IDB migration eval.
- **P2:** ≤3-tap repeat-set eval; rest-timer eval; per-exercise history + ▲ eval.
- **P3:** exercise-image-present (offline) eval; attribution-present eval.
- **P4:** 1RM/volume correctness; streak-forgiveness; PR-detection evals.
- **P5:** typecheck + config review (no device runtime here); handoff steps verified to exist.

## Agent self-evaluation (run at the end of each phase)

Produce a short **eval report** (append to `PROGRESS.md` under the phase):

1. **Criteria table:** each acceptance criterion → PASS/FAIL + the test id proving it.
2. **Evidence:** command output summary (counts), not raw dumps.
3. **Gaps/known issues:** anything unverifiable here (e.g. Android runtime) stated plainly.
4. **Decision:** phase = ✅ / ⚠️ / 🔄. Never ✅ with a failing criterion.

If any criterion FAILs → do **not** advance; run RCA (`DEV_LOOP.md`) and fix.

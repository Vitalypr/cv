# TESTING

## Philosophy

Test behavior the user depends on, and the bug classes that already bit us. Favor a few
high-signal tests per layer over exhaustive low-value ones. Every fixed bug gets a
regression test. Every phase has golden-flow E2E + evals (`EVAL.md`).

## Test pyramid

```
        e2e / eval (Playwright, real Chromium)   ← golden flows, XSS, regressions
      ───────────────────────────────────────
     component/integration (Vitest + jsdom)      ← render + interaction of a tab
   ─────────────────────────────────────────────
  unit (Vitest)                                   ← pure domain functions (most tests)
```

- **Unit (most):** pure functions in `src/lib` — nutrition totals, day/week-from-startDate,
  estimated 1RM, volume, progressive-overload decision, streak (with forgiveness), date
  scaling for charts, migrations, import coercion/clamping, `esc()`.
- **Component/integration:** a tab renders given a state; an interaction updates state and
  DOM without losing focus/notes. Uses Vitest + jsdom (or @testing-library/preact).
- **E2E/eval:** Playwright drives the built app through the CONOPS scenarios.

## Directory layout

```
tests/
  unit/        *.test.ts          (vitest)
  component/   *.test.tsx         (vitest + jsdom)
  e2e/         *.spec.ts          (playwright golden flows)
  eval/        *.eval.ts          (playwright, tagged @eval; acceptance specs)
  fixtures/    sample states, a malicious backup, a multi-week history
```

## Key invariants every suite must protect

1. **No data loss:** typing in any field then triggering a re-render preserves the value.
2. **No XSS:** user text with HTML/script renders inert everywhere it appears.
3. **Stable identity:** editing/deleting a custom exercise never re-maps another
   exercise's done-state or history.
4. **Offline:** flows pass with network disabled (route abort in Playwright).
5. **Export/import round-trip:** export → reset → import reproduces the same state.
6. **Migration safety:** old-version fixture loads without loss; `*_prev` snapshot exists
   after import.

## Playwright setup notes

- Headless Chromium from `/opt/pw-browsers` (see `TOOLING.md`). No `playwright install`.
- Run against `npm run preview` (the real build) for e2e; against dev for fast iteration.
- Seed state via `localStorage`/IndexedDB injection (`addInitScript`) from `tests/fixtures`.
- Tag acceptance specs `@eval` so `npm run eval` runs the acceptance subset.

## Definition of done for a change

- New/changed behavior covered by the lowest sufficient test layer.
- The relevant golden flow still green.
- Bug fixes include a regression test that fails on the old code.
- Coverage is not a target in itself; **uncovered domain logic is a smell**.

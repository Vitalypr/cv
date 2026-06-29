# TDD — Test-Driven Development (adapted for an autonomous agent)

TDD here is a guardrail against the two failure modes of agentic coding: (a) confidently
shipping code that doesn't do what was asked, and (b) regressing something that worked.

## The cycle

**RED → GREEN → REFACTOR**, per behavior:

1. **RED — write the failing test first.**
   - Translate the spec/acceptance criterion (`EVAL.md`, `ROADMAP.md`) into a concrete
     test at the lowest sufficient layer (prefer unit; use e2e for flows).
   - Run it. **Confirm it fails for the right reason** (asserts the real behavior, not a
     typo/import error). A test that passes immediately is suspect — strengthen it.

2. **GREEN — minimum code to pass.**
   - Implement the smallest change that makes the test pass. No speculative extras.
   - Run the single test, then the file, then the gate subset.

3. **REFACTOR — clean with tests green.**
   - Remove duplication, improve names, extract pure functions. Re-run tests after each
     step. Behavior must not change.

## Rules to avoid gaming tests

- **Tests assert observable behavior / spec, not the implementation.** Don't write a test
  that just mirrors the code you're about to write.
- **Never weaken a test to make it pass.** If a test is wrong, fix it deliberately and say
  so in the commit; don't silently relax assertions.
- **Don't special-case test inputs in product code.** No `if (input === fixtureValue)`.
- **Every bug fix starts with a failing regression test** reproducing the bug, then the
  fix turns it green.
- **Property/edge cases:** for domain math (1RM, totals, date scaling) test boundaries
  (empty, single point, equal min/max, large gaps, negative/garbage input).

## When to write tests first vs. after

- **First (always):** domain logic, data migrations, import coercion, bug fixes, anything
  with a numeric/spec answer, and each `EVAL.md` acceptance criterion.
- **Alongside:** component interaction tests for new UI (write the interaction assertion,
  then build the component to satisfy it).
- **Exploratory spikes** may precede tests, but the spike is thrown away and rebuilt
  test-first. Don't keep untested spike code.

## Web/E2E specifics

- For UI behaviors that are really about flow (≤3-tap logging, rest timer starts, notes
  survive re-render), the failing test is a **Playwright** spec. Write it against the
  intended selectors/`data-testid`s before building the UI.
- Use stable `data-testid` hooks for testability; don't assert on Hebrew copy that may
  change (assert on testids/roles, not translatable strings).

## Loop integration

TDD is the "test" + "implement" core of the dev loop (`DEV_LOOP.md`). RED happens in the
*research→implement* boundary; GREEN is *implement*; REFACTOR is part of *fix*. Evals
(`EVAL.md`) are the acceptance layer on top of TDD's developer tests.

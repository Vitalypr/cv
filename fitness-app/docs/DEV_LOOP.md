# DEV_LOOP — the autonomous development loop

Every phase (and every non-trivial feature within it) runs this loop. It is the operating
procedure for autonomous work on this repo.

```
review → research → implement → test → eval → RCA → fix → update-docs → commit
   ▲                                                   │
   └───────────────── (if eval/RCA finds issues) ──────┘
```

## Steps

1. **Review** — Re-read the relevant `docs/` (CONOPS, ARCHITECTURE, ROADMAP, REVIEW_FINDINGS)
   and the code you're about to touch. Restate the goal + acceptance criteria for this unit
   of work in `PROGRESS.md`. Identify risks and the smallest viable change.

2. **Research** — Only when there's a real unknown (API, library behavior, licensing,
   platform constraint). Verify with primary sources; capture decisions as an ADR
   (`docs/adr/`). Don't re-research what's already settled.

3. **Implement (TDD)** — RED: write the failing test/eval for the criterion. GREEN: minimum
   code to pass. Keep changes small and within the layer boundaries (`ARCHITECTURE.md`).

4. **Test** — Run unit + component tests. Then the verification gate subset relevant to the
   change. Fix until green. Add regression tests for anything you broke.

5. **Eval** — Run the golden flows + the phase's acceptance evals (`EVAL.md`). These are the
   user-facing proof. Produce the self-eval report.

6. **RCA (root cause analysis)** — For every failing test/eval or surprising behavior, do
   RCA before fixing. Use the template below. Fix the *cause*, not the symptom.

7. **Fix** — Apply the cause-level fix, re-run test + eval. Refactor with tests green.

8. **Update docs** — Update `PROGRESS.md` (status + eval report), and any `docs/` whose
   reality changed (ARCHITECTURE, TOOLING, REVIEW_FINDINGS closure, new ADR, CHANGELOG entry).

9. **Commit** — Run the full gate. Commit a small, reviewable unit with a clear message.
   Push when the phase (or a meaningful milestone) is complete.

## RCA template (record in PROGRESS.md or commit body for non-trivial bugs)

```
RCA: <one-line symptom>
- Trigger: what input/action surfaces it
- Root cause: the actual defect (mechanism), not "the test failed"
- Why tests missed it: gap in coverage/spec
- Fix: cause-level change
- Prevention: new test/eval/lint rule/doc rule added so it can't recur
- Blast radius: other places with the same pattern (grep), fixed or ticketed
```

## Guardrails

- **Don't advance a phase with a red acceptance criterion.** Loop back.
- **Don't silently weaken tests/evals** to get green (see `TDD.md`).
- **Don't skip RCA** for "small" bugs — recurring small bugs are an unfixed root cause.
- **Keep `PROGRESS.md` honest and current** — it is the source of truth for status while
  working autonomously. State caveats (e.g. Android runtime unverified) explicitly.
- **Stay in scope:** engineering/UX/architecture/data only — never alter training/nutrition
  *content* (CONOPS non-goal).
- **One concern per commit;** never bundle an unrelated refactor with a fix.

## Phase exit checklist

- [ ] All acceptance criteria for the phase PASS (eval report in `PROGRESS.md`)
- [ ] Golden flows G1–G7 green
- [ ] Bug-class guards green
- [ ] Verification gate green (lint, typecheck, unit, e2e, build)
- [ ] Docs updated; ADRs added for decisions; CHANGELOG entry
- [ ] Committed + pushed; `PROGRESS.md` reflects ✅/⚠️ with caveats

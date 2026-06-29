# REVIEW_FINDINGS — baseline audit, tracked to closure

From the multi-agent audit of the baseline `index.html`. Each finding has an ID, severity,
target phase, and status. Close a finding only when a regression test/eval proves it fixed.

Status: ⬜ open · 🔄 in progress · ✅ fixed (test id) · 🚫 won't-fix (rationale)

## High

| ID | Severity | Finding | Phase | Status |
|----|----------|---------|-------|--------|
| F1 | 🔴 | Stored XSS: user text interpolated without `esc()` in set-log (L308), nutrition (L399), body (L464) tables; also via import | P0 | ✅ E-XSS |
| F2 | 🔴 | Full-`innerHTML` re-render on every interaction discards unsaved notes + focus + scroll | P0 (mitigate) / P1 (eliminate) | 🔄 mitigated (G3: notes persist on input; check toggles in-place) — full fix P1 |
| F3 | 🔴 | `done` flags keyed by array index → corrupt mapping on custom edit/delete; flags never displayed | P0 | ✅ E-IDENTITY (id-keyed + progress shown) |
| F4 | 🔴 | No per-exercise history; set logs are write-only after the day rolls over | P2 | ⬜ |

## Medium

| ID | Severity | Finding | Phase | Status |
|----|----------|---------|-------|--------|
| F5 | 🟡 | `startDate`/`dayNames` dead code; app never computes today's workout/week | P0 | ✅ G1 (auto-select today + "שבוע N · יום M") |
| F6 | 🟡 | Silent overwrite of same-date body entry; duplicate meal-day stacking; one-way complete | P0 | ✅ overwrite/dup confirms + complete toggle |
| F7 | 🟡 | No PWA manifest/SW though UI promises offline/add-to-home-screen | P1 | ⬜ |
| F8 | 🟡 | No ARIA (tabs/checkbox/labels/live-region); re-render destroys focus for AT | P0 (basics) / P1 | 🔄 basics done (tablist/tab, checkbox role, toast live region, icon aria-labels); label-for + focus restore → P1 |
| F9 | 🟡 | Shallow import validation; bad-but-shaped file can corrupt persisted state, no undo | P0 | ✅ E-IMPORT-GUARD (type coercion, clamp, `_prev` snapshot, size/onerror) |

## Low

| ID | Severity | Finding | Phase | Status |
|----|----------|---------|-------|--------|
| F10 | 🟢 | Charts plot by index not date → distorted trends; no axis date labels | P0 | ✅ G5 (date-scaled x + first/last date labels + debug hook) |
| F11 | 🟢 | Exercise search case-sensitive substring; no muscle index for built-ins | P0 | ✅ G6 (case-insensitive) |
| F12 | 🟢 | `structuredClone` used unconditionally → blank app on old WebView/Safari <15.4 | P0 | ✅ E-CLONE (JSON clone) |
| F13 | 🟢 | Charts don't redraw on resize/orientation change | P0/P1 | ✅ resize/orientation listener |
| F14 | 🟢 | Numeric inputs lack min/max/range validation; body metrics stored as raw strings | P0→P1 | ⬜ deferred to P1 (typed model) |
| F15 | 🟢 | `render()` eagerly builds all six tabs + rebinds handlers each change (wasteful) | P1 | ⬜ |

## Notes

- F2 is mitigated in P0 (persist notes on input/blur; targeted DOM update for hot paths)
  and fully eliminated in P1 by moving to Preact targeted rendering.
- Keep the line numbers as historical references to the *baseline*; they will not match
  post-migration code.

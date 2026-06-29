# ADR-0002 — Stay buildless (revise ADR-0001 stack)

- Status: Accepted (supersedes the build/rendering parts of ADR-0001)
- Date: 2026-06-29
- Context: ADR-0001 proposed Vite + TypeScript + Preact + signals + IndexedDB. Phase 0 is
  done and the app is hardened and fully eval-covered. Re-evaluating before Phase 1 with the
  overriding goal of delivering a complete, working, tested app autonomously.

## Decision

Keep the app **buildless and self-contained**: a single `index.html` (organized into clear
internal sections) plus an `assets/` folder for bundled media and PWA files. Add libraries by
**vendoring/inlining** (e.g. uPlot) rather than via a bundler. Retain `localStorage`
(hardened in Phase 0) as the store; adopt IndexedDB only if/when user-supplied media or large
data actually requires it. Package for Android by pointing **Capacitor's `webDir` at the
`fitness-app/` folder** — no Vite needed.

## Why (vs. the ADR-0001 build path)

- **Capacitor doesn't need a bundler.** It wraps a static web directory; a single HTML file
  is a perfectly valid `webDir`. The Android/Health Connect goal does not force a build.
- **Lower delivery risk.** A full Preact/TS rewrite of a 600-line app, done autonomously
  without check-ins, risks leaving the app half-broken. The proven render functions stay.
- **Phase 0 already fixed the motivating bugs.** Lost notes/focus and identity/corruption
  (the main reasons to adopt Preact + TS) are resolved with targeted in-place updates,
  stable ids, runtime guards, and an eval suite — so the marginal benefit of Preact + TS is
  now small relative to its cost/risk.
- **Storage:** the only strong reason for IndexedDB was image Blobs; bundled exercise images
  are static files referenced by path (no Blob storage needed). `localStorage` (5MB) is
  sufficient for the JSON logs. Revisit IDB only for user-uploaded media.
- **Simplicity preserved.** The app can still be opened as one file and remains trivial to
  back up and reason about.

## Consequences

- No TypeScript type-checking; we rely on the eval suite + runtime guards for safety.
- The single file grows; mitigate with clear section banners and keeping logic in small
  named functions. If it becomes unwieldy, revisit a build then (ADR-0001 remains the
  documented path for that future).
- Vendored libs must be committed (offline requirement) and their licenses recorded.
- `package.json` keeps only dev tooling (Playwright) + later Capacitor; no app runtime deps.

## Re-sequenced roadmap (see ROADMAP.md)

- P1 PWA (manifest + service worker + iOS meta + install hint) — make offline/installable real.
- P2 Logging UX + per-exercise history (F4) + rest timer + previous-session hints + overload.
- P3 Exercise media (bundled static images, lazy-load, attribution).
- P4 Stats (vendored uPlot interactive per-exercise charts; streaks; PRs).
- P5 Capacitor Android + Health Connect (code-complete + handoff; no SDK in this container).

Storage stays `localStorage` (hardened). IndexedDB deferred/optional and documented.

# ADR-0001 — Stack & architecture for the rebuild

- Status: Accepted
- Date: 2026-06-29
- Context: baseline is a single-file vanilla-JS app; owner wants exercise media, richer
  stats, better logging interactivity, and Samsung Health sync, and chose to **wrap as an
  Android app**.

## Decision

Adopt a **Vite + TypeScript + Preact(+signals)** web app, charts via **uPlot**, storage via
**IndexedDB (idb-keyval)**, packaged for Android with **Capacitor** (Health Connect for
Samsung Health). Tests: **Vitest** + **Playwright**.

## Rationale

- **Android wrap is mandatory** for Samsung Health: no browser can reach Samsung Health /
  Health Connect / Apple Health directly; Google Fit REST is shutting down. Health Connect
  (which aggregates Samsung Health) is reachable only from a native Android app → Capacitor.
- Once Capacitor is in, a **build step is already required**, so Vite + TypeScript cost
  nothing extra and buy type-safety against the data-corruption bug class.
- **Preact + signals** (~5KB) replaces the full-`innerHTML` re-render (root cause of lost
  notes/focus) with targeted updates, while staying tiny and framework-light.
- **uPlot** (~18KB, zero-dep) is time-series-native and fixes the index-vs-date bug with
  far less code than maintaining hand-rolled canvas.
- **IndexedDB** removes the 5MB localStorage cap and stores image Blobs for Phase 3.
- `vite-plugin-singlefile` preserves an offline single-HTML web artifact for non-Android use.

## Alternatives considered

- **Stay buildless (Preact via CDN/import-maps):** preserves "one file, no server," but the
  Android requirement already forces tooling, so the simplicity benefit evaporates.
- **lit-html instead of Preact:** viable, standards-based; Preact chosen for a more familiar
  component/state model and signals maturity. (Revisit if WebComponents become desirable.)
- **Chart.js / ApexCharts / Frappe / Observable Plot:** heavier or (Observable) needs D3;
  uPlot best fits size + offline + time-series.
- **Aggregator API (Terra/Spike/Rook) for health data:** great coverage but SaaS + per-user
  cost + a server; conflicts with the no-server/private CONOPS. Capacitor+Health Connect
  keeps it on-device and free.
- **Keep localStorage:** insufficient for images and risks the 5MB cap.

## Consequences

- New toolchain (Node/Vite/TS/Capacitor) to maintain; mitigated by `TOOLING.md`.
- Android runtime can't be tested in the current container (no SDK) → Phase 5 is
  code-complete + handoff (`ROADMAP.md`, `TOOLING.md`).
- CC BY-SA exercise media requires attribution (Phase 3).

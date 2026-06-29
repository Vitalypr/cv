# ARCHITECTURE

## 0. Status

- **Baseline (committed):** single `index.html`, vanilla JS IIFE, full-`innerHTML`
  re-render per interaction, `localStorage` persistence, hand-rolled canvas charts,
  inline SVG exercise icons.
- **Target:** modular ESM under `src/`, reactive targeted DOM updates, IndexedDB
  persistence, `uPlot` charts, bundled exercise media, Capacitor Android wrapper with
  Health Connect.

The migration is phased (see `ROADMAP.md`). Phase 0 hardens the baseline in place;
Phase 1 introduces the build + module split.

## 1. Layered model

```
┌──────────────────────────────────────────────────────────┐
│ View layer        components/ — render functions/Preact    │
│                   targeted updates, no full-tree rebuilds  │
├──────────────────────────────────────────────────────────┤
│ State layer       state/ — single store + signals,         │
│                   selectors, actions, schema + migrations  │
├──────────────────────────────────────────────────────────┤
│ Domain layer      lib/ — pure functions: program/day calc, │
│                   nutrition math, 1RM/volume, progressive   │
│                   overload, streaks, date utils            │
├──────────────────────────────────────────────────────────┤
│ Persistence       storage/ — IndexedDB (idb-keyval),       │
│                   export/import, migration runner          │
├──────────────────────────────────────────────────────────┤
│ Platform bridge   platform/ — Capacitor plugins:           │
│                   Haptics, LocalNotifications, Health Connect│
│                   (all feature-detected; web fallbacks)    │
└──────────────────────────────────────────────────────────┘
```

**Dependency rule:** view → state → domain → persistence/platform. Domain layer is
pure (no DOM, no storage, no Capacitor) so it is trivially unit-testable.

## 2. Chosen stack (see ADR-0001)

| Concern | Choice | Why |
|---|---|---|
| Build | **Vite** + `vite-plugin-singlefile` (web target) | Modular dev; can still emit one offline HTML; required anyway for Capacitor |
| Language | **TypeScript** | Type-safety for the data model is the cheapest defense against the corruption-class bugs in the baseline |
| Rendering | **Preact + @preact/signals** | ~5KB, component model, targeted DOM updates fix focus/notes loss; signals align with TC39 Signals |
| Charts | **uPlot** | ~18KB gz, zero-dep, time-series-native, free zoom/cursor; fixes index-vs-date bug |
| Storage | **IndexedDB** via `idb-keyval` | Async, holds images/Blobs, no 5MB cap; near drop-in for the current get/set |
| Exercise media | **Free Exercise DB** (bundled) / **wger API** | Open, CC BY-SA static images; offline-bundle-able |
| Mobile shell | **Capacitor** | Wraps the web app for Android; only viable path to Samsung Health (via Health Connect) |
| Unit tests | **Vitest** | Vite-native, fast |
| E2E/eval | **Playwright** | Real Chromium, drives the golden flows |

## 3. Data model (single source of truth)

One JSON-serializable `AppState` object, versioned. Baseline shape (to be typed and
extended):

```ts
interface AppState {
  meta: { createdAt: string; version: number };
  selectedDay: number;            // manual override of today's computed day
  settings: {
    calorieTarget: number; proteinTarget: number; carbTarget: number;
    fatTarget: number; waterTarget: number;
    units: 'kg' | 'lb';           // NEW: canonical storage is kg; format on render
    restDefaultSec: number;       // NEW: global rest-timer default
    haptics: 'enhanced' | 'minimal' | 'off'; // NEW
  };
  logs: {
    workouts: Record<string, WorkoutLog>;   // key: `${isoDate}_${dayId}`
    nutrition: Record<string, NutritionLog>; // key: isoDate
    body: Record<string, BodyLog>;           // key: isoDate
    performance: PerformanceLog;
  };
  customExercises: Record<string, Exercise[]>; // key: dayId
}
```

### Identity rules (fixes baseline corruption bugs)

- Every exercise (base + custom) MUST have a **stable `id`**. Base exercises get a
  deterministic id (`${dayId}:${slug(name)}`); customs already carry `uid()`.
- `WorkoutLog.done` is keyed by **exercise id**, never array index.
- Logged sets store the exercise **id** (plus a denormalized name for display), so
  per-exercise history survives renames/reorders.

### Migrations

- `migrations/` holds ordered `vN → vN+1` functions. The migration runner runs them in
  sequence on load. Each is pure, tested, and additive/non-destructive.
- Before any import overwrites live state, snapshot the previous state to a secondary key
  (`*_prev`) for one-step recovery.

## 4. Rendering principles

- **No full-subtree `innerHTML` rebuilds on interaction.** Mutate only what changed
  (Preact diff). This is the root cause of lost notes, lost focus, scroll reset.
- Inputs (notes, set fields) are controlled/persisted on `input`/`blur` (debounced) so a
  re-render can never discard them.
- The exercise search re-renders only its results list, never its own input.
- After any necessary structural re-render, restore focus to a stable anchor.

## 5. Accessibility baseline

- Tab bar = `role="tablist"` / `tab` / `tabpanel` + `aria-selected` + `aria-controls`.
- Exercise "done" control = `role="checkbox"` + `aria-checked` + name in `aria-label`.
- Every input associated to a `<label for>`.
- Toast = `role="status"` `aria-live="polite"`.
- Charts carry an `aria-label` summary (current + trend) or an offscreen data table.

## 6. Security model

- Single-user local app, but **import is an untrusted input**: a malicious/corrupt backup
  can inject script or bad shapes. Therefore: escape all user text on render, and
  type-validate/coerce on import (clamp `selectedDay`, force arrays/objects, `Number()`
  numerics). See `REVIEW_FINDINGS.md` #1 and #robustness.
- No `eval`, no `innerHTML` of unescaped user content, no remote script.

## 7. Offline & platform

- Web target: PWA manifest + service worker (precache shell, cache-first assets) so
  "add to home screen" is real. iOS meta tags for completeness.
- Android target: Capacitor; Health Connect via community plugin; Haptics +
  LocalNotifications for the rest timer. All platform calls feature-detected with web
  fallbacks (`navigator.vibrate`, in-page timer, download-based export).

## 8. Health Connect data flow (Phase 5, code-complete + handoff)

```
Samsung Health ─┐
Other apps     ─┼─▶ Android Health Connect (on-device) ◀──▶ Capacitor plugin ◀──▶ app
                ┘     read: steps, weight, sleep, heartRate
                      write: completed workout sessions
```
- Read steps/weight/sleep to pre-fill body metrics. Write completed workouts back.
- Pure-web cannot reach Health Connect → this layer only runs inside the Android build.

# TOOLING

## Stack & versions

- Node 22, npm 10 (this container). Java 21 present.
- Vite + TypeScript, Preact + @preact/signals, uPlot, idb-keyval.
- Vitest (unit), Playwright (e2e/eval), ESLint + Stylelint.
- Capacitor (Android shell) — Phase 5.

## Commands (canonical — keep `package.json` scripts matching this)

```bash
npm install            # deps
npm run dev            # vite dev server (http://localhost:5173)
npm run build          # production build -> dist/ (single HTML for web target)
npm run preview        # serve the build locally

npm run lint           # eslint + stylelint (incl. RTL logical-property rule)
npm run typecheck      # tsc --noEmit
npm test               # vitest run (unit)
npm run test:watch     # vitest watch
npm run test:e2e       # playwright test (headless chromium) — golden flows + evals
npm run eval           # alias of test:e2e tagged @eval

# Capacitor (Phase 5, requires Android SDK — NOT available in this container)
npm run cap:sync       # npx cap sync android
npm run cap:open       # npx cap open android
```

## Verification gate

Before every commit run the gate from `CLAUDE.md`: lint, typecheck, test, test:e2e,
build. Never commit a regression in a previously-green check.

## Conventions

- **TypeScript strict.** No `any` in domain/state layers. Model `AppState` precisely.
- **Pure domain layer** (`src/lib`): no DOM, storage, or Capacitor imports → unit-tested
  in isolation.
- **RTL CSS:** logical properties only. Stylelint rule `stylelint-use-logical` (or a
  custom check) forbids physical `left/right/margin-left/...` in layout. Mirror only
  directional icons under `[dir=rtl]`.
- **i18n/format:** `Intl.NumberFormat` (units) + `Intl.PluralRules` for numbers/plurals;
  never string-concatenate counts. Store weights in **kg**, format per `settings.units`.
- **Escaping:** a single `esc()`/safe-template helper; no raw user text in markup. With
  Preact, prefer text nodes (auto-escaped) over `dangerouslySetInnerHTML`.
- **IDs:** stable exercise ids everywhere (`ARCHITECTURE.md §3`).
- **Commits:** small, imperative subject, body explains why. Co-author + session trailer
  per repo policy. One logical change per commit.
- **Files:** kebab-case filenames; one component/module per file; named exports.

## Browser testing in this container

- Chromium is at `/opt/pw-browsers`. `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers` and
  `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` are set. **Do not run `playwright install`.**
- If a pinned Playwright wants a different Chromium, launch with
  `executablePath: '/opt/pw-browsers/chromium'`.
- Run E2E headless. Use Playwright to drive real flows against `npm run dev`/`preview`.

## Android handoff (Phase 5 — do on a machine with Android SDK)

This container has **no Android SDK/Gradle**, so APK build + Health Connect runtime are
not possible here. Deliverables here are code-complete + reviewed. On your machine:

```bash
# Prereqs: Android Studio + SDK (API 34+), JDK 17+, a device/emulator with the
# Health Connect app installed (Android 9+; preinstalled on Android 14+).

cd fitness-app
npm install
npm run build
npx cap add android          # first time only
npm run cap:sync
npm run cap:open             # opens Android Studio
# In Android Studio: let Gradle sync, then Run on device/emulator.

# Health Connect: grant the requested data permissions when prompted.
# Verify: steps/weight read into body metrics; completed workout written back
# (visible in Health Connect > app data, and surfaced in Samsung Health if installed).
```

Manifest/permission requirements (declared in the Android project, see Phase 5 code):
- Health Connect permissions for the data types read/written (steps, weight, sleep,
  exercise session) + the privacy-policy intent filter Health Connect requires.
- `minSdk` per the chosen Health Connect plugin.

Document any runtime result back into `PROGRESS.md` (flip Phase 5 from ⚠️ to ✅ only after
on-device verification).

## Proxy / network notes

Outbound HTTPS goes through the agent proxy (CA at `/root/.ccr/ca-bundle.crt`); Java trust
store is preconfigured. npm registry is reachable. Never disable TLS verification. If a
fetch fails with 403/405/407, see `/root/.ccr/README.md`.

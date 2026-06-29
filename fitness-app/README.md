# תוכנית חיטוב וכוח — Fitness Training App

An offline-first, Hebrew (RTL), phone-first strength + cardio training tracker. Single
self-contained `index.html` (no server, no accounts, data stays on the device), installable
as a PWA and wrappable as an Android app for Samsung Health sync via Health Connect.

## Features

- **Daily workout** with today's scheduled day auto-selected ("שבוע N · יום M"), exercise
  checklist with progress, and per-day notes that never get lost.
- **Fast set logging** with previous-session ghost hints, progressive-overload ▲ flags, and
  per-exercise history.
- **Rest timer** (auto-start, ±15s, skip; beep + vibrate on Android).
- **Nutrition** quick-add + meal templates with live calorie/protein targets.
- **Tracking & stats**: body metrics, honest date-scaled charts, an interactive estimated-1RM
  progression chart (uPlot), streaks (with forgiveness), and personal records.
- **Exercise guide** with real photos (CC BY-SA) and Hebrew form cues; SVG fallback offline.
- **Backup/restore** (JSON), and **Samsung Health / Health Connect** sync in the Android app.

## Run it

Just open `index.html` in a browser, or serve the folder:

```bash
npm install
npm run serve     # http://127.0.0.1:5180/index.html
```

## Test

```bash
npm run test:e2e  # Playwright golden-flow + @eval acceptance suite (headless Chromium)
```

## Android (Samsung Health)

Code-complete; build on a machine with the Android SDK — see
[`docs/ANDROID_HEALTH_CONNECT.md`](docs/ANDROID_HEALTH_CONNECT.md).

## Docs

Architecture, decisions, testing and the development loop live in [`docs/`](docs/) and
[`CLAUDE.md`](CLAUDE.md). Live status: [`PROGRESS.md`](PROGRESS.md). Credits: exercise photos
from [Free Exercise DB](https://github.com/yuhonas/free-exercise-db) (CC BY-SA); charts by
[uPlot](https://github.com/leeoniya/uPlot) (MIT).

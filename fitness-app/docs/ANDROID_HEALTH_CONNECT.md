# Android wrapper + Samsung Health (Health Connect)

The Android project is **committed** (`fitness-app/android/`, Capacitor 7) with the
`@devmaxime/capacitor-health-connect` plugin (read-only: **Steps**, **Weight** — Health
Connect aggregates Samsung Health data) and the manifest wired for Health Connect. There are
two ways to get an installable APK.

## Easiest: download the CI-built APK (no Android Studio)

1. Push to the `claude/training-app-review-ykzrgo` branch (already done) — the
   **Build Android APK** GitHub Actions workflow (`.github/workflows/android.yml`) runs, or
   trigger it manually from the repo's **Actions** tab → *Build Android APK* → *Run workflow*.
2. Open the finished run → **Artifacts** → download **fitness-app-debug-apk**, unzip to get
   `app-debug.apk`.
3. On your Samsung phone: copy the APK over, enable **Install unknown apps** for your file
   manager/browser, tap the APK to install.
4. Open the app → Settings (גיבוי) → **חיבור ל‑Samsung Health** → **התחבר**, grant the
   Health Connect permissions, then **משוך צעדים/משקל** to import.

This APK is debug-signed (fine for personal sideloading). For Play Store distribution you'd
add a release signing config.

## Alternative: build locally (Android Studio)

Requires Android Studio + SDK (API 35), JDK 21, a device/emulator with Health Connect.

## Why this shape

- No browser API can read Samsung Health. **Health Connect** is the on-device Android hub
  that already aggregates Samsung Health; you reach it from a **native Android app**.
- Capacitor wraps our static web app into that native app and exposes Health Connect through
  a plugin. On the plain web the bridge no-ops and the Settings card says "Android app only".

## Prerequisites (on your machine)

- Android Studio + Android SDK (API 34+), JDK 17+.
- A device/emulator running Android with the **Health Connect** app available
  (pre-installed on Android 14+; installable from Play on 9–13).
- Samsung Health installed and set to share data with Health Connect (for Samsung data).

```bash
cd fitness-app
npm install
npm run cap:sync       # build:web + cap sync android
npm run cap:open       # opens Android Studio; let Gradle sync, then Run on device
```

The project is already configured:
- `capacitor.config.json` → `appId: com.vitaly.fitness`, `webDir: dist-web`.
- Plugin `@devmaxime/capacitor-health-connect` installed (Capacitor 7).
- `android/variables.gradle` → `minSdkVersion = 26` (Health Connect requirement).

### What's already wired in the manifest

`android/app/src/main/AndroidManifest.xml` already declares:
- `READ_STEPS` + `READ_WEIGHT` health permissions,
- the `<queries>` entry for `com.google.android.apps.healthdata`,
- the permissions-rationale intent filters on `MainActivity` (Android 13- and 14+).

### The web bridge

`index.html` (`healthAction()` + `mergeHealthIn()`) calls the plugin via
`Capacitor.Plugins.HealthConnect`: `checkAvailability()`, `requestPermissions({read:['Steps','Weight'], write:[]})`,
`readRecords({type, start, end})`. The plugin is **read-only**, so "send workouts" is shown
as not-supported; to write exercise sessions, add a write-capable plugin (e.g.
`@flomentumsolutions/capacitor-health-extended`) and extend `healthAction('out')`.

## Run + verify on device

In the app → Settings (גיבוי) → "חיבור ל‑Samsung Health":
1. **התחבר** → grant the Health Connect permissions prompt.
2. **משוך צעדים/משקל** → recent steps + latest weight populate the body-metrics log
   (verify on the Tracking tab + charts).

For Samsung data: install Samsung Health and enable its Health Connect sharing, so its steps
and weight appear in Health Connect (and therefore in this app).

## Update status

After on-device verification, flip Phase 5 in `PROGRESS.md` from ⚠️ to ✅ and note what was
confirmed (which data types read/wrote, device + Android version).

## Notes / caveats

- **Samsung partner program:** writing into Samsung Health's own database (vs. Health
  Connect) needs Samsung's partner approval, which was paused for new apps in late 2025.
  Going through Health Connect avoids that for the common data types.
- **iOS:** not covered here. Apple Health is native-iOS-only; a separate Capacitor iOS build
  + HealthKit plugin would be required.
- **Privacy:** all sync is user-initiated and opt-in; request the minimum data types.

# Android wrapper + Samsung Health (Health Connect) — handoff

This phase is **code-complete in the web app** (a feature-detected `Health` bridge + a
Settings card) and **scaffolded for Capacitor** (`capacitor.config.json`, `build:web`,
scripts). The Android project itself must be generated and built on a machine with the
**Android SDK**, which this dev container does not have. Follow the steps below there.

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

## 1. Install Capacitor + Android platform

```bash
cd fitness-app
npm install            # existing dev deps (Playwright)
npm install @capacitor/core @capacitor/cli @capacitor/android
npm run build:web      # assembles dist-web/ (the webDir Capacitor copies)
npx cap init "אימונים" com.vitaly.fitness --web-dir dist-web   # config already provided; this is a no-op if present
npx cap add android
npm run cap:sync       # build:web + cap sync
```

`capacitor.config.json` is already set (`appId: com.vitaly.fitness`, `webDir: dist-web`).

## 2. Install a Health Connect plugin

Pick a maintained Capacitor Health Connect plugin, e.g. `capacitor-health-connect`
(community) — verify the latest name/version on npm before installing:

```bash
npm install capacitor-health-connect
npm run cap:sync
```

The web bridge (`Health` in `index.html`) calls a plugin exposed as
`Capacitor.Plugins.HealthConnect` (or `.Health`) with these methods:

- `requestHealthPermissions({ read:['Steps','Weight','SleepSession'], write:['ExerciseSession'] })`
- `readRecords({ type:'Steps'|'Weight', startDate, endDate })`
- `insertRecords({ records:[{ type:'ExerciseSession', startDate, endDate, exerciseType, title }] })`

If your chosen plugin uses different method/field names, adapt the thin adapter in
`index.html` (`healthAction()` + `mergeHealthIn()`) — they are isolated and commented for
exactly this. Keep the read/merge field-path fallbacks defensive.

## 3. AndroidManifest — permissions + privacy policy

Health Connect requires declaring each data type permission and a privacy-policy intent.
In `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.health.READ_STEPS"/>
<uses-permission android:name="android.permission.health.READ_WEIGHT"/>
<uses-permission android:name="android.permission.health.READ_SLEEP"/>
<uses-permission android:name="android.permission.health.WRITE_EXERCISE"/>

<!-- Health Connect permission-rationale activity (required) -->
<activity android:name=".PermissionsRationaleActivity" android:exported="true">
  <intent-filter>
    <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"/>
  </intent-filter>
</activity>
<!-- For Android 14+, also handle the new ViewPermissionUsageActivity alias per Health Connect docs. -->
```

Set `minSdkVersion` to what the plugin requires (Health Connect SDK supports API 26+; the
Health Connect app needs API 28+) in `android/variables.gradle`.

## 4. Run + verify on device

```bash
npm run cap:open      # opens Android Studio; let Gradle sync, then Run
```

In the app → Settings (גיבוי) → "חיבור ל‑Samsung Health":
1. **התחבר** → grant the Health Connect permissions prompt.
2. **משוך צעדים/משקל** → recent steps + latest weight populate the body-metrics log
   (verify on the Tracking tab + charts).
3. **שלח אימונים** → completed workouts are written as exercise sessions (verify in the
   Health Connect app → app data, and in Samsung Health if installed).

## 5. Update status

After on-device verification, flip Phase 5 in `PROGRESS.md` from ⚠️ to ✅ and note what was
confirmed (which data types read/wrote, device + Android version).

## Notes / caveats

- **Samsung partner program:** writing into Samsung Health's own database (vs. Health
  Connect) needs Samsung's partner approval, which was paused for new apps in late 2025.
  Going through Health Connect avoids that for the common data types.
- **iOS:** not covered here. Apple Health is native-iOS-only; a separate Capacitor iOS build
  + HealthKit plugin would be required.
- **Privacy:** all sync is user-initiated and opt-in; request the minimum data types.

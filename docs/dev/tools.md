# Toolchain

Versions live ONLY in `android/gradle/libs.versions.toml`. Verified against Maven metadata 2026-08-05.

| Tool | Version | Why this one |
|---|---|---|
| JDK | 21 (container) | AGP 8.13 supports 17–21 |
| Gradle | 8.14.3 (system install, `/opt/gradle`) | preinstalled → no wrapper download; compatible with AGP 8.13 |
| AGP | 8.13.2 | newest 8.x; AGP 9.x requires Gradle 9 — deliberate hold-back, see gotchas |
| Kotlin | 2.2.21 | mature 2.2 line, K2, bundled Compose compiler; 2.4.x exists but adds no needed feature vs. churn risk |
| KSP | 2.2.21-2.0.5 | must match Kotlin version prefix exactly |
| Compose BOM | 2026.04.01 | Compose 1.11 stable; compileSdk 36 |
| compile/target SDK | 36 | current stable platform (installed in `/opt/android-sdk`) |
| minSdk | 26 | geofencing + notification channels baseline; S23 Ultra ships far above |
| Room | 2.8.4 | stable 2.x line; Room 3.0 is a KMP-focused major — adopt only with a migration test pass |
| Hilt | 2.57.2 | KSP2-compatible; **2.58+ requires AGP 9** — bump only together with AGP/Gradle 9 migration |
| Robolectric | 4.16.1 | SDK 36 shadows |
| Roborazzi | 1.70.0 | JVM screenshot tests (no emulator in this environment) |
| Play services location | (catalog) | GeofencingClient |

## Environment setup (fresh container)

```bash
bash scratchpad/sdk-install.sh   # or: install cmdline-tools → sdkmanager "platforms;android-36" "build-tools;36.0.0"
export ANDROID_HOME=/opt/android-sdk
cd android && gradle :domain:test
```

## Screenshot workflow (Roborazzi)

- Record: `gradle :app:testDebugUnitTest -Proborazzi.test.record=true` → PNGs in `app/src/test/snapshots/`
- Verify: `-Proborazzi.test.verify=true` → diff images on failure in `build/outputs/roborazzi/`
- Screenshots are committed; a red verify means either a regression (fix code) or an approved design change (re-record + commit together with the change, never separately).

## Version bump procedure

1. Change `libs.versions.toml` only. 2. `gradle :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. 3. Verify screenshots. 4. Note the bump + reason in the commit message.

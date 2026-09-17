# Dev Environment Setup

## Recommended approach

**Develop and initially test on a Windows-hosted Android emulator, move to your real phone (OnePlus 15) once the app runs in the emulator.**

Why emulator-first:
1. Fast iteration — no cable, no "install APK to phone" round trip for every change.
2. Debugger attaches directly; you can inspect `RideSessionService`/Ferrostar state and logcat live.
3. Location and route responses can be faked (mock location via `adb emu geo fix` or the AVD's Extended Controls) so you can exercise the dial/maneuver logic without a live GPS fix or a real drive.

Why you still need the real phone before calling a phase done:
1. Bluetooth behavior (BLE link to the future ESP32 puck, background power management) is emulator-unreliable — BT support in AVDs is limited/inconsistent.
2. OnePlus's OxygenOS has its own background-restriction quirks that only show up on real hardware.
3. The actual exit criteria for each phase in `MotoNav_REBUILD_PLAN_OSM.md` (real ride, screen-lock survival, aeroplane-mode routing) can only be measured on the bike.

So: build and unit-test the pure logic (`nav/`) in the emulator, then do all Bluetooth/power/OEM-behavior/real-ride validation on your OnePlus 15.

## Install steps (Windows)

**Already done on the primary dev machine** (2026-09-17) — this section is for setting up a second machine, or reference if something needs reinstalling.

1. **Install Android Studio**: `winget install --id Google.AndroidStudio`.
   - Bundles the Android SDK, AVD Manager, and Gradle tooling. Run it once after install so its first-run wizard installs the SDK, platform-tools, and emulator.
   - **Gotcha:** Android Studio's bundled JBR (JetBrains Runtime) tracks the newest JDK (JDK 25 as of this writing) — too new for AGP 8.7.0, which fails with a cryptic bare-version-number error. Install a separate **Temurin 21 JDK** (`winget install --id EclipseAdoptium.Temurin.21.JDK`) and point `JAVA_HOME` at it for CLI Gradle builds. Android Studio itself is unaffected (it always uses its own bundled JBR for the IDE).
2. **Set `ANDROID_HOME`/`ANDROID_SDK_ROOT`** to the SDK path (`%LOCALAPPDATA%\Android\Sdk`) and **`JAVA_HOME`** to the Temurin 21 install (`setx`, then open a new shell).
3. **Open this repo** (`C:\Daifuku RAG Dev\Active\MotoNav`) in Android Studio: File → Open → select the folder.
   - Let it sync Gradle on first open (downloads dependencies — needs internet).
   - CLI equivalent: `./gradlew assembleDebug` (uses the repo's pinned Gradle wrapper, 8.10.2).
4. **Create an emulator:**
   - AVD Manager → Create Device → pick a Pixel profile → any system image is fine (Play Services are not required — the app no longer depends on Google Maps/Waze). Android 14 or 15 recommended (match roughly what your OnePlus 15 runs).
   - Current dev AVD: `MotoNav_Pixel7_API35` (Pixel 7 profile, API 35).
5. **Run the app** (green Run button, or Shift+F10) targeting the emulator, or `adb install -r app/build/outputs/apk/debug/app-debug.apk` after a CLI build.

## Testing on your OnePlus 15

1. Enable Developer Options (Settings → About Phone → tap Build Number 7 times) and USB Debugging.
2. Connect via USB, accept the debugging prompt on the phone.
3. Your device appears in Android Studio's device dropdown next to the emulator — select it and Run to deploy directly.
4. For untethered testing (riding), build a debug APK and install it once, then just observe logs/behavior without staying plugged in; use `adb logcat` over USB only when you need live debug output.

## Kotlin/Compose gotcha

Kotlin 2.0+ decoupled the Compose compiler from the Kotlin Gradle plugin. `org.jetbrains.kotlin.plugin.compose` must be applied alongside `org.jetbrains.kotlin.android` (both root and app `build.gradle.kts`) or the build fails at configuration with "Compose Compiler Gradle plugin is required." `composeOptions.kotlinCompilerExtensionVersion` is obsolete under this setup — don't add it back.

## Routing/geocoding backends

The app talks to remote services that are **user-configurable, not hardcoded** (see `docs/MotoNav_REBUILD_PLAN_OSM.md` §5.1 for why):

- **Router** (`RouterBackend` in `ride/RouterBackend.kt`): FOSSGIS (`valhalla1.openstreetmap.de`, no key needed), Stadia Maps (needs a free-tier API key you supply yourself), or LOCAL (on-device Valhalla, Phase C — needs a side-loaded NZ tile tarball, not yet built).
- **Geocoding** (`ride/Geocoding.kt`): Photon's public demo server, fair-use only.

Neither needs configuration to build and run against the hardcoded test destination — only matters once you're testing destination search or want a different routing provider.

## Offline map / offline routing data

Both Phase C (offline routing) and Phase E (offline destination map) need a data file side-loaded onto the device that is **not shipped in the repo or built by Gradle**:

- Offline basemap PMTiles cutout — see `docs/MotoNav_OFFLINE_MAP.md` for how to build and `adb push` it.
- Offline Valhalla NZ tile tarball for `LocalRouteProvider.kt` — not yet built as of this writing; needs `valhalla_build_tiles` run against an NZ OSM extract.

Without these, the app still runs — the map screen shows a blank background and `RouterBackend.LOCAL` has nothing to route against — but neither offline path is testable until the file exists on-device.

## Distribution (later, per PRD)

Once past the emulator/early-device stage, distribute via **Play Console's closed testing track** rather than repeated manual APK installs. That requires:
1. A Google Play Developer account (one-time $25 fee).
2. An upload key / app signing setup (Android Studio can generate this: Build → Generate Signed Bundle/APK).
This is a later step — not needed for the initial emulator/on-device dev loop.

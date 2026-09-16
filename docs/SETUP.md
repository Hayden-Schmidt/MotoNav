# Dev Environment Setup

## Recommended approach

**Develop and initially test on a Windows-hosted Android emulator, move to your real phone (OnePlus 15) once the notification-listener pipeline works in the emulator.**

Why emulator-first:
1. Fast iteration — no cable, no "install APK to phone" round trip for every change.
2. Debugger attaches directly; you can inspect `NotificationListenerService` callbacks and logcat live.
3. Emulators can post local test notifications (via `adb shell cmd notification post ...` or a tiny debug helper) so you can build and test the parser logic *before* you're relying on live Maps/Waze traffic.

Why you still need the real phone before calling Phase 1 done:
1. Emulators don't have Google Maps/Waze installed by default in a way that mirrors real notification behavior, and some emulator images lack Play Services entirely.
2. Bluetooth behavior (auto-launch triggers, background power management) is emulator-unreliable — BT support in AVDs is limited/inconsistent.
3. OnePlus's OxygenOS has its own background-restriction quirks (flagged as an open PRD question) that only show up on real hardware.
4. The actual exit criteria (30+ min test ride, real battery drain over hours) can only be measured on the bike.

So: build and unit-test the parsing/UI logic in the emulator, then do all Bluetooth/power/OEM-behavior/real-ride validation on your OnePlus 15.

## Install steps (Windows)

**Already done on the primary dev machine** (2026-09-17) — this section is for setting up a second machine, or reference if something needs reinstalling.

1. **Install Android Studio**: `winget install --id Google.AndroidStudio`.
   - Bundles the Android SDK, AVD Manager, and Gradle tooling. Run it once after install so its first-run wizard installs the SDK, platform-tools, and emulator.
   - **Gotcha:** Android Studio's bundled JBR (JetBrains Runtime) tracks the newest JDK (JDK 25 as of this writing) — too new for AGP 8.7.0, which fails with a cryptic bare-version-number error. Install a separate **Temurin 21 JDK** (`winget install --id EclipseAdoptium.Temurin.21.JDK`) and point `JAVA_HOME` at it for CLI Gradle builds. Android Studio itself is unaffected (it always uses its own bundled JBR for the IDE).
2. **Set `ANDROID_HOME`/`ANDROID_SDK_ROOT`** to the SDK path (`%LOCALAPPDATA%\Android\Sdk`) and **`JAVA_HOME`** to the Temurin 21 install (`setx`, then open a new shell).
3. **Open this repo** (`C:\Daifuku RAG Dev\Active\MotoNav`) in Android Studio: File → Open → select the folder.
   - Let it sync Gradle on first open (downloads dependencies — needs internet).
   - CLI equivalent: `./gradlew assembleDebug` (uses the repo's pinned Gradle wrapper, 8.10.2).
4. **Create an emulator with Google Play, not just Google APIs:**
   - AVD Manager → Create Device → pick a Pixel profile → choose a system image tagged **"Google Play"** (not "Google APIs") so Play Store and Play Services are present — needed to install real Google Maps/Waze from the Play Store inside the emulator later, if you want to test against the real apps rather than posted test notifications.
   - Current dev AVD: `MotoNav_Pixel7_API35` (Pixel 7 profile, API 35, Google Play image). Android 14 or 15 recommended (match roughly what your OnePlus 15 runs, so notification-permission behavior lines up).
5. **Run the app** (green Run button, or Shift+F10) targeting the emulator, or `adb install -r app/build/outputs/apk/debug/app-debug.apk` after a CLI build.

## Testing on your OnePlus 15

1. Enable Developer Options (Settings → About Phone → tap Build Number 7 times) and USB Debugging.
2. Connect via USB, accept the debugging prompt on the phone.
3. Your device appears in Android Studio's device dropdown next to the emulator — select it and Run to deploy directly.
4. For untethered testing (riding), build a debug APK and install it once, then just observe logs/behavior without staying plugged in; use `adb logcat` over USB only when you need live debug output.

## Kotlin/Compose gotcha

Kotlin 2.0+ decoupled the Compose compiler from the Kotlin Gradle plugin. `org.jetbrains.kotlin.plugin.compose` must be applied alongside `org.jetbrains.kotlin.android` (both root and app `build.gradle.kts`) or the build fails at configuration with "Compose Compiler Gradle plugin is required." `composeOptions.kotlinCompilerExtensionVersion` is obsolete under this setup — don't add it back.

## Notification-listener development note

Android requires the user to manually grant "Notification access" per app in system settings — this can't be requested via a normal runtime permission dialog. The app should deep-link to:
```
Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
```
You'll need to grant this manually every time you reinstall the app during development (it resets on uninstall). This is expected, not a bug.

## Distribution (later, per PRD)

Once past the emulator/early-device stage, the PRD calls for distributing via **Play Console's closed testing track** rather than repeated manual APK installs. That requires:
1. A Google Play Developer account (one-time $25 fee).
2. An upload key / app signing setup (Android Studio can generate this: Build → Generate Signed Bundle/APK).
This is a later step — not needed for the initial emulator/on-device dev loop.

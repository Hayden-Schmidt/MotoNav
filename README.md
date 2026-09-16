# MotoNav

A glanceable, motorcycle-mount navigation companion for Android — reads turn-by-turn data from Google Maps and Waze (via notification listening) and displays a large, high-contrast arrow/distance/street/ETA screen. Built to eventually drive a custom ESP32 round-display dash (Phase 2) with multi-rider pairing (Phase 3).

See [`docs/MotoNav_PRD_Phase1.md`](docs/MotoNav_PRD_Phase1.md) for the full product spec, and [`docs/SETUP.md`](docs/SETUP.md) for the dev environment.

## Status

Phase 1 (Android-only POC) — in development. Not yet functional.

## Project structure

```
MotoNav/
├── docs/                          Product spec, dev setup, research notes
├── app/                           Android app module
│   └── src/main/
│       ├── java/com/motonav/app/  Kotlin source
│       │   ├── MainActivity.kt
│       │   └── notification/      NotificationListenerService + per-app parsers
│       ├── res/                   Layouts, strings, drawables
│       └── AndroidManifest.xml
├── build.gradle.kts               Root Gradle build
├── settings.gradle.kts
└── gradle.properties
```

## Architecture (Phase 1)

1. `NotificationListenerService` captures Google Maps / Waze navigation notifications.
2. Per-app parsers (behind a common `NavDataSource` interface) extract maneuver, distance, ETA, street name.
3. A full-screen Compose UI renders the current nav state.
4. Settings control screen-on behavior and auto-launch triggers (BT connect / nav start / both).

Full rationale and resolved research questions are in the PRD.

## Phase roadmap

1. **Phase 1** — Android app only, phone-mount display.
2. **Phase 2** — ESP32 round-display output over BLE, reusing Phase 1's parsed data pipe.
3. **Phase 3** — Multi-rider pairing/binding so ESP32 units don't cross-connect between riders.

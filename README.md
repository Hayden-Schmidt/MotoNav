# MotoNav

A glanceable, motorcycle-mount navigation companion for Android — reads live turn-by-turn
guidance and displays a large, high-contrast arrow/distance/street/ETA screen. Built to eventually
drive a custom ESP32 round-display dash (Phase 2) with multi-rider pairing (Phase 3).

See [`docs/SETUP.md`](docs/SETUP.md) for the dev environment.

## Status

**OSM stack: Ferrostar + Valhalla, on OpenStreetMap data.** See
[`docs/MotoNav_REBUILD_PLAN_OSM.md`](docs/MotoNav_REBUILD_PLAN_OSM.md) for why (Google's
Navigation SDK is commercial-only and cannot be used here) and the phased build plan (A–E). Older
docs describing the Google Navigation SDK / notification-listening approaches are superseded and
moved to [`docs/Archive/`](docs/Archive/).

Phases A–E of the OSM rebuild plan are implemented (Ferrostar/Valhalla routing — remote and
on-device, route-line rendering on the dial, BLE link to the ESP32 puck, and geocoded destination
search with an offline PMTiles route-selection map). None of it has been build-verified or ridden
yet — see the plan document's phase-by-phase exit tests.

- Git repo initialized; Gradle wrapper pinned in-repo.
- Android Studio, SDK (API 35), Temurin 21 JDK installed; `ANDROID_HOME`/`JAVA_HOME` set.
- `MainActivity` shows a full-screen Compose UI driven by `RideStateHolder`'s `StateFlow`.
- Not yet built: on-device NZ tile tarball (Phase C ships the wiring, not the data), ESP32
  firmware (Phase D ships the phone-side BLE link and the wire-format doc only), BLE OTA/web
  uploader tooling.

## Project structure

```
MotoNav/
├── docs/                          Plan, UI spec, archived docs
│   └── Archive/                   Superseded Navigation-SDK-era docs
├── app/                           Android app module
│   └── src/main/
│       ├── java/com/motonav/app/  Kotlin source
│       │   ├── MainActivity.kt
│       │   ├── nav/               Pure maneuver/geometry/geocoding/elevation logic
│       │   ├── ride/              RideSessionService, FerrostarCore, BLE link, settings-backed state
│       │   ├── ui/dial/           The dial renderer (DialLayout constants, NavDial, RouteLine)
│       │   ├── location/          Compass + GPS
│       │   ├── map/               Destination search + PMTiles route-selection screen
│       │   └── settings/          SharedPreferences-backed settings
│       ├── res/                   Layouts, strings, drawables
│       └── AndroidManifest.xml
├── build.gradle.kts               Root Gradle build
├── settings.gradle.kts
└── gradle.properties
```

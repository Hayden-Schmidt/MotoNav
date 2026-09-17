# MotoNav

A glanceable, motorcycle-mount navigation companion for Android — reads turn-by-turn data from Google Maps and Waze (via notification listening) and displays a large, high-contrast arrow/distance/street/ETA screen. Built to eventually drive a custom ESP32 round-display dash (Phase 2) with multi-rider pairing (Phase 3).

See [`docs/MotoNav_PRD_Phase1.md`](docs/MotoNav_PRD_Phase1.md) for the full product spec, and [`docs/SETUP.md`](docs/SETUP.md) for the dev environment.

## Status

**Architecture revised Sept 2026 — see `docs/MotoNav_PRD_Phase1.md` "Architecture revision" and `docs/RESEARCH_NOTES.md`.** MotoNav is moving from notification-listening (reading Google Maps' own notifications) to the Google Navigation SDK (requesting routes directly from Google's routing backend). The notification-based pipeline below is still in the codebase and still works, but is superseded — new work happens in `app/src/main/java/com/motonav/app/navsdk/`.

GCP setup (`docs/MotoNav_GCP_SETUP.md`) is **complete** — API key is live in `local.properties`. **Next: `docs/MotoNav_IMPLEMENTATION_HANDOFF.md`** — the entry point for wiring the `Navigator` + destination-entry UI + live map screen + phone GPS, with exact file-by-file steps and the Beeline UX reference distilled into concrete implementation targets. Start there before touching `navsdk/` further.

Phase 1 (Android-only POC) — in development. Google Maps notification parsing and a basic
full-screen Compose UI are working and verified end-to-end on-device (PRD Timeline step 2) —
this was the pre-revision milestone; see status note above for the current direction.

- Git repo initialized; Gradle wrapper (8.10.2) pinned in-repo.
- Android Studio, SDK (API 35, Google Play system image), Temurin 21 JDK installed; `ANDROID_HOME`/`JAVA_HOME` set.
- AVD `MotoNav_Pixel7_API35` created; `app-debug.apk` builds via `./gradlew assembleDebug` and installs/launches on it.
- `NavNotificationListenerService` extends navparser's `NavigationListener` and captures live Google Maps navigation notifications; `GoogleMapsNavMapper` converts them to `NavState` (unit conversion, best-effort maneuver-type/street-name derivation from free text).
- `MainActivity` shows a full-screen Compose UI driven by `NavStateHolder`'s `StateFlow`, switching between an idle screen and the live nav screen. Verified live on the emulator: starting/stopping Google Maps turn-by-turn navigation correctly updates and clears the UI.
- Unit tests for `GoogleMapsNavMapper` in `app/src/test/`.
- devgraph MCP (code-graph tool) is currently failing to connect — ignore for now, not blocking.
- Not yet built: Waze notification parsing (needs on-device capture spike first), settings, auto-launch, maneuver-icon rendering, BLE/ESP32 output. See `docs/MotoNav_PRD_Phase1.md` for the phased build order.

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

## Claude Code setup

- `.mcp.json` configures the **context7** MCP server (live library docs) — used for the pinned `GMapsParser`/`navparser` snapshot dependency and AndroidX/Compose APIs.
- `.claude/skills/gen-test` — generates a JUnit 4 test for a Kotlin class, matching this repo's test setup (`/gen-test`).
- `.claude/skills/project-conventions` — background knowledge Claude applies automatically (BLE-serialization constraint on `NavState`, minSdk 26 rationale, parser library guidance).

## Phase roadmap

1. **Phase 1** — Android app only, phone-mount display.
2. **Phase 2** — ESP32 round-display output over BLE, reusing Phase 1's parsed data pipe.
3. **Phase 3** — Multi-rider pairing/binding so ESP32 units don't cross-connect between riders.

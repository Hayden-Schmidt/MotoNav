# MotoNav Task 7 — Staged Implementation Plan

**Created:** 2026-09-17
**Supersedes the sequencing in:** `docs/MotoNav_IMPLEMENTATION_HANDOFF.md` §4 (that doc's *content* is still the reference; this doc replaces its "do it in one pass" framing and corrects the points listed in §0.3).
**Format:** §1 is shared context every stage needs. §2–§7 are six self-contained stage prompts, each intended to be pasted into its own fresh Claude Code session. §8 covers why there are no subagents.

---

## 0. What changed, and why this doc exists

### 0.1 Decisions locked

| Decision | Resolution | Was previously |
|---|---|---|
| Navigator lifecycle | **Foreground service** — extend `RideSessionService` | Open (handoff §5.3) |
| Location + compass hosting | **Same service.** Not screen-scoped | Open (handoff §5.2) |
| Notification path | **Hard sideline.** Not structurally binding on any new design | "Superseded, kept for record" |
| Primary nav display | **Round dial**, map is opt-in secondary | Map-only; dial deferred to Phase 2 |
| Phone vs ESP32 rendering | **Two renderers, one layout.** Phone rich, ESP32 lean, shared layout constants | Not specced |
| Configurability | Per-page element toggles, **nav page only** this phase | Not specced |
| Compass north | Fused rotation-vector sensor, GPS-course fallback, **true-north corrected** | Not specced |
| Units | Metric. Speed limit is a **placeholder, not wired** | Open |
| Map style | Follow system dark/light | Open |
| Battery optimization | Prompt for exemption in stage 1 | Not specced |
| Test destination | Albany, Auckland landmarks | Open |

### 0.2 Two structural changes from the handoff doc

**The dial is now primary.** The handoff doc's §1 resolved the dial-vs-map tension by declaring the round dial a Phase 2 / ESP32-only concern and the phone a map-first surface. **That is reversed.** The product goal is that a rider gets substantially the same interface whether they mount a phone or the ESP32 puck, so the dial is the phone's default view and the map is opt-in.

Consequence: `docs/MotoNav_UI_SPEC.md` is **no longer superseded.** Its §3 state inventory (idle / active / rerouting / stale / arrived) and §5 fallback table are the direct build spec for stage 2. The only stale part is §1's data-inventory table, which describes notification-era fields — read `navsdk/NavSdkState.kt` for the real field list.

**The session lives in a service, not the UI.** See §0.4.

### 0.3 Corrections to the handoff doc

1. **Route geometry may not be unavailable.** The handoff doc and `navsdk/NavSdkState.kt`'s header both state route polyline/geometry is not exposed. That is true of the `NavInfo` turn-by-turn feed. It is *not* obviously true of the `Navigator` object, which appears to expose `getRouteSegments()` / `getCurrentRouteSegment()` returning `RouteSegment` objects with lat/lng lists. If that holds on SDK 7.9.0, Beeline's schematic route-line dial (the white squiggle in `blk_nav.png`) is buildable now. **Stage 5 verifies this before anything depends on it.** Stage 2 builds the dial with a large maneuver icon and treats the route line as a later enhancement, so a negative result costs nothing.

2. **`RideSessionService` has an undocumented hard dependency on the notification listener.** It is started *only* from `NavNotificationListenerService.onListenerConnected()` (`notification/NavNotificationListenerService.kt:29`) and detects nav-start by observing the old `NavStateHolder` (`ride/RideSessionService.kt:46`). Dropping the notification gate without addressing this silently kills wake-lock, screen-on and BT auto-launch — with no code deleted and no error. The handoff doc's §4.5 does not mention this. **Stage 1 fixes it.**

3. **`RideSessionService` declares `foregroundServiceType="connectedDevice"` only.** It now needs `connectedDevice|location`.

### 0.4 Architecture: one service owns the session

An earlier draft of this plan put the `Navigator` in a ViewModel. That was wrong, and the reason is worth recording so nobody re-proposes it.

A ViewModel-owned `Navigator` dies with the Activity. Screen locks or an incoming call arrives → Activity killed → `onCleared()` → `stopGuidance()` → **navigation silently ends mid-ride.** This is not a Phase 2 concern; it fires on an ordinary screen timeout today, and the existing `ScreenOnMode` setting defaults to `NEVER`, so the app relies on the rider having found and enabled it. Android's background location throttling compounds the problem.

**Therefore:** extend `RideSessionService` — which is already a foreground service with a wake lock — to own the `Navigator`, the `FusedLocationProviderClient`, the rotation-vector sensor, and later the BLE link to the ESP32. Rename it if it outgrows its name.

The Activity does exactly two things: show the SDK terms dialog (this genuinely needs an Activity) and request permissions. After that it is a pure consumer of StateFlows. **The UI can die and come back without touching navigation.**

This is also what makes the ESP32 a drop-in rather than a rewrite: the puck becomes a second consumer of the same state. The phone resolves everything and pushes roughly 11 bytes per second over BLE — maneuver enum, distance to turn, speed, heading, ETA, distance remaining, state flags — which fits inside a single BLE notification at the default 23-byte MTU. There is no bandwidth problem here, and BLE does not contend with GPS or cellular in any meaningful way. The thing that actually threatens a long ride is Android killing the app, which is what the foreground service exists to prevent.

### 0.5 Two renderers, one layout

The phone has a GPU. The ESP32-C3 has a single 160MHz RISC-V core, ~150–200KB of free heap once the BLE stack is up, and a 240×240 panel whose full framebuffer alone is 115KB. These are not the same rendering problem and should not pretend to be.

**Do not dumb the phone UI down to ESP32 capability.** The phone gets full animation, anti-aliasing, and whatever looks good. The ESP32 gets a separate, leaner LVGL implementation when hardware lands — same layout and concepts, simpler execution.

**The one shared artifact is a layout-constants file:** dial element positions and sizes expressed as fractions of dial radius, plus the state machine. Compose reads from it; the ESP32 firmware transcribes it to C. This is not a renderer abstraction — there is no interface, and there is no second implementation yet. It exists so the numbers live in one readable place rather than scattered through composables, and so the eventual port is a transcription rather than a re-derivation by eye from screenshots.

Keep a 240×240 `@Preview` beside the phone preview as a **legibility sanity check** — "does this layout still read when it's small" — not as a rendering constraint. Rough guide: on a 1.28" panel, a glyph under ~8% of dial diameter (~19px) is not glanceable on a moving bike.

For reference when the firmware stage arrives, what's cheap on the ESP32 is geometric primitives, subset fonts (digits + units + NSEW), and icons compiled as glyphs in an icon font — all 22 `BucketedManeuver` values cost single-digit KB that way. What's expensive is bitmaps in the repeating redraw path and continuous full-screen animation. A one-shot boot logo bitmap is affordable (115KB of 4MB flash); a 30fps full-screen anything is not.

### 0.6 Version pins to verify, not trust

Every version number below came from an earlier doc or general knowledge. Check each against current release notes before pinning. This is the single most likely source of a wasted debugging hour.

- `com.google.android.libraries.navigation:navigation:7.9.0` — already in the build, pinned Aug 2026.
- `com.google.android.gms:play-services-location` — handoff doc suggests `21.3.0`.
- `com.google.android.libraries.places:places` — version unknown, check [release notes](https://developers.google.com/maps/documentation/places/android-sdk/releases).
- `androidx.core:core-splashscreen` — for the boot logo.

No ViewModel dependency is needed — the service decision in §0.4 removes it.

---

## 1. Shared context — include this header in every stage prompt

> **Project:** MotoNav, at `C:\Daifuku RAG Dev\Active\MotoNav`. A motorcycle navigation app: Android/Kotlin/Jetpack Compose phone app now, an ESP32 round-display handlebar puck later. Solo project, personal POC, no team and no users yet.
>
> **Architecture, current:** Plain Compose + singleton `object` state holders + one foreground service. No DI, no ViewModels, no repository layer. `SettingsStore` is plain SharedPreferences. Keep it that way — do not introduce Hilt, Room, DataStore, a navigation library, or a repository abstraction. This codebase is deliberately small.
>
> **Session ownership:** `ride/RideSessionService` is a foreground service that owns the navigation session — the `Navigator`, location, sensors, wake lock. The UI is a pure consumer of StateFlows and must be able to die and return without affecting navigation. Do not move session state into the UI layer.
>
> **Data source:** Google Navigation SDK 7.9.0. `navsdk/NavSdkTurnByTurnService` receives `NavInfo` at ~1Hz and pushes `NavSdkUiState` into the `NavSdkStateHolder` StateFlow. Read `navsdk/NavSdkState.kt` and `navsdk/Maneuver.kt` first — they are the real field contract.
>
> **The `notification/` package is dead.** It was the previous notification-sniffing data source. The files stay on disk for reference but **its architecture is not binding on anything you build**. Do not extend it, do not mirror its patterns for their own sake, do not preserve its coupling. If something new works better structured differently, structure it differently.
>
> **Units:** metric throughout (m / km / km/h).
>
> **Reference material:** `UI-Guidelines/README.md` indexes annotated Beeline screenshots. `UI-Guidelines/beeline-co-device-screens/blk_nav.png` is the round-dial reference, `blk_Journey_tracking.png` the compass reference, `beeline-app-store/screenshot_7.png` the phone-map reference. Copy **layout language and hierarchy**, not Beeline's screen set, flows or branding.
>
> **Ponytail rules apply:** no speculative abstractions, no interface with one implementation, no config for a value that never changes. Shortest working diff — but only after you have read the code the change touches. Leave one runnable check behind for non-trivial logic.

---

## 2. Stage 1 — Foundation + session service + hardcoded destination

**Goal:** prove `NavSdkStateHolder` populates with real turn-by-turn data on the phone, from a service that survives the screen locking. No real UI. This isolates the riskiest, least-documented part (SDK lifecycle) from every other variable.

**Why this is first:** if the Navigator never becomes ready, everything downstream is unbuildable, and you want to find that out against a plain-text screen rather than while also debugging a custom dial renderer.

### Prompt

> [paste §1 shared context]
>
> **Task:** wire a real `Navigator` from the Google Navigation SDK, owned by a foreground service, and prove it produces live turn-by-turn data that survives the screen locking. Deliberately no real UI in this stage — the exit test is plain text on screen.
>
> **Read first:** `docs/MotoNav_TASK7_STAGED_PLAN.md` §0.4 (why the service, not a ViewModel), `app/src/main/java/com/motonav/app/MainActivity.kt`, `ride/RideSessionService.kt`, `navsdk/NavSdkTurnByTurnService.kt`, `navsdk/NavSdkStateHolder.kt`, `navsdk/NavSdkState.kt`, `notification/NavNotificationListenerService.kt`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`.
>
> **Reference implementation:** Google's official samples at https://github.com/googlemaps-samples/android-navigation-samples — specifically the `Navigation-sample` app. This SDK is new enough that working sample code beats the API reference. Consult it for the `getNavigator` → terms → `setDestination` → `startGuidance` sequence rather than inferring it from docs.
>
> **Do these five things:**
>
> **(a) Un-gate the app from notification access, without breaking `RideSessionService`.**
> `RideSessionService` owns the wake lock, the BT-gated foreground session and auto-launch. It is currently started *only* by `NavNotificationListenerService.onListenerConnected()` and detects nav-start by observing `notification.NavStateHolder`. Both couplings must move: start it from `MainActivity`, and have it observe `navsdk.NavSdkStateHolder` instead. Remove `!isNotificationAccessGranted() -> PermissionRequestScreen()` from `MainActivity`'s `when` block. Leave every file under `notification/` on disk and leave the listener's manifest registration alone.
> *The wake-lock and auto-launch paths have no test coverage — confirm by reasoning through `RideSessionService`'s event flow, not by assuming.*
>
> **(b) Move Navigator ownership into `RideSessionService`.** Change its `foregroundServiceType` to `connectedDevice|location`. The service should:
> - Call `NavigationApi.getNavigator(...)`, handling **both** callback paths. The error path has real, distinguishable cases — terms declined, network failure, not authorised, location permission missing — and each needs a distinct on-screen message. A rider who declined the terms dialog and a rider with a bad API key must not see the same blank screen.
> - Expose a sealed `NavigatorState` (e.g. `Initialising` / `Ready` / `Error(reason)`) as a StateFlow the UI observes.
> - On ready: `registerServiceForNavUpdates(...)` pointing at `NavSdkTurnByTurnService`, then `setDestination(...)`, then `startGuidance()`.
> - On service destroy: `stopGuidance()`, `unregisterServiceForNavUpdates()`, clear `NavSdkStateHolder`. Skipping this orphans a guidance session.
> - **Verify against the SDK sources / samples, do not guess:** the exact `registerServiceForNavUpdates` signature (it may take a display-step count), whether terms acceptance needs an explicit `showTermsAndConditionsDialog` call from the Activity or is implicit in `getNavigator`, whether `getNavigator` accepts a non-Activity context once terms are accepted, and the current `RouteStatus` enum members. This is a beta API surface.
>
> **(c) Add the permission and exemption flows to `MainActivity`.**
> - Runtime location permission. The manifest already declares `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` / `FOREGROUND_SERVICE_LOCATION`, but nothing requests them, and the SDK cannot initialise without them. Use `ActivityResultContracts.RequestMultiplePermissions`. Per the PRD, show a brief in-app explanation before the OS prompt — `PermissionRequestScreen` in `MainActivity.kt` is the existing shape. Handle permanent denial with a route to app settings; a rider who taps deny twice otherwise hits a dead end.
> - Battery-optimization exemption. Check `isIgnoringBatteryOptimizations` and offer a prompt if not. The target device runs OxygenOS, which kills background apps aggressively *despite* a foreground service — this is the most likely cause of a mid-ride death and is cheap to pre-empt.
>
> **(d) Hardcode a test destination.** Put it in a clearly-labelled `const` with a comment marking it as stage-1 scaffolding to be removed in stage 6. Candidates near Albany, Auckland 0630 — **these are approximate and must be checked in Google Maps before use:**
> - North Harbour Stadium ≈ `-36.7255, 174.7145`
> - Westfield Albany ≈ `-36.7290, 174.7000`
> - Massey University Albany ≈ `-36.7315, 174.6990`
> Pick one a few minutes' drive out so the arrival state is reachable in a test ride. Build via `Waypoint.builder().setLatLng(lat, lng).build()`.
>
> **(e) Debug screen.** Replace `MainActivity`'s `NavScreen` with a temporary composable dumping every `NavSdkUiState` field as plain text, plus `NavigatorState`, plus a visible millisecond counter since `lastUpdated` so you can see the ~1Hz cadence with your own eyes.
>
> **Exit test (a real phone — the emulator's mock location makes guidance behave differently):** launch, accept the terms dialog once, confirm the debug screen shows a real maneuver, a decreasing distance-to-turn, and a `lastUpdated` counter cycling roughly once per second. **Then lock the screen for a minute and unlock — guidance must still be running.** That is the regression test for the whole service decision. If `RouteStatus` comes back anything other than OK, report the exact value rather than working around it.
>
> **Stop here.** No dial, no map, no search, no speed. Report what the SDK actually returned, and flag any place the real API differed from what this prompt assumed.

**Files touched:** `MainActivity.kt`, `ride/RideSessionService.kt`, `notification/NavNotificationListenerService.kt` (remove the `startService` call), `AndroidManifest.xml`, `app/build.gradle.kts`.

---

## 3. Stage 2 — The round dial + boot logo

**Goal:** the real product surface. A circular nav display showing live guidance, built `@Preview`-first so the look is settled before real data is attached.

### 3.1 Layout constants

Author the dial's geometry in **a single constants file, as fractions of dial radius** — never as magic numbers buried in composables, never as fixed `dp`. See §0.5 for why: this file is what the ESP32 firmware later transcribes, and it is the only thing the two renderers share.

The phone renderer itself is unconstrained. Animate it, anti-alias it, make it look good.

### 3.2 Prompt

> [paste §1 shared context]
>
> **Task:** build MotoNav's primary navigation display — a round dial. This replaces the stage-1 debug screen and becomes the app's default view.
>
> **Read first:** `docs/MotoNav_TASK7_STAGED_PLAN.md` §0.5 (the two-renderer split), `docs/MotoNav_UI_SPEC.md` (§3 state inventory and §5 fallback table are the direct build spec — the only stale part is §1's notification-era field table), `navsdk/NavSdkState.kt`, `navsdk/Maneuver.kt`, `UI-Guidelines/README.md`, and look at `UI-Guidelines/beeline-co-device-screens/blk_nav.png`.
>
> **Layout rule:** put every dial position and size in **one constants file, as fractions of dial radius**. Not scattered through composables, not fixed `dp`. That file is the artifact the ESP32 firmware transcribes later; the drawing code reads from it. Provide two `@Preview`s — phone size, and one constrained to 240×240dp. The 240×240 one is a **legibility check only** ("does this still read small"), not a rendering constraint — nothing smaller than ~8% of dial diameter. The phone render itself can be as rich as you like.
>
> **Build order — get the look right before touching real data:**
> 1. Build against a hand-written fake `NavSdkUiState`, driven entirely by `@Preview`. Iterate here. No emulator, no build-install cycle.
> 2. Only once it looks right, bind it to `NavSdkStateHolder`.
>
> **The dial's five states** — from `MotoNav_UI_SPEC.md` §3, still current:
> - **Idle** (`NavSdkUiState == null`): dim. Centre shows the logo from part (c).
> - **Active** (`ENROUTE`, fresh): large centred maneuver icon from `BucketedManeuver.icon()` upper zone, large distance numeral with unit beneath in the lower zone. Solid full-brightness outer ring. Roughly 55% upper / 35% lower per `blk_nav.png`.
> - **Rerouting** (`REROUTING`): outer ring to dashed. Maneuver icon dims or becomes neutral — do not display a directional claim the SDK itself is not currently making.
> - **Stale** (`now - lastUpdated` beyond threshold): last-known values dimmed, outer ring to a **distinctly different** broken pattern than rerouting. A rider must tell "the route is recalculating" from "the app lost its feed" at a glance — different problems, different responses. Threshold ~5–8s in a named constant, expect to tune from real riding.
> - **Arrived** (`maneuver == DESTINATION`): flag glyph, no distance countdown.
>
> **Also build:**
>
> **(a) A `NavDialConfig` data class** holding element visibility: `showCompass`, `compassStyle`, `showSpeed`, `showSpeedLimit`, `showEta`, `showDistanceRemaining`, `showStreetName`. Hardcode a sensible default instance — **no settings UI, no persistence**, stage 4 adds those. Reading config from the start makes stage 4 purely additive. Elements whose data does not exist yet (speed, speed limit) render **nothing** when their flag is on — do not fabricate placeholder values and do not draw an empty box where a number will go.
>
> **(b) The compass marker, as a static placeholder.** A red marker on the outer edge, clipped to the circle, indicating north. Four configurable styles: small dot / small outward-pointing triangle / the letter `N` / off. All red. Accept a hardcoded bearing parameter for now and render at the right angle — **stage 3 supplies the real heading.** Reference is the red triangle in `UI-Guidelines/beeline-co-device-screens/blk_Journey_tracking.png`, except ours rotates to track north rather than sitting fixed at top.
>
> **(c) A circular app logo + splash.** Generate a simple, generic, circular mark — a placeholder, explicitly not final branding. The phone version can be rich, but design the **silhouette** so it still reads at 240px monochrome, since the ESP32 will eventually show a simplified variant as its idle screen. Wire it as the Android splash via `androidx.core:core-splashscreen` (a centered circular drawable is exactly this API's intended shape), and reuse it as the dial's idle-state centre.
>
> **(d) Make the dial `MainActivity`'s default screen** and delete the stage-1 debug composable.
>
> **Animation:** Compose built-ins — `AnimatedContent` / `Crossfade` for icon swaps, `animateColorAsState` for ring changes, 150–250ms. Do **not** add Lottie or any animation library. Do **not** interpolate the distance numeral between the SDK's 1Hz updates — a tweened intermediate value is a fabricated number shown to someone deciding whether to brake.
>
> **Exit test:** ride or drive a real route. Correct maneuvers, counting-down distance, visible transition when Maps reroutes, arrived state reached. Check it in daylight — that is the actual operating condition.
>
> **Stop here.** No GPS speed, no settings UI, no map, no search.

**Files touched:** new `ui/dial/DialLayout.kt` (constants), `ui/dial/NavDial.kt` (+ likely `DialRing.kt`, `CompassMarker.kt`), `ui/dial/NavDialConfig.kt`, `MainActivity.kt`, logo drawable + splash theme resources.

---

## 4. Stage 3 — GPS speed + compass heading

**Goal:** the two live sensor values the dial needs. Grouped because both are "read a device sensor into a StateFlow," both live in the session service, and both share a permission.

### 4.1 The declination problem

Auckland's magnetic declination is roughly **20° East**. An uncorrected magnetometer heading puts the north marker a fifth of a quadrant off — obvious and wrong on a dial whose entire job is showing north. `android.hardware.GeomagneticField` computes the correction from lat/lng/altitude/time. **Mandatory, not a refinement.**

### 4.2 The sensor choice

Use `Sensor.TYPE_ROTATION_VECTOR`, not the raw magnetometer. It is the OS's fused sensor (magnetometer + gyroscope + accelerometer) and is what good compass apps actually use. It is dramatically more stable near a motorcycle's metal and electrics than a bare mag reading. It still needs `remapCoordinateSystem` to account for how the phone is physically mounted.

GPS course-over-ground is the **fallback**, not the primary: it produces nothing at a standstill, which is exactly when a rider is most likely to want orientation.

### 4.3 Prompt

> [paste §1 shared context]
>
> **Task:** supply the two live sensor values the dial needs — current speed and compass heading. Both belong to the session service, not the UI.
>
> **Read first:** `ride/RideSessionService.kt`, `ui/dial/NavDial.kt` and `NavDialConfig.kt` (stage 2), `MainActivity.kt`.
>
> **(a) Speed, via GPS.** Add `com.google.android.gms:play-services-location` (verify the current version; the handoff doc suggests `21.3.0`). Note `build.gradle.kts` already excludes `play-services-maps` globally because the Navigation SDK bundles its own — `play-services-location` is a separate artifact and should be unaffected, but confirm the build rather than assuming.
>
> `FusedLocationProviderClient` with `Priority.PRIORITY_HIGH_ACCURACY` at a 1–2s interval — plenty, this drives a numeral, not a map camera.
>
> **Host it in `RideSessionService`, tied to the service lifecycle** — not a `DisposableEffect` in a composable. These values must keep flowing with the screen off, because in Phase 2 the ESP32 displays them while the phone is pocketed, and in Phase 1 the display shouldn't go blank on a screen timeout.
>
> `Location.getSpeed()` returns m/s, convert to km/h. It is **not reliable on all hardware** — some chipsets report 0 or stale values. Gate on `hasSpeed()` and render nothing when false. Do **not** derive speed from position deltas as a fallback; that is noisy enough at low speed to display something visibly wrong.
>
> **(b) Compass heading.** `SensorManager` with `Sensor.TYPE_ROTATION_VECTOR` — the OS fused sensor, not the raw magnetometer, which reads badly near a bike's metal and ignition. Pipeline: `getRotationMatrixFromVector` → `remapCoordinateSystem` for mount orientation → `getOrientation` → azimuth.
>
> **Correct to true north via `android.hardware.GeomagneticField`** using the current location. Auckland's declination is about 20° East — uncorrected, the marker is visibly and consistently wrong. Required, not optional.
>
> Fall back to GPS course-over-ground (`Location.getBearing()`, gated on `hasBearing()`) when the sensor is absent or reports `SENSOR_STATUS_ACCURACY_LOW`. When neither source is usable, **hide the marker — do not freeze it at a stale bearing**, which looks identical to a working compass and is worse than showing nothing.
>
> Smooth lightly so it doesn't jitter, but keep it responsive — a heavily damped compass that lags a turn is useless. Note the wraparound trap: naive averaging across the 0°/360° boundary swings the marker the long way round the dial. Handle it (angle-space interpolation, or smooth the sin/cos components).
>
> **(c) Wire both into the dial.** Speed as a numeral honouring `NavDialConfig.showSpeed`. Heading into the stage-2 compass marker, replacing its hardcoded bearing. Add a **speed-limit placeholder**: reserve its layout slot per `blk_nav.png` (red-circle sign, upper right), gated on `NavDialConfig.showSpeedLimit`, rendering **nothing** when there is no data — which is always, this phase. The turn-by-turn feed carries no speed limits; wiring it needs the Roads API and is out of scope. Leave a one-line comment saying so.
>
> **Leave one runnable check behind** for the two pieces of non-trivial maths — declination correction and angular smoothing across the 0/360 wrap. An `assert`-based self-check or one small `test_*.kt`. No framework beyond the `junit` already present.
>
> **Exit test:** speed matches your bike's speedo within a few km/h. The north marker points at actual north — verify against a known road bearing or another compass app, and specifically check it is not sitting ~20° off, the signature of missed declination correction. Rotate the phone on the mount and confirm the marker stays pointing north. Lock the screen, unlock, confirm both values are still live.
>
> **Stop here.** No settings UI, no map, no search.

**Files touched:** `ride/RideSessionService.kt`, new `location/LocationStateHolder.kt` and `location/CompassStateHolder.kt` (or one combined holder — your call, but do not build a shared abstraction over both for its own sake), `ui/dial/NavDial.kt`, `app/build.gradle.kts`.

---

## 5. Stage 4 — Per-element configurability

**Goal:** the settings surface that makes the dial's elements user-selectable, built so future pages slot in without rework.

**Scope discipline:** the page model should support multiple pages, because that is the known direction, but **only the nav page is wired this phase.** No page-cycling UI, no page-reordering UI, no settings screens for speedometer/compass/trip pages. That is Beeline's full six-page device and it roughly doubles this phase for pages with no data behind them yet.

### Prompt

> [paste §1 shared context]
>
> **Task:** make the dial's elements configurable from the phone's settings menu, persisted across launches.
>
> **Read first:** `settings/SettingsScreen.kt`, `settings/SettingsStore.kt`, `ui/dial/NavDialConfig.kt`, `ui/dial/NavDial.kt`.
>
> **The model:** a page has a list of elements that *can* apply to it; the user toggles which are shown. Structure it so adding a second page later is additive. **Wire only the nav page now.**
>
> Nav page elements: compass marker (a four-way choice — dot / triangle / N / off, not a boolean), current speed, speed limit, ETA, distance remaining, street name, next maneuver.
>
> **Persistence:** extend the existing `SettingsStore` SharedPreferences pattern. **Do not add DataStore** — this is a handful of booleans and one enum, and SharedPreferences is already here and already works.
>
> **UI:** extend the existing `SettingsScreen` in its current style. A section for the nav page listing its elements with toggles, plus a segmented or radio control for compass style. Keep it plain — a settings list, not a design showcase.
>
> **One real constraint:** an element toggled on must never cause the dial to draw an empty slot or a fabricated zero when its data is absent. Confirm by toggling speed limit on — it has no data source at all this phase and must render nothing with the rest of the layout undisturbed.
>
> **Exit test:** toggle each element, background and relaunch, confirm persistence and that the dial reflects it. Toggle everything off and confirm the dial degrades to just maneuver icon and distance rather than breaking its layout.
>
> **Stop here.** No second page, no page cycling, no map, no search.

**Files touched:** `settings/SettingsStore.kt`, `settings/SettingsScreen.kt`, `ui/dial/NavDialConfig.kt`.

---

## 6. Stage 5 — Opt-in map view + route-geometry verification

**Goal:** the Beeline-style full-screen map, as a secondary view. Plus the one open technical question that could improve the dial.

**Why this is late:** it is no longer the primary surface and nothing above depends on it. It is also where the route-geometry question gets answered, and that answer is worth having *after* the dial exists, so you can judge whether a schematic route line is worth adding to it.

### Prompt

> [paste §1 shared context]
>
> **Task:** add the full-screen map as an opt-in secondary view alongside the dial, and answer one open technical question.
>
> **Read first:** `UI-Guidelines/beeline-app-store/screenshot_7.png` (layout reference), `UI-Guidelines/README.md`, `ride/RideSessionService.kt`, `ui/dial/NavDial.kt`.
>
> **Reference implementation:** https://github.com/googlemaps-samples/android-navigation-samples, the `Map-sample` app, for the `GoogleMap` + `Navigator` wiring pattern.
>
> **(a) Verify the route-geometry question first, before building anything.** `docs/MotoNav_IMPLEMENTATION_HANDOFF.md` and `navsdk/NavSdkState.kt` both assert route polyline/geometry is unavailable. True of the `NavInfo` turn-by-turn feed. It appears **not** to be true of the `Navigator`, which seems to expose `getRouteSegments()` / `getCurrentRouteSegment()` returning `RouteSegment` with lat/lng lists.
>
> Check against SDK 7.9.0 and **report the answer explicitly** — it is load-bearing for the dial's future. If route geometry *is* available, a Beeline-style schematic route line on the dial (the white squiggle in `blk_nav.png`) becomes buildable, a meaningful upgrade over a static maneuver icon. **Do not build that line in this stage** — establish whether it is possible and note what the data looks like. Correct the stale comments in `NavSdkState.kt` and the handoff doc either way, so the next person is not misled by the same claim.
>
> **(b) Build the map screen.** Host the Navigation SDK's map view via `AndroidView` (it is a classic View, not Compose-native — standard interop, not a workaround). Enable camera-follow (`followMyLocation`) — this produces the continuously-smooth rotating-map feel, driven by the SDK at render rate independent of the 1Hz data feed. Do not hand-roll camera interpolation.
>
> Set day/night mode to **follow the system theme**. Dark tiles in direct sun are hard to read and this app is used outdoors.
>
> Overlay per `screenshot_7.png`: opaque top bar (maneuver icon + distance to turn, settings icon right), opaque bottom bar (ETA + time left, distance remaining right, stop button). Opaque, not translucent — these must stay legible over arbitrary map content.
>
> **(c) Make it opt-in.** Dial stays default. Add a simple switch — a control on the dial and a way back. **Do not add a navigation library**; a boolean in existing state covers two screens. Structure it so a proper page model can replace it later without the map screen changing.
>
> **Exit test:** switch to the map mid-navigation, confirm it follows and rotates smoothly with the route drawn, overlay bars update in step with the dial's values, and switching back does not interrupt guidance or restart the session.
>
> **Stop here.** No search yet.

**Files touched:** new `ui/map/NavMapScreen.kt`, `MainActivity.kt`, possibly `navsdk/NavSdkState.kt` (comment correction).

---

## 7. Stage 6 — Destination search

**Goal:** replace the hardcoded coordinate with real address search. Last because it is the most self-contained piece and blocks nothing above it.

**Blocking prerequisite — your action, not Claude's:** Places API (New) must be enabled in GCP project `motonav-508822` *and* added to the API key's restriction list, per `docs/MotoNav_GCP_SETUP.md` §3a. Do this before starting. If search silently returns no predictions, this is overwhelmingly the likely cause, not a code bug.

### Prompt

> [paste §1 shared context]
>
> **Task:** build destination search, replacing the hardcoded stage-1 coordinate.
>
> **Prerequisite:** Places API (New) is enabled in GCP and added to the API key's restrictions. If predictions come back empty, suspect this before suspecting the code.
>
> **Read first:** `ride/RideSessionService.kt`, `app/build.gradle.kts`, `docs/MotoNav_IMPLEMENTATION_HANDOFF.md` §4.2, and look at `UI-Guidelines/beeline-co-app-screens/moto_app_planning.webp`.
>
> **(a) Add the Places SDK for Android** (`com.google.android.libraries.places:places` — check current version against the release notes). Initialise with `Places.initialize(context, apiKey)` reading the **same** `MOTONAV_MAPS_API_KEY` already wired through `local.properties` → `build.gradle.kts` → manifest. No second key.
>
> **(b) Build the search screen.** Search field top, a "current location" convenience row pinned above results, scrollable predictions below (pin icon, name, subtitle, distance away). `moto_app_planning.webp` is the layout reference, not a clone target.
>
> Autocomplete for as-you-type predictions. On selection, fetch coordinates via `FetchPlaceRequest` with **Place Details Essentials** — we need a lat/lng, nothing more. This also closes the autocomplete session, the correct billing pattern regardless of the fact that this usage sits comfortably inside the free tier.
>
> Handle no-results, no-network and API-error states with visible messages. A silently empty list is indistinguishable from a misconfigured API key, and you will waste time on it.
>
> **(c) Wire it in.** Build `Waypoint.builder().setLatLng(lat, lng).build()` and pass it to the service's existing `setDestination` path. **Remove the hardcoded stage-1 constant.** Search becomes the entry point: app opens to search when idle, dial takes over once guidance starts.
>
> **(d) Full verification pass.** `docs/MotoNav_IMPLEMENTATION_HANDOFF.md` §4.6, plus: cold start with no permissions granted, terms dialog appearing exactly once, screen locked mid-ride with guidance surviving, and killing the app mid-navigation leaving no orphaned session.
>
> **Not in scope:** tap-to-drop-a-pin (worth adding later for trailheads and informal meeting points, but search was the actual ask), saved/favourite places, route options. On saved places specifically — Google's Starred/Favourites lists are private app data exposed by **no** public API. Not an integration gap to solve; not accessible to any third-party app.

**Files touched:** new `ui/search/DestinationSearchScreen.kt`, `ride/RideSessionService.kt`, `MainActivity.kt`, `app/build.gradle.kts`, `MotoNavApp.kt` (Places init).

---

## 8. On subagents — don't

Straight-shot each stage in its own session. No orchestration, no fan-out.

**Stages 1→2→3 are strictly sequential.** Each one's exit test is "does this work on my phone," and each depends on the previous actually running. A parallel agent building the dial while the Navigator is unproven is building against a guess.

**The failure mode you are avoiding is diagnostic, not throughput.** The whole reason for staging is that when something breaks you know which layer it is in. Subagents reintroduce exactly the ambiguity staging removes — with the added problem that an agent's summary describes what it meant to do, not what it did, so you would end up reading the diff anyway.

**Stages 4, 5 and 6 are genuinely independent** of each other once 1–3 land. If you want to compress the calendar, run those three in parallel *sessions* — but they touch `MainActivity` in overlapping ways, so expect to reconcile by hand. For a solo POC that is probably not worth it.

The one thing worth delegating is a focused research question — e.g. "does `Navigator.getRouteSegments()` exist in 7.9.0 and what does it return." That is a lookup with a crisp answer, which is what subagents are actually good at.

---

## 9. Deferred — explicitly out of scope, recorded so they are not rediscovered

| Item | Why deferred | Unblocked by |
|---|---|---|
| Speed limits | Not in the turn-by-turn feed; needs Roads API | A decision to add that API + its cost |
| Schematic route line on dial | Depends on stage 5's geometry answer | Stage 5 |
| ESP32 LVGL renderer + BLE link | No hardware yet | Hardware |
| Speedometer / compass / trip / summary pages | Net-new subsystems each | Nav page proving the config model |
| Page cycling UI | Only one page exists | A second page |
| Tap-to-drop-pin destination | Search was the ask | Time |
| Waze support | Explicit PRD non-goal | — |
| Lottie / custom animated glyphs | Compose built-ins cover current needs | A specific animation Compose can't do |
| Release-build SHA-1 in API key restrictions | Only debug SHA-1 configured | Any Play Console distribution |
| Custom icon set | `material-icons-extended` lacks right-handed U-turn/roundabout and any ferry glyph (see `navsdk/Maneuver.kt:121`) | Deciding the reused-left-glyph compromise isn't good enough |

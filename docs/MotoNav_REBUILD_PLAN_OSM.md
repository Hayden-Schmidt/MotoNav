# MotoNav — OSM Stack Rebuild Plan

**Created:** 2026-09-17
**Supersedes:** `MotoNav_TASK7_STAGED_PLAN.md` (stages 1–6), `MotoNav_IMPLEMENTATION_HANDOFF.md`, and the "Architecture revision (Sept 2026)" section of `MotoNav_PRD_Phase1.md`.
**Status of build at time of writing:** Task 7 stages 1–4 complete, halted mid-stage-5.

---

## 0. Why we are stopping

Three independent findings, any one of which is sufficient.

### 0.1 The Navigation SDK is commercial-only

Google's Navigation SDK for Android policies page states plainly:

> "The Navigation SDK for Android is allowed only for commercial applications."

MotoNav is a hobby project intended for open-source release. It does not qualify. This is not a grey area and not a clause that can be engineered around.

The earlier research pass recorded in `MotoNav_PRD_Phase1.md` concluded the opposite — that the Navigation SDK's Service Specific Terms "explicitly permit" this use case, correcting an earlier pass that had said it was blocked. **That correction was itself wrong.** It examined the ToS and the Service Specific Terms but not the SDK's own policies page, where the commercial-only restriction lives. Record this, because it has now flipped twice.

### 0.2 The ESP32 pipeline sits on the scraping line

Same policies page:

> "Road Name and Speed Limit may be returned to you based on user interactions with your app. If you were to capture or persist the Road Name or Speed Limit for use in any other context outside of the user session, this would constitute scraping, which is not allowed by our terms."

Streaming to a BLE display during an active session is arguably in-session. Persisting a speed limit on the ESP32 across power cycles is not. The general Maps Platform ToS reinforces this: customers must not export, extract or otherwise scrape Google Maps Content for use outside the Services, and caching is prohibited except where expressly permitted.

Phase 2 is the entire point of this project. An architecture that becomes non-compliant the moment the hardware arrives is not an architecture.

### 0.3 The Beeline road-snake is forbidden under Google data

The Service Specific Terms repeat, service by service, that Google Maps Content "must not be used in conjunction with a non-Google map."

Stage 5 was about to verify whether `Navigator.getRouteSegments()` exposes route geometry, so we could draw the white squiggle from `blk_nav.png` on our own dial. **Do not run that verification.** Even a positive result is unusable: Google route geometry rendered into our own road view is precisely the combination that clause exists to prevent. Google's own turn card is permitted; our re-rendering of Google's geometry is not.

### 0.4 Also: the original notification path was never clean either

The Google Maps end-user terms prohibit using any part of Google Maps "with other people's products or services for or in connection with real-time navigation," excepting Google-provided features such as Android Auto. The ESP32 is other people's products. The notification-sniffing path we already abandoned for reliability reasons was independently non-compliant. This closes that door as a fallback — it is not somewhere to retreat to.

### 0.5 What replaces it

| Component | Role | Licence |
|---|---|---|
| OpenStreetMap | Underlying road data | ODbL — attribution required |
| Valhalla | Routing engine, C++ | MIT |
| `valhalla-mobile` (Rallista) | Valhalla as an Android `.so` | MIT |
| Ferrostar (Stadia Maps) | Navigation state machine, Rust core + Kotlin bindings | BSD |
| Protomaps PMTiles | Basemap archive, route-selection screen only | Code BSD; data ODbL |
| MapLibre Native Android | Renders PMTiles, `pmtiles://` since 11.7.0 | BSD |
| Photon (optional) | Geocoding | Apache-2.0 |

Total obligation: credit OpenStreetMap, state that the data is ODbL. Our re-rendered road view and the ESP32 output are **Produced Works**, not Derivative Databases — the OSMF test is whether the published result is intended for extraction of the original data. It isn't. **ODbL does not force us to open-source anything.** We're open-sourcing by choice; the licence isn't what compels it.

---

## 1. What survives, what dies

This is the part worth reading carefully. Less is being thrown away than the stack change implies, because the existing architecture was deliberately source-agnostic below the data layer.

### 1.1 Keep, unchanged

| Asset | Why it survives |
|---|---|
| `ride/RideSessionService` | The §0.4 reasoning in the old plan — a ViewModel-owned navigator dies on screen lock, mid-ride — is correct and source-independent. A Ferrostar core needs a foreground service for exactly the same reason. Keep the wake lock, the BT-gated session, the auto-launch. Only the thing it owns changes. |
| `ui/dial/DialLayout.kt` | Fractions-of-radius constants. Zero Google coupling. This is the ESP32 transcription artifact and it was the single best structural decision in the old plan. |
| `ui/dial/NavDial.kt` + ring/compass components | Consumes a state object, doesn't care who filled it. Rebinding to a new state class is a field-rename exercise. |
| `ui/dial/NavDialConfig.kt` + stage-4 settings | Element-toggle model, `SettingsStore` SharedPreferences persistence, settings UI. Entirely orthogonal to the data source. |
| Compass subsystem | `TYPE_ROTATION_VECTOR` → `remapCoordinateSystem` → `getOrientation` → `GeomagneticField` declination correction. Auckland's ~20°E declination problem is a property of Auckland, not of Google. The angular-smoothing-across-0/360 logic and its self-check stay too. |
| GPS speed via `FusedLocationProviderClient` | `play-services-location` is a separate artifact from anything Maps. Keep it. |
| Permission + battery-exemption flows | OxygenOS still kills background apps. Location permission is still needed — more so, since Ferrostar needs a location feed we supply ourselves. |
| Splash / circular logo | Unaffected. |
| The five dial states | Idle / active / rerouting / stale / arrived, from `MotoNav_UI_SPEC.md` §3. Ferrostar has an equivalent state machine; the *visual* contract stands, including the rule that rerouting and stale must be visually distinct. |
| Ponytail rules | No DI, no Room, no DataStore, no repository layer, no interface with one implementation. Still correct. The new stack does not justify relaxing this. |

### 1.2 Delete

| Asset | Reason |
|---|---|
| `navsdk/` package entirely | `NavSdkState`, `NavSdkStateHolder`, `NavSdkTurnByTurnService`. Ferrostar replaces all of it. |
| `notification/` package | Kept "for record" through two architecture changes, now confirmed non-compliant per §0.4. Delete it. It is not a fallback. |
| Navigation SDK dependency + API key plumbing | `local.properties` → `build.gradle.kts` → manifest meta-data chain. |
| GCP project `motonav-508822` | Nothing in the new stack authenticates to it. Turn off billing. |
| Places SDK search (stage 6, unbuilt) | Replaced by Photon or an offline index. Nothing lost — it was never built. |
| `MotoNav_GCP_SETUP.md` | Obsolete. |

### 1.3 Rework, don't rewrite

| Asset | Change |
|---|---|
| `navsdk/Maneuver.kt` — `BucketedManeuver` | The **bucketing concept is the valuable part** and it transfers directly. Google's 62 constants become Valhalla/Ferrostar's maneuver set; the target buckets and the icon mapping stay. Rename to `nav/Maneuver.kt`. The recorded gap — `material-icons-extended` lacks a right-handed U-turn, roundabout variants, and any ferry glyph — is unchanged and still deferred. |
| Stage 5's map screen | Was: Google `NavigationView` with `followMyLocation`, full-screen, always available. Now: MapLibre + local PMTiles, **route-selection only**, not a live nav surface. Different enough to start fresh, but the overlay-bar layout language from `screenshot_7.png` still applies. |
| `MotoNav_UI_SPEC.md` | §1's data-inventory table was already stale (notification-era). It is now stale twice over. Rewrite §1 against the Ferrostar field list. §3 and §5 stand. |

---

## 2. Target architecture

```
┌─────────────────────── Android phone ───────────────────────┐
│                                                             │
│  RideSessionService  (foreground: connectedDevice|location) │
│    ├─ FerrostarCore                    ← replaces Navigator │
│    │    └─ Valhalla route provider                          │
│    │         ├─ remote: FOSSGIS / Stadia   (phase A)        │
│    │         └─ local:  valhalla-mobile    (phase C)        │
│    ├─ FusedLocationProviderClient      ← unchanged          │
│    ├─ ROTATION_VECTOR compass          ← unchanged          │
│    ├─ NavStateHolder : StateFlow<RideState>                 │
│    └─ BleLink                          ← phase D            │
│                                                             │
│  UI (pure consumer, may die freely)                         │
│    ├─ NavDial      ← unchanged renderer, rebound            │
│    ├─ Settings     ← unchanged                              │
│    └─ RoutePlanner ← new: MapLibre + PMTiles, selection only│
│                                                             │
└─────────────────────────────────────────────────────────────┘
                              │ BLE, ~11 bytes/s
                              ▼
                   ESP32-C3 · GC9A01 240×240 · LVGL
```

Unchanged from the old plan and still correct:

- The service owns everything with a lifecycle. The Activity requests permissions and consumes StateFlows.
- The ESP32 is a second consumer of the same state, not a second implementation. The phone resolves everything; the puck renders.
- Two renderers, one layout constants file. Phone is unconstrained; ESP32 transcribes the fractions to C. No renderer abstraction, no interface.

### 2.1 One new internal boundary

`RideState` — the single state object the dial, the BLE link and the eventual ESP32 all read. Ferrostar's own state types stay *inside* the service.

Reason: this is the third data source this project has had. The first two (notification parsing, Navigation SDK) each required touching the renderer. A thin owned state class means the fourth doesn't.

This is not a speculative abstraction — it is one data class with a mapping function, justified by two observed rewrites.

---

## 3. Build phases

Each phase is one Claude Code session with a hardware-verifiable exit test. Strictly sequential through C; D and E are independent.

---

### Phase A — Swap the brain

**Goal:** Ferrostar + remote Valhalla producing live maneuvers on the existing dial. Same test ride, same screen, different engine.

**Why first:** this is the whole bet. Everything else is downstream. Prove it against the dial you already have rather than debugging a new renderer at the same time.

**Work:**

1. Delete `navsdk/`, delete `notification/`, strip the Navigation SDK dependency and the API-key chain from `build.gradle.kts` and the manifest.
2. Add Ferrostar. **Take `com.stadiamaps.ferrostar.core` only** — skip `composeui` and `maplibreui`. We have a better renderer for this use case and pulling MapLibre in here would drag the route-planner decision forward prematurely.
3. `RideSessionService` owns a `FerrostarCore` instead of a `Navigator`. Feed it the existing `FusedLocationProviderClient` output.
4. Routing backend, **user-configurable from day one** — not hardcoded:
   - **FOSSGIS** `valhalla1.openstreetmap.de` — full planet, fair-use, 1 req/s per user, 100 req/s total. They ask that published apps announce themselves via GitHub Discussions, send an identifying `X-Client-Id` header, and **explicitly recommend not hardcoding the service URL in the app**.
   - **Stadia Maps free tier** — 200,000 credits/month, no card, commercial use not allowed (fine for us). This is the tier Ferrostar's own docs assume.
   - Make it a setting because FOSSGIS asks for it, and because a hardcoded shared endpoint is the thing that kills a forkable project.
5. Motorcycle costing: Valhalla's `motorcycle` profile. Real options are `use_highways`, `use_tolls`, `use_trails`, `exclude_unpaved`, `top_speed`, `shortest`, `use_distance`. Start with defaults, expose nothing yet.
6. Map Ferrostar's maneuver set onto the existing `BucketedManeuver` buckets. Rebind `NavDial` to `RideState`.
7. Keep the hardcoded Albany test destination. Search comes in Phase E.

**Exit test:** real ride. Correct maneuvers, counting-down distance, visible reroute transition, arrived state reached. **Lock the screen for a minute mid-ride — guidance must survive.** That was the regression test for the service decision and it remains the regression test.

**Explicitly not in this phase:** map screen, offline tiles, search, elevation, BLE.

---

### Phase B — Route line on the dial

**Goal:** the white squiggle from `blk_nav.png`. The thing stage 5 was about to investigate, now legally and technically unblocked.

**Why this is now easy:** Ferrostar hands us the full route polyline. No `getRouteSegments()` archaeology, no terms question. This was the single biggest capability gap between MotoNav and Beeline and it closes here.

**Work:**

1. Simplify the polyline to a schematic — heading-relative, current position pinned at the arrow, forward slice only.
2. Add the geometry constants to `DialLayout.kt` alongside the existing element fractions. ESP32-transcribable: polyline rendering is *cheap* on an ESP32 (geometric primitives), unlike bitmaps or full-screen animation.
3. Optional, same phase because it's the same data: the side-road stubs visible either side of the route in the Beeline reference.

**Exit test:** 240×240 `@Preview` legibility check, then a real ride. Does the squiggle actually help, or is the large maneuver icon better? **Genuinely answer this** — Beeline ships both and it may be a mode, not a replacement.

---

### Phase C — Go offline

**Goal:** routing with no network. NZ back-country is the actual use case and this is where the OSM stack decisively beats what we had.

**Work:**

1. Add `valhalla-mobile` (`io.github.rallista:valhalla-mobile`, MIT, 0.6.3 as of Aug 2026). Ships Valhalla as an Android shared library, exposing `route`, `trace_route`, `trace_attributes`, `height`, `sources_to_targets` — **all running entirely against tiles on the device.**
2. Build an NZ-only Valhalla tile tarball. Ship or side-load it; do not attempt dynamic multi-region sync. Their own docs flag that as the hard part: pre-built tarballs are easy, bbox extracts are reasonable, syncing regions built at different times is where it breaks. NZ-only is a single periodically-rebuilt tar and completely tractable.
3. Point Ferrostar's route provider at the local engine. Remote stays as a user-selectable option.
4. **Elevation, free:** Valhalla's `height` action samples elevations under a shape. Needs an elevation tile directory in the config — without it every height returns null. This closes the nice-to-have.

**Exit test:** aeroplane mode, full route, complete ride. Elevation profile populated.

---

### Phase D — ESP32

**Goal:** the actual product. Unblocked by hardware, not by software.

**Work:**

1. `BleLink` in `RideSessionService`, consuming the same `RideState`. ~11 bytes/s — maneuver enum, distance to turn, speed, heading, ETA, distance remaining, state flags. Fits a single BLE notification at the default 23-byte MTU. There is no bandwidth problem here.
2. Firmware: ESP32-2424S012 (ESP32-C3, GC9A01 240×240), LVGL. Transcribe `DialLayout.kt` to C.
3. **Steal bikenavi_esp32's distribution model wholesale:** browser-based web uploader, BLE OTA. Buy board → flash in browser → install APK → ride. That model is why a hobby project gets a hundred builders instead of five.

**Constraints, recorded from the old plan and still accurate:** single 160MHz RISC-V core, ~150–200KB free heap once BLE is up, 115KB for a full framebuffer. Cheap: geometric primitives, subset fonts (digits + units + NSEW), icons as glyphs in an icon font. Expensive: bitmaps in the repeating redraw path, continuous full-screen animation. One-shot boot logo bitmap is affordable against 4MB flash.

**Exit test:** phone pocketed, full ride navigated from the puck alone.

---

### Phase E — Destination entry

**Goal:** replace the hardcoded coordinate. Last because it is self-contained and blocks nothing.

**Work:**

1. **Geocoding.** Photon's public demo server is explicitly fair-use — "extensive usage will be throttled or completely banned," no availability guarantee. Fine for us today; a liability the day the repo gets traction. Self-hosting is out of scope (planet DB ~95GB, 64GB RAM recommended). Make the endpoint configurable, same as the router. Consider an offline NZ place index as the durable answer.
2. **Route-selection map.** MapLibre Native Android, PMTiles via the `pmtiles://` prefix (supported since 11.7.0). Use `pmtiles extract` for an NZ cutout — the full planet is ~120GB. Two gotchas: PMTiles sources **do not support offline pack downloads or caching** in MapLibre Native, and `pmtiles://asset://` is unsupported — use `getExternalFilesDir` with `pmtiles://file://`.
3. Layout language from `screenshot_7.png` and `moto_app_planning.webp`. Reference, not clone.

**Exit test:** search, route, ride, no hardcoded constants remaining.

---

## 4. Carried forward — still open

| Item | Status |
|---|---|
| **Speed limits** | Our weakest point on this path, and worth naming now. The Beeline reference screens show a `70` ring; Google and Apple hand that over for free. OSM `maxspeed` coverage has historically been thin — the NLnet default-speed-limits project cited roughly 12% of OSM roads tagged. NZTA publishes NZ speed limit data but under CC-BY-4.0, which the OSM NZ community has flagged as needing a waiver before import. **Cleanest route: ship NZTA data as a separate app layer under CC-BY attribution rather than routing it through OSM.** Keep the stage-3 placeholder slot rendering nothing until then. |
| **"Fun" / twisty routing** | Valhalla's motorcycle costing has **no curvature parameter**. Beeline's Fun mode has no direct equivalent. Approximate with `use_highways` near 0 and distance weighting; genuine sinuosity preference means computing curvature from `trace_attributes` edge geometry and scoring candidate routes ourselves. Real work. **Sequence it last** — it is a research problem sitting on top of a solved navigation problem and must not block Phase A. |
| **Custom icon set** | Unchanged. `material-icons-extended` lacks right-handed U-turn, roundabout variants, ferry. |
| **Multi-page dial** | Unchanged. The stage-4 page model supports it; only the nav page is wired. |
| **Multi-rider pairing** | Phase 3, unchanged. |

---

## 5. Open-source hygiene

These are the decisions that determine whether the repo survives contact with the public. Cheap now, expensive later.

1. **No shared keys or endpoints in the repo.** Router and geocoder URLs are settings with README instructions. FOSSGIS explicitly asks for this.
2. **Attribution.** `© OpenStreetMap contributors (ODbL)` visible without interaction on the route-selection map and in About. Add the `openstreetmap.org/fixthemap` link if using FOSSGIS. The ESP32 is a Produced Work with no room for a credit line — attribution on the phone and in the docs is the reasonable reading, since the ODbL test is whether attribution is reasonably calculated to make people aware.
3. **`X-Client-Id` header** on FOSSGIS requests, and announce the project via their GitHub Discussions before any public release.
4. **Fixture-based regression tests.** Steal this from SteedPilot — it has fixtures for roundabout countdowns near preceding junctions, exactly the class of bug that is miserable to find on a bike and trivial to catch on replay. Our current coverage is JUnit self-checks for pure logic plus manual adb verification for everything sensor-dependent. Recorded route fixtures close most of that gap with no hardware. Add them from Phase A, not retroactively.
5. **Licence.** Our code MIT or BSD to match the stack. ODbL applies to the data, not to us.

---

## 6. Sequencing

```
A ──► B ──► C ──► D
                  │
      E ──────────┘   (independent after A)
```

A → B → C are strictly sequential; each exit test is "does this work on my bike" and each depends on the previous actually running.

Straight-shot each phase in its own session. **No subagents.** The reasoning from the old plan §8 holds: the failure mode being avoided is diagnostic, not throughput. Staging exists so that when something breaks you know which layer it is in, and an agent's summary describes what it meant to do rather than what it did. The one thing worth delegating is a focused research question with a crisp answer.

**Verify every version pin against current release notes before pinning.** Ferrostar, `valhalla-mobile`, MapLibre Native and `play-services-location` all move. This remains the single most likely source of a wasted debugging hour.

---

## 7. What this buys

| | Before | After |
|---|---|---|
| Legal basis | Commercial-only SDK, hobby project | MIT / BSD / ODbL-attribution |
| Distributable as FOSS | No | Yes |
| Fork requires | Google Cloud billing account | Nothing |
| Route geometry for the dial | Forbidden with non-Google map | Available |
| Offline | No | Yes (Phase C) |
| Elevation | Not exposed | `height` action, on-device |
| Motorcycle profile | Two-wheeled travel mode | Dedicated `motorcycle` costing |
| Speed limits | Provided | **Our problem** |
| Twisty routing | Not available | Buildable, but our work |
| Cost at any scale | Per-destination billing | Free |

The two things we give up — speed limits and any hope of a free "Fun" mode — are both real, and both are solvable with our own work. The thing we gain is a project that can actually be published.

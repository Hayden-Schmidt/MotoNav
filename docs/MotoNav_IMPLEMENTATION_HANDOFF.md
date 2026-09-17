# MotoNav — Implementation Handoff (Navigation SDK wiring + GPS + Beeline UX reference)

**Purpose of this doc:** single entry point for continuing this build in a fresh session/tool (Claude Code). It consolidates what's already done, exactly what Task 7 requires file-by-file, the GPS/location wiring that hasn't been designed anywhere else yet, and the Beeline UX reference distilled into concrete implementation targets. Read this first; it links out to the supporting docs rather than repeating them.

**Status as of Sept 17 2026:** GCP project (`motonav-508822`) created, billing linked, API key created and restricted (API restrictions: Navigation SDK, Maps SDK for Android, Maps SDK for iOS, Places API (New), Maps Elevation API — see `docs/MotoNav_GCP_SETUP.md` for why iOS and Elevation are deliberately included, not leftovers; Application restriction: Android apps, package `com.motonav.app` + debug SHA-1 fingerprint). Key is in `local.properties` as `MOTONAV_MAPS_API_KEY` (gitignored) and wired through `app/build.gradle.kts` → `AndroidManifest.xml` meta-data already. Gradle/AGP/compileSdk/targetSdk/desugaring all bumped to Navigation SDK 7.9.0's requirements (see `docs/RESEARCH_NOTES.md` "Technical requirements"). The `navsdk/` package exists (`Maneuver.kt`, `NavSdkState.kt`, `NavSdkStateHolder.kt`, `NavSdkTurnByTurnService.kt`) but is **wired and inert** — nothing calls `NavigationApi.getNavigator()` or `registerServiceForNavUpdates()` yet, and `MainActivity` still runs entirely on the old notification-listening path (`NavStateHolder`, `NavState`, `NavScreen` in `MainActivity.kt`).

**What's left (this doc's actual subject):**
1. Wire a real `Navigator` + destination-entry UI, retire the notification path from the active UI (keep the old files in place, per the existing "superseded, not deleted" convention — see `notification/NavDataSource.kt`'s header comment).
2. Add phone GPS (current position + speed) — not built anywhere yet, needed for the live map's own-position puck context and the phone screen's speed metric (`docs/MotoNav_UI_SPEC.md` §4, "Reserved row... current speed").
3. Build the live map view itself (Phase 1 scope per the most recent steer: Google Maps only, single-arrow, Beeline-inspired layout) using the Navigation SDK's bundled `GoogleMap` — this hasn't been built yet either; only the data-layer (`navsdk/` package) exists.

Supporting docs, not restated here: `docs/RESEARCH_NOTES.md` (Navigation SDK decision + Beeline case study raw findings), `docs/MotoNav_UI_SPEC.md` (round-dial screen states, written before the live-map decision — see note in §1 below on how it now applies), `docs/MotoNav_PRD_Phase1.md` (product goals/non-goals), `docs/MotoNav_GCP_SETUP.md` (account setup, already complete), `UI-Guidelines/README.md` (Beeline screenshot index — the source for everything in §2 below).

---

## 1. How the live map view fits with the existing round-dial spec

`docs/MotoNav_UI_SPEC.md` was written for a *dial-only* display (no map tiles, just a big icon + distance) because at the time the data source was notification-listening (no route geometry available) and the Phase 1 scope was "single arrow point like Beeline's device screen." The subsequent steer ("scope Phase 1 UI to Google Maps only... im happy to essentially copy their ui... more about their application flow and layout") plus the Navigation SDK adoption changes this: the SDK bundles a real `GoogleMap` and we're explicitly building the **phone full-screen turn-by-turn view**, which per `UI-Guidelines/README.md` is Beeline's `screenshot_7` / `moto_app_3.webp` layout (map visible, arrow+distance overlaid), not the dial-only device screen (`blk_nav.png`).

**Resolution:** build the Phase 1 phone screen as Beeline's full-screen map layout (§2 below), reusing the *existing* `ManeuverType`/`BucketedManeuver` icon set and the UI_SPEC's state machine (idle / active / rerouting / stale / arrived) for the overlay content — the state logic in `MotoNav_UI_SPEC.md` §3 and §5 is still correct and should drive the overlay; only the "no map tiles, dial only" framing is superseded. The round-dial-only treatment becomes the Phase 2 ESP32 target once that hardware exists (UI_SPEC's original intent), not the Phase 1 phone screen.

---

## 2. Beeline UX reference — what's actually sourced vs. what's a recommended implementation target

**Scope clarification (Sept 2026, refines the earlier "essentially copy their ui" steer):** MotoNav reuses Beeline's **visual style and UI direction** — layout proportions, hierarchy conventions, icon usage, the general "how information is arranged on screen" language — not its **application functionality or exact design**. Concretely: copy things like the two-zone screen split, the fixed-arrow/rotating-map convention, opaque top/bottom info bars, one-accent-color-per-primary-action. Do **not** copy things like Beeline's specific screen set (route-style tabs Fast/Fun/Quiet/Compass, ride history/Journeys tab, waypoint-skip prompts, their exact ride-summary flow) or treat any Beeline screenshot as a pixel-accurate template to clone — MotoNav's actual screens, states, and flows are defined by `docs/MotoNav_UI_SPEC.md` and this doc, informed by Beeline's direction rather than mirroring its app 1:1. Where a section below says "reuse X's structure," read that as "use X as the layout reference," not "replicate X verbatim."

Everything in `UI-Guidelines/` was captured from **static screenshots and product photos** (App Store listing images, beeline.co press assets) — there is no video/motion capture of the real app. That matters here because the user asked specifically about "smoothness," which is a motion property. Treat the two lists below as separated on purpose: list A is what the screenshots actually show and can be copied directly; list B is this doc's own engineering recommendation for how to make MotoNav *feel* smooth, inferred from the static layout conventions and from what Navigation SDK's update cadence actually allows — not a measurement of Beeline's real animation code, which we have no access to.

### 2A. Sourced from screenshots (`UI-Guidelines/README.md`, `screenshot_7`, `blk_nav.png`, `moto_app_3.webp`)

- **Two-zone screen split**: map fills the top majority, a dark info band anchors the bottom third (distance/ETA). Full-screen map, not a small inset.
- **Fixed-arrow, rotating-world convention** (device screen `blk_nav.png`): the direction arrow itself never rotates — the map/route line rotates underneath it. This is the single most important "smoothness" cue we *can* source: it means the perceived motion is the map panning/rotating continuously as the rider's bearing changes, not a maneuver icon animating. Directly implementable: Navigation SDK's `GoogleMap` supports this natively via `mapView.followMyLocation(CameraPerspective.TILTED)` (or equivalent camera-follow API), which keeps the camera centered on the user with bearing-locked rotation — this is standard Navigation SDK camera behavior, not something we build by hand.
- **Floating circular buttons over the map** (`screenshot_7`): compass-toggle and mute, top-left/top-right, layered over the map rather than in a toolbar — avoids a hard top bar eating map space.
- **Top black info bar**: turn icon + distance, left-aligned; settings icon, right-aligned. Non-floating, opaque band (not overlaid translucently on the map) — this is the part that should stay perfectly legible regardless of what's under it, hence opaque rather than a translucent overlay.
- **Bottom black info bar**: ETA + time (left), distance-remaining (right), large circular stop button (bottom-right) — same opaque-band treatment.
- **3D-tilted map with building extrusion** — cosmetic; Navigation SDK's `GoogleMap` supports tilt via camera position, optional for Phase 1 (flagged as a nice-to-have, not required for MVP legibility).
- **Solid vs. dotted route-line** = confirmed vs. rerouting state (`blk_rerouting_*.png`) — a state-driven visual (not an animation), cheap to implement as a stroke-style swap on route-line rendering, or (since we render via Navigation SDK's own polyline layer, not a hand-drawn line) as a UI-layer dashed border/pulse on the overlay bars instead, per `UI-Guidelines/README.md`'s note that this pattern is "worth copying for our own reroute/stale-data indicator."
- **One dominant accent color** reserved for the single primary action per screen (their yellow "Go"/checkmark buttons) — pattern to copy, not the literal color (per the earlier explicit steer: flow/layout/icons only, not branding).

### 2B. Recommended implementation targets for "smooth" (not sourced from Beeline — our own engineering plan, flagged as such)

These exist because the *data* driving the UI updates at different rates than the *screen* should render, and naive 1:1 binding (render only when new data arrives) would look stepped/janky regardless of what Beeline actually does internally:

- **Navigation SDK's `NavInfo` feed updates ~once per second** (`docs/RESEARCH_NOTES.md`, "Turn-by-turn data feed"). Directly re-rendering text/icons only on each `NavInfo` message is fine for the distance countdown and maneuver icon (a rider doesn't need sub-second precision on "distance to turn" text) — **do not** try to interpolate/tween the numeral itself between updates; that adds complexity for no perceptible benefit and risks showing a fabricated intermediate number.
- **Camera/map motion is the one thing that must be continuously smooth**, and this is solved for free: Navigation SDK's own camera-follow mode (`followMyLocation`) is driven by the SDK's internal location/bearing updates at normal map-render frame rate, independent of the ~1Hz `NavInfo` cadence — this is exactly why Beeline's "map rotates continuously, arrow stays fixed" convention (2A) works smoothly on a real GPS-driven product: the thing that visibly moves every frame (the map) is decoupled from the thing that updates once a second (the turn/distance text). We should rely on the SDK's built-in camera-follow rather than writing our own camera interpolation.
- **State transitions (idle → active, active → rerouting, active → stale) should use Compose's built-in animation APIs** (`AnimatedContent`/`Crossfade` for icon swaps, `animateColorAsState` for the outer-band/border color/stroke-style changes described in `UI-Guidelines/README.md`'s solid-vs-dotted pattern) rather than a hard cut — a hard cut on a 1Hz-updating display reads as "did it just glitch?" more than a purposeful state change. Keep transition duration short (150–250ms) so it doesn't itself become a distraction at a glance.
- **Distance-remaining/ETA text updates should debounce to whole-second granularity** (they already will, since `NavInfo` arrives ~1Hz) — no code changes needed here beyond not over-engineering a faster update path than the SDK provides.
- **Do not build a custom-rotating arrow.** Per the PRD's own P2 "Future Considerations" note, a continuously GPS-heading-rotating maneuver arrow (Android Auto-style) was explicitly deferred pending a decision the Navigation SDK's own camera-follow mode now resolves for free at the *map* level — the arrow itself should stay in the fixed convention from 2A. Revisit only if a future pass wants a rotating arrow specifically instead of/in addition to a rotating map.

---

## 3. GPS / phone location — not built anywhere yet, needed for two things

**Important distinction:** Navigation SDK's `GoogleMap` already renders its own "my location" blue-dot/puck on the map surface, using the SDK's internal location handling once navigation is active — that part needs no extra code from us. What's *not* provided by the turn-by-turn `NavInfo` feed (confirmed by field inventory in `navsdk/NavSdkState.kt` — no speed field anywhere in `NavSdkUiState`) is:

1. **Current speed**, for the phone screen's metrics band (`docs/MotoNav_UI_SPEC.md` §4, currently a reserved/unpopulated row).
2. **Raw current position independent of an active nav session** — useful if we ever want a "here's roughly where you are" idle-state treatment, or for future compass-mode bearing math (Phase 3, out of scope here but the plumbing should not preclude it).

### 3.1 Dependency + permissions (permissions already declared, dependency is not)

`AndroidManifest.xml` already has `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_LOCATION` — added ahead of time when the Navigation SDK pivot happened, but the runtime permission *request* flow doesn't exist (only the notification-listener permission has a request screen — `PermissionRequestScreen` in `MainActivity.kt`). `app/build.gradle.kts` does **not** yet have a location-provider dependency. Add:

```kotlin
implementation("com.google.android.gms:play-services-location:21.3.0")
```

(Re-check version against the current release before pinning — same caveat as the Navigation SDK pin itself. `play-services-location` is a separate artifact from `play-services-maps`, so it is **not** affected by the `exclude(group = "com.google.android.gms", module = "play-services-maps")` block already in `build.gradle.kts` — no conflict expected, but verify the build after adding it given the exclusion is already in place for a related reason.)

### 3.2 Runtime permission request

`MainActivity`'s current permission handling only checks notification-listener access (`isNotificationAccessGranted()`), which is a special-access setting, not a runtime permission — different API from `ACCESS_FINE_LOCATION`, which needs the standard `ActivityResultContracts.RequestPermission` (or `RequestMultiplePermissions` for fine+coarse together) flow. This needs its own request screen/step, gated similarly to how `PermissionRequestScreen` currently gates the main content — add a location-permission check alongside the existing notification-access check in `MainActivity`'s `when` block, with its own explanatory screen (per the PRD's existing pattern: "clear in-app explanation screen, not just the bare OS prompt").

### 3.3 Where the location client lives

Follow the existing StateFlow-singleton pattern used by both `notification.NavStateHolder` and `navsdk.NavSdkStateHolder` rather than introducing a different pattern for a third data source. Suggested new file: `app/src/main/java/com/motonav/app/location/LocationStateHolder.kt` (or fold into a `location/` package alongside a `LocationClient` wrapper class), producer being a `FusedLocationProviderClient` with a `LocationRequest` built via `Priority.PRIORITY_HIGH_ACCURACY`, updated at an interval sensible for a "current speed" readout (1–2 second interval is plenty — this does not need to match the map's own internal render-rate smoothness from §2B, since it's driving a numeral, not the camera).

- Register the location callback in a foreground-service-adjacent scope consistent with the existing `RideSessionService` foreground-service pattern (`app/src/main/java/com/motonav/app/ride/RideSessionService.kt`) — do not start a second independent foreground service if `RideSessionService` can host this; check its current responsibilities before deciding whether to extend it or add a narrowly-scoped second service. This wasn't decided in any prior session — flag as an open implementation decision, not a prescribed answer.
- Speed value: `Location.getSpeed()` returns m/s when available (device/GPS-chipset dependent — may be null/stale on some hardware); convert for display consistent with the existing `formatDistance()` unit-handling convention in `MainActivity.kt`.

---

## 4. Task 7 — exact wiring steps, file by file

This is the actual remaining build task. Order matters (each step depends on the previous one existing).

### 4.1 Accept Navigation SDK terms-of-use + get a `Navigator`

Per `NavSdkTurnByTurnService.kt`'s own header comment, this doesn't exist yet. In `MainActivity` (or a small dedicated `navsdk/NavigatorProvider.kt` if it's cleaner to keep `MainActivity` from growing further — matches the existing pattern of keeping `navsdk/` self-contained):

- Call `NavigationApi.getNavigator(activity: Activity, callback: NavigatorListener)`. First call on a fresh install shows the Navigation SDK's own terms-of-use dialog (Google-provided UI, not ours to build) — the callback fires once accepted (or with an error/decline path to handle: `onError(int errorCode)` per the SDK's documented contract — surface a clear failure state rather than silently doing nothing if the user declines terms or the SDK reports an error).
- Hold the resulting `Navigator` instance for the activity/app's lifetime (it's expensive to recreate) — a natural fit for a small singleton/holder similar in spirit to `NavSdkStateHolder`, or held in a `ViewModel` if one gets introduced at this point (none exists in the codebase today — everything is currently plain Compose + singletons, so introducing a `ViewModel` here is an architecture decision worth flagging rather than assuming).

### 4.2 Destination-entry UI — real address search (decided; supersedes the earlier "flag before building" note)

**Decision (Sept 2026):** build real address search, matching Beeline's `moto_app_planning.webp` reference (back chevron, end-field with entered text, current-location row pinned above results, list of matched addresses with pin icon + name + subtitle + distance-away) — not the pin-drop fallback this doc originally proposed. Checked the actual cost first (`docs/RESEARCH_NOTES.md` "Places API (New) — destination search pricing"): 10,000 free autocomplete searches/month, unlimited free session bundling, not a realistic cap for solo use — so this isn't the cost-tradeoff it looked like initially. Places API (New) needs enabling + adding to the key's API restrictions (`docs/MotoNav_GCP_SETUP.md` §3a) — **Hayden-only GCP Console step, same pattern as the original Navigation SDK enable, not something Claude Code can do.**

**Note on personal saved places:** confirmed via research that neither the Places API nor Places SDK exposes a user's own Google Maps "Starred/Favorites/Want to Go" lists — that's private app data with no public API (Google has an open, unresolved feature request for this themselves). Not a gap in our integration; it's not accessible to any third-party app. Destination search here means general address/place lookup, not "pick from my saved Google Maps places."

**Implementation:**

- Use the **Places SDK for Android**'s Autocomplete component (`com.google.android.libraries.places:places` — check current version against [the SDK's release notes](https://developers.google.com/maps/documentation/places/android-sdk/releases) before pinning, same versioning caveat as the Navigation SDK dependency) for the as-you-type suggestion list. Initialize with `Places.initialize(context, apiKey)` reading the same `MOTONAV_MAPS_API_KEY` already wired through `local.properties`/`build.gradle.kts`/manifest — no second key needed, it's the same restricted key with one more API now allowed on it.
- On a selected prediction, fetch place details (lat/lng) via `FetchPlaceRequest` (Place Details Essentials tier is sufficient — no need for Pro/Atmosphere data, we only need the coordinate) — this closes the Autocomplete session so it stays in the free-bundled-session tier rather than reverting to per-keystroke billing (still free either way at this usage scale, but the session-closing call is the correct integration pattern regardless).
- Build the `Waypoint` from the resolved coordinate: `Waypoint.builder().setLatLng(lat, lng).build()`, then proceed to §4.3.
- Layout: informed by `moto_app_planning.webp` — search field top, current-location convenience row, scrollable results list — as the layout reference, not a verbatim clone (see this section's scope clarification above). It's a standard, non-branded search-then-select pattern, and the general arrangement is worth following; exact spacing/styling is MotoNav's own.
- Tap-to-drop-a-pin-on-the-map remains worth keeping as a **secondary** input method (useful when there's no clean address for a location, e.g. a trailhead or informal meeting point) but is no longer the primary/only Phase 1 path — build search first since that's the actual ask, add pin-drop after if time allows.

### 4.3 Start navigation

- `navigator.setDestination(waypoint, callback)` — handle the route-status callback (`Navigator.RouteStatus`: `OK`, `NO_ROUTE_FOUND`, `NETWORK_ERROR`, `LOCATION_DISABLED`, etc. — exact enum per current SDK reference, verify against installed SDK version's docs since this evolves) with a real error UI state, not just the happy path.
- `navigator.registerServiceForNavUpdates(packageName, NavSdkTurnByTurnService::class.java.name)` — this is the call `NavSdkTurnByTurnService.kt`'s header comment says is still missing. Once called, `NavSdkStateHolder.state` starts populating for real.
- `navigator.startGuidance()` to begin active turn-by-turn.

### 4.4 Build the live map screen

New Composable (e.g. `NavMapScreen.kt`), replacing `MainActivity.kt`'s current `NavScreen` as the active-navigation view:

- Hosts the Navigation SDK's `GoogleMap`/`NavigationView` (via `AndroidView` interop in Compose, since the Navigation SDK's map component is a classic View, not Compose-native — this is standard practice for any Play Services map in a Compose app).
- Enable camera-follow (`followMyLocation`) per §2B.
- Overlay: top bar (icon from `BucketedManeuver.icon()` + `distanceToCurrentStepMeters`, per §2A layout), bottom bar (`timeToFinalDestinationSeconds`/ETA + `distanceToFinalDestinationMeters`), floating settings button — reusing `NavSdkStateHolder.state`'s fields directly (all already modeled in `NavSdkState.kt`), and the existing state-machine logic from `docs/MotoNav_UI_SPEC.md` §3/§5 (idle/active/rerouting/stale/arrived) driving which overlay content shows, per the resolution in §1 above.
- Staleness detection: reuse the same `lastUpdated`-delta approach already specified in UI_SPEC §3.4 — `NavSdkUiState.lastUpdated` already exists for exactly this (see `NavSdkState.kt` field comment).

### 4.5 Rewire `MainActivity`, retire the notification path from the active UI

- Replace `val navState by NavStateHolder.state.collectAsState()` with `val navSdkState by NavSdkStateHolder.state.collectAsState()`, and the `when` block's `NavScreen(navState!!, ...)` branch with the new map screen from 4.4.
- Do **not** delete `notification/NavNotificationListenerService.kt`, `notification/GoogleMapsNavMapper.kt`, `notification/NavDataSource.kt`, `notification/NavStateHolder.kt`, or their manifest service registration — all already carry explicit "superseded, kept for record, not deleted" comments (see `NavDataSource.kt` header) written specifically so this rewiring wouldn't need a judgment call about whether to remove working code. Leave the manifest's `NavNotificationListenerService` registration in place too, for the same reason — it costs nothing while unused and preserves a fallback reference implementation.
- Add the location-permission gate from §3.2 to `MainActivity`'s permission-check `when` branch, alongside the existing notification-access check (which can likely be removed from the *required-to-proceed* gate once the notification path is no longer the active UI — but only from the gate, not the code, per the point above; flag this as a decision for whoever does this pass rather than presupposing whether notification access should still be requested at all going forward).

### 4.6 Testing/verification before calling Task 7 done

- Cold-start flow: terms-of-use dialog appears once, destination entry works, route calculates, map renders with camera-follow, turn-by-turn overlay updates roughly every second matching `NavInfo` cadence, rerouting state visibly differs from normal (per §2A dotted/solid convention adapted to whichever element we end up styling), arrival triggers the arrived state.
- Confirm destination search actually returns predictions before assuming the code is broken — if it silently returns nothing, check first whether `docs/MotoNav_GCP_SETUP.md` §3a (enable Places API (New) + add it to the key's restrictions) was actually completed, since that's a manual step outside this codebase and the most likely failure cause.
- Confirm the API key restriction (Navigation SDK + Maps SDK for Android + Places API (New), Android-app + package/SHA-1 restricted) doesn't block anything at runtime — if a `REQUEST_DENIED`/`API key not valid` type error appears, the most likely cause is either a missing API in the key's restriction list or a release-build SHA-1 not yet added (only the debug SHA-1 was configured — release builds will need their own entry added later, before any Play Console distribution, not blocking for local dev/testing).
- Re-confirm `docs/MotoNav_PRD_Phase1.md`'s exit criteria still make sense against the new architecture (2-second update latency target, etc.) — written against notification-listening's known lag profile, likely still valid but worth a sanity check once real Navigation SDK timing is observed on-device.

---

## 5. Open decisions flagged for whoever picks this up (not resolved by this doc — deliberately surfaced, not guessed)

1. ~~Places API vs. tap-to-set-destination~~ **RESOLVED** — build real address search via Places SDK Autocomplete (§4.2); cost checked, not a real constraint at this usage scale. Only remaining action is the Hayden-only GCP Console step in `docs/MotoNav_GCP_SETUP.md` §3a (enable Places API (New), add it to the key's restrictions) before this screen will actually authenticate.
2. **Where the location client's foreground service lives** (§3.3) — extend `RideSessionService` or add a new one.
3. **Whether to introduce a `ViewModel`** now that there's a `Navigator` lifecycle to manage (§4.1) — the codebase has been plain-Compose-plus-singletons throughout; this is the first piece of state with real lifecycle/cleanup requirements (a `Navigator` shouldn't just be recreated on every recomposition).
4. **Whether notification access should still be requested at all** once the notification path is no longer the primary UI (§4.5) — the underlying parsers are being kept for record, but that doesn't by itself answer whether the app should still prompt for the permission on every fresh install.

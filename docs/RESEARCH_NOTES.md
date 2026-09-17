# Research Notes

Findings gathered during PRD development, kept here so they don't only live in chat history. See the PRD's Open Questions and Prior Art sections for the resolved/actionable summary — this file has the raw source list for reference.

## Notification parsing libraries / prior art

- **`3v1n0/GMapsParser`** (github.com/3v1n0/GMapsParser) — Kotlin library, LGPL-3.0, on JitPack (`com.github.3v1n0.GMapsParser:navparser:master-SNAPSHOT`). Parses Google Maps navigation notifications into a structured `NavigationData` object: `nextDirection` (localeString + localeHtml + nested distance), `remainingDistance`, `eta`, `actionIcon` (Bitmap of Maps' own turn icon), `isRerouting`, `finalDirection`. No separate street-name field — it's embedded in `nextDirection.localeString`. **Recommended as a direct dependency** rather than writing our own Maps parser.
- **`prusux/reNavigator`** — regex-style notification parsing across nav apps including Waze. Not yet reviewed in detail; next step for closing the Waze turn-by-turn open question.
- **`REDLINE`** (Arjun P., arjunp.pro/projects/suzuki-connect-re.html) — production app, same notification-listening architecture as this project, targeting a factory motorcycle Bluetooth dash instead of a phone screen. Validates the overall approach. Useful reference for Phase 2 protocol design methodology (JADX decompile + Frida hooking + Android HCI snoop log capture) if we ever need to interoperate with third-party hardware.

## Google Navigation SDK — architecture decision (Sept 2026, superseding earlier "ruled out" note)

**Correction to this project's earlier conclusion:** this section previously said the Navigation SDK was ruled out over ToS restrictions on building a navigation "substitute." That restriction is real but comes from the **general Google Maps Platform Terms of Service** (2018-02-07, §10.9 "No navigation" — covers the base Maps/Places/Geocoding APIs). The **Navigation SDK is a separate product with its own Service Specific Terms (§12)**, which explicitly permit our use case and do not carry that restriction. Re-verified directly against Google's current docs (Sept 2026) — full findings below.

### Decision: adopt Navigation SDK as the core Phase 1 architecture

Following the comparison discussion in this session, MotoNav is moving off notification-listening as its primary data source and onto the Navigation SDK: we call Google's routing backend directly and receive structured turn-by-turn data, rather than reading Google Maps app notifications. Rationale (full comparison table discussed in chat, summarized here):

- Notification-derived `maneuverType`/`streetName` were both best-effort text-parsing (keyword matching / regex) — brittle across phrasing/locale changes and confirmed via `GoogleMapsNavMapper` code review to have real gaps (`streetName` null on roundabout/arrive/reroute text).
- The Navigation SDK's **turn-by-turn data feed** (`TurnByTurnManager`/`NavInfo`/`StepInfo`) is a first-party feature built for exactly this project's use case — Google's own docs describe it for "two-wheeled vehicle drivers... project navigation-only guidance... minimal distractions."
- It gives structured, non-guessed data: a proper `Maneuver` enum, full road name, distance-to-step, lane guidance, remaining trip time/distance, and a real `NavState` (`ENROUTE`/`REROUTING`/`STOPPED`) — no keyword matching anywhere.
- It also resolves the Phase 3 dead-reckoning blocker (see the "Beeline Moto II case study" section below) for free: since we call `setDestination()` ourselves, we already hold the destination coordinates needed for compass-mode bearing math — no share-intent capture hack needed.
- Tradeoff, accepted knowingly: this means MotoNav becomes a real navigation app (its own destination search/route request), not a passive companion display reading an app the rider already trusts. This directly changes the Phase 1 PRD's original non-goal ("no turn-by-turn routing of our own") — the PRD is being rewritten to reflect this.

### Turn-by-turn data feed — what it actually provides

Source: developers.google.com/maps/documentation/navigation/android-sdk/tbt-feed (current as of this research pass).

- You register a `Service` with the SDK's `Navigator` (`registerServiceForNavUpdates`); it receives `NavInfo` messages roughly once per second while navigating, via `TurnByTurnManager`.
- **Per-step fields (`StepInfo`):** full road name, maneuver icon/type (real enum, e.g. `TURN_LEFT`, `ON_RAMP_LEFT`, `DESTINATION_LEFT`/`DESTINATION_RIGHT`, etc.), distance to the next step, lane guidance (`Lane`/`LaneDirection` objects — which lane is recommended for the upcoming maneuver).
- **Per-trip fields (`NavInfo`):** remaining time, distance to destination, overall `NavState` (`ENROUTE` = active guidance with step info available; `REROUTING` = looking for a new route, no step info until one's found; `STOPPED` = user exited navigation).
- No route polyline/shape is exposed through this feed — same geometry gap we already had with notification parsing, so the UI spec's decision to use a big maneuver icon instead of a schematic route line (see `docs/MotoNav_UI_SPEC.md`) still holds.

### Offline capability — unchanged conclusion, re-confirmed for Navigation SDK specifically

Re-checked directly against the Navigation SDK's own FAQ (not just the general Maps SDK FAQ used in the original research pass): **no offline mode**, pre-cached information only. So switching to the Navigation SDK does not gain us offline capability — both approaches need live connectivity. This was previously checked against the wrong product's FAQ; conclusion happens to be the same for both, but worth having verified the right source.

### Pricing

Source: developers.google.com/maps/documentation/navigation/android-sdk/pricing, mapsplatform.google.com blog (pricing update).

- Billed per destination/waypoint requested (the "Navigation Request" SKU) — **not** per navigation session. Starting turn-by-turn guidance, rerouting due to traffic/road closures/deviation are all free once a destination has been fetched.
- **First 1,000 destinations/month free.** For personal solo-rider use this is very unlikely to ever be exceeded.
- Requires a Google Cloud project with billing enabled (a payment method on file) even to stay within the free tier — standard Google Cloud requirement, not a MotoNav-specific cost. Account/billing setup is a Hayden-only step; see `docs/MotoNav_GCP_SETUP.md`.

### Terms of Service — actual restriction, verified in the current Service Specific Terms (§12, cloud.google.com/maps-platform/terms/maps-service-terms)

- §12.1 *Use without a Google Map* — explicitly permits using Navigation SDK content in an app **without a corresponding Google Map**. This is exactly MotoNav's design (arrow/distance dial, no rendered map tiles) — directly supports the architecture in `docs/MotoNav_UI_SPEC.md`.
- §12.2 *No Use with a non-Google map* — the actual restriction: don't combine Navigation SDK data with a non-Google map (e.g., don't overlay their turn data on an OpenStreetMap render). Irrelevant to us since we show no map at all.
- §12.3 *Caching* — may cache lat/lng from the SDK for up to 30 consecutive days, then must delete. Irrelevant to real-time nav use.
- No restriction found on two-wheeled vehicles, personal/hobby use, or custom BLE hardware output.

### Technical requirements — Navigation SDK 7.9.0 (current as of Aug 17 2026, per Maven)

- **Cannot coexist with the Maps SDK in the same app** — Navigation SDK bundles its own Maps SDK; any dependency that pulls in `play-services-maps` must exclude it.
- Minimum Android 7 (API 24) — our current `minSdk = 26` already clears this.
- Target Android 16 (API 36) or higher — our current `targetSdk = 35` does **not** clear this; needs bumping.
- For SDK 7.7.0+: requires Gradle 8.13, Android Gradle Plugin 8.13.2, and `com.android.tools:desugar_jdk_libs_nio:2.1.5` for Java 8 desugaring. Our project is currently on Gradle 8.10.2 / AGP 8.7.0 — needs bumping.
- Device requirements: Google Play services, 2GB+ RAM, OpenGL ES 2.0 — no impact on our target hardware (rider's own phone).
- Dependency coordinate: `com.google.android.libraries.navigation:navigation:7.9.0` (Google Maven repository) — version number should be re-checked against the release notes page before final pin, since this SDK ships monthly.

## Places API (New) — destination search pricing (Sept 2026, resolves the earlier "flag before building" note)

Source: developers.google.com/maps/billing-and-pricing/pricing (fetched directly, last updated by Google 2026-09-10).

- **Autocomplete Requests** (the as-you-type suggestion dropdown): 10,000 free/month, then $2.83/1,000.
- **Autocomplete Session Usage**: unlimited, always free — a "session" bundles all the keystroke-by-keystroke Autocomplete calls for one search into a single billable unit, which only gets charged under the Autocomplete Requests SKU above if the session is properly closed out with a Place Details call.
- **Places API Place Details Essentials** (resolving a selected suggestion to a real place/coordinate): 10,000 free/month, then $5.00/1,000.
- **Geocoding API** (simpler alternative — no autocomplete dropdown, just full-address-string → coordinate): 10,000 free/month, then $5.00/1,000.
- **Conclusion:** for a solo personal-use rider, 10,000 searches/month is not a realistic usage level to approach — this is a free feature in practice, same conclusion as the Navigation SDK's own free tier. Enabling the Places API (New) for destination search is not a meaningful cost decision, just a GCP Console step (see `docs/MotoNav_GCP_SETUP.md`) and an addition to the API key's allowed-APIs restriction list (currently scoped to Navigation SDK + Maps SDK for Android only — `Places API` needs adding).
- This supersedes the earlier flagged concern in `docs/MotoNav_IMPLEMENTATION_HANDOFF.md` §4.2/§5 that treated enabling Places API as an open cost decision requiring a stop-and-ask — checked, and it isn't one at this usage scale.

## Waze — no usable SDK or public API

- **Waze Transport SDK**: partner-only (rideshare/fleet integrations), requires a formal Waze partnership, and is being sunset November 1, 2026 in favor of "Navigation Connect." Not accessible for this project.
- **Waze Deep Links**: one-way only (opens Waze with a destination), returns no data. Not useful for reading nav state.
- **No public API for alerts, traffic, or routing exists.**

## Waze live-map scraping — confirmed dead end (March 2026+)

- Endpoint: `https://www.waze.com/live-map/api/georss` (bounding-box params: `top`/`bottom`/`left`/`right`/`env`/`types`). Used historically by third-party projects to pull police/hazard/accident/camera alert data for an area.
- Prior art that used this: `muke24/Alert-Detector` (ESP32 device, reports the endpoint broke for bare-device HTTP clients — suspected cookie/session blocking), `guberm/waze-alerts-monitor` (Flutter app, MIT licensed).
- **Confirmed blocked as of this project's testing (Sept 2026):**
  - Live test from sandbox (datacenter IP): 403.
  - Live test from Hayden's phone on mobile data, real coordinates: 403.
  - `guberm/waze-alerts-monitor` issue #1 (filed 19 Mar 2026, still open): *"waze implemented a recaptcha enterprise WAF, requests to https://www.waze.com/live-map will result in 403."*
- Also forecloses a related lead: `Nimrod007/waze-api` (stale 2015 Java wrapper) suggested a `/driving-directions` / `routesWithDirections` endpoint might expose real turn-by-turn steps from Waze's backend — same domain, same WAF, presumed equally blocked. Not tested separately since the georss result already demonstrates the blocking is domain-wide bot protection, not endpoint-specific.
- **Conclusion:** any Waze data (alerts or routing) must come from notification-listening on-device, not the live-map API. Alerts specifically are unconfirmed even via notifications (see PRD open question) since police/hazard alerts read like an in-app popup overlay, not necessarily a tray notification.

## Competitive landscape

- **Beeline Moto** — proprietary app + non-standardized BLE protocol (confirmed via Pure Maps maintainer discussion on GitHub). No industry-standard protocol exists for phone-to-handlebar-display navigation; every product (including this one) has to build its own.
- **Chigee / Carpuride** — CarPlay/Android Auto screen-mirroring hardware, not a custom-protocol receiver. Different problem (bigger mirrored screen) than this project (custom minimal glanceable UI + eventual custom ESP32 receiver).
- **TomTom Rider** — dedicated GPS hardware, not phone-companion, not directly comparable.

## Beeline Moto II case study (Sept 2026) — informs Phase 3 dual-mode decision

Full sourced write-up gathered via web/firecrawl research; summarized here as the actionable reference. Beeline is the closest commercial analog to this project's eventual Phase 2/3 hardware, and it ships **two nav modes** (turn-by-turn "Route" mode and bearing-only "Compass" mode) that we don't currently plan to replicate. This section captures what their product does and whether our notification-listening architecture can support the same two modes.

### Beeline architecture vs. MotoNav architecture

| | Beeline Moto II | MotoNav (this project) |
|---|---|---|
| Routing/maps source | Own stack: OpenStreetMap base + Mapbox routing engine + HERE traffic (paid tier) + proprietary "Fun/Fast" scoring layer (whitelisted countries only) | None — reads whatever Google Maps/Waze is already navigating via `NotificationListenerService` |
| Where compute happens | Phone app (all GPS, routing, map render) | Phone app (all notification parsing) |
| Display hardware | Custom round puck, 53mm, 1.45" IPS TFT 412×412, no GPS chip — only accelerometer/gyro/magnetometer | Phone screen (Phase 1); custom ESP32 round display planned (Phase 2), same "dumb peripheral" pattern |
| Data pushed to display | Compact state: bearing, distance-to-next-turn, maneuver icon, ETA, speed, mini-map tiles | Planned Phase 2: `NavState` (maneuver type, distance, street name, ETA) broadcast over BLE — same pattern |
| Offline capability | None confirmed — needs phone connectivity to reroute | None — inherited from Google Maps/Waze's own connectivity requirement |
| Nav modes shipped | Route (turn-by-turn) **and** Compass (dead-reckoning bearing+distance only) | Route only (planned) |

### Data availability gap analysis — can MotoNav support both modes?

| Field needed | Route mode (turn-by-turn) | Compass mode (dead-reckoning) | Available from Gmaps/Waze notifications today? |
|---|---|---|---|
| Current position | Not required | Required | N/A — sourced from phone's own GPS (`ACCESS_FINE_LOCATION`), independent of notification data. Not a gap. |
| Current heading | Not required | Required | N/A — sourced from phone's own magnetometer. Not a gap. |
| Destination coordinates (lat/lon) | Not required | **Required** — needed to compute bearing | **No.** Notification extras only ever carry Maps/Waze's rendered text output (maneuver string, street name, ETA string), never the destination coordinate. Confirmed via `GoogleMapsNavMapper`/`NavDataSource` — no coordinate field exists in what we parse. |
| Maneuver type (turn direction) | Required | Not required | Partial — derived via best-effort English keyword matching on `maneuverText` (`GoogleMapsNavMapper.deriveManeuverType`). Works, but brittle across locales/phrasing changes. |
| Distance to next turn | Required | Not required | Yes — `distanceToTurnMeters` in `NavState`. |
| Street name | Nice-to-have | Not required | Partial — regex-extracted from maneuver text (`onto/on <street>` pattern only); null for roundabout/arrive/reroute text. |
| ETA / remaining distance | Nice-to-have | Nice-to-have | Yes — `etaText`, `remainingDistanceMeters`. |

**Conclusion:** Route mode is already supported by the current data pipeline (with known accuracy caveats above). Compass mode is blocked on exactly one field — destination coordinates — which Google Maps/Waze never expose via notification, by design (their notification API is a rendered display surface, not a data API). This is the single blocker, not a general data-availability shortfall.

### Options to close the destination-coordinate gap (ranked, for Phase 3 decision)

| Option | How it works | Pros | Cons |
|---|---|---|---|
| **1. Capture via Android share-intent (recommended)** | User shares a pin from Google Maps ("Share" → MotoNav); share payload is typically a `geo:` URI or Maps short-link carrying real lat/lon, caught via an intent filter | Real coordinates, no new mapping stack, stays inside the "piggyback on existing nav apps" strategy | Requires one extra manual user action (share, not just start nav) |
| **2. Own destination entry (search/geocoding inside MotoNav)** | Bypass Gmaps/Waze for destination selection entirely; use our own geocoding (e.g. Nominatim/OSM, matching Beeline's base map choice) | Full control, no dependency on share flow | Turns MotoNav into a mini maps app — direct scope conflict with the Phase 1 PRD's explicit non-goal ("no turn-by-turn routing of our own") |
| **3. Reverse-geocode the parsed street name** | Convert `streetName` text into an approximate coordinate via a geocoding API call | Cheap to add, no new user step | Imprecise (street-level, not exact destination), adds external API dependency and a network call per new street name |

Recommendation: Option 1 fits the project's established "read, don't rebuild" philosophy and requires no new mapping/routing dependency — closest match to how MotoNav already operates. Options 2 and 3 both reopen scope decisions closed in the Phase 1 PRD.

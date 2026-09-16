# Research Notes

Findings gathered during PRD development, kept here so they don't only live in chat history. See the PRD's Open Questions and Prior Art sections for the resolved/actionable summary — this file has the raw source list for reference.

## Notification parsing libraries / prior art

- **`3v1n0/GMapsParser`** (github.com/3v1n0/GMapsParser) — Kotlin library, LGPL-3.0, on JitPack (`com.github.3v1n0.GMapsParser:navparser:master-SNAPSHOT`). Parses Google Maps navigation notifications into a structured `NavigationData` object: `nextDirection` (localeString + localeHtml + nested distance), `remainingDistance`, `eta`, `actionIcon` (Bitmap of Maps' own turn icon), `isRerouting`, `finalDirection`. No separate street-name field — it's embedded in `nextDirection.localeString`. **Recommended as a direct dependency** rather than writing our own Maps parser.
- **`prusux/reNavigator`** — regex-style notification parsing across nav apps including Waze. Not yet reviewed in detail; next step for closing the Waze turn-by-turn open question.
- **`REDLINE`** (Arjun P., arjunp.pro/projects/suzuki-connect-re.html) — production app, same notification-listening architecture as this project, targeting a factory motorcycle Bluetooth dash instead of a phone screen. Validates the overall approach. Useful reference for Phase 2 protocol design methodology (JADX decompile + Frida hooking + Android HCI snoop log capture) if we ever need to interoperate with third-party hardware.

## Google Navigation SDK

- Confirmed via Google's own FAQ (developers.google.com/maps/documentation/navigation/android-sdk/faq): **no offline mode.** Pre-caches ~15-20 min of route data only. Ruled out as a data source for this project since offline riding (tunnels, dead zones) matters.
- Also has ToS restrictions against building a navigation "substitute" for public distribution — another reason it's not used here.

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

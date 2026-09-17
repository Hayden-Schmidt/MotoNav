# Dev log

Running log of decisions made outside the rebuild plan's own revision history —
things worth recording once, not re-litigating.

## 2026-09-18

- Build confirmed working (Phases A-E). UI is functional but rough and still expected to
  change shape — a proper UI overhaul is upcoming work, not yet scheduled.
- ESP32 hardware not yet in-country — Phase D (firmware) deliberately on hold until the
  phone-side UI stabilizes enough to be worth mirroring onto the puck.
- Fixture-based regression tests (rebuild plan §5.4) deliberately deferred until nav/UI
  logic stops churning — writing fixtures against logic that's still changing direction
  means rewriting them every time.
- Added `LICENSE` (MIT) for the app's own code. ODbL applies to the OSM data, not our code.
- ODbL attribution: confirmed the share-alike/"open the database" obligation only applies
  to Derivative Databases (we're a Produced Work, exempt). Attribution itself (ODbL §4.3)
  is a *separate* obligation that still applies to Produced Works — added
  "Map and routing data © OpenStreetMap contributors, ODbL" to the Settings screen's About
  section to satisfy it.
- FOSSGIS GitHub Discussions announcement (plan §5.3) — skipped for now, courtesy step only,
  revisit before any public release.
- **Speed limits:** NZTA's National Speed Limit Register confirmed as the data source —
  free, no approval needed, downloadable from Waka Kotahi's open data portal
  (https://opendata-nzta.opendata.arcgis.com/maps/NZTA::national-speed-limit-register-nslr/explore),
  licensed CC BY 4.0 (attribution required, no cost/gate). Plan: ship as a separate app-side
  layer per the rebuild plan §4, not routed through OSM. Scheduled as real dev work.
- **Twisty/"Fun" routing:** decided to use RideBeeline's own open-source curvature scorer
  (https://github.com/RideBeeline/curvature, forked from adamfranco/curvature) when this is
  picked up — computes a per-way curvature score from OSM geometry (circumcircle radius over
  consecutive point triples). Matches the approach the rebuild plan sketches doing manually
  via `trace_attributes`. Marked for later; not started.
- Next scheduled work: Phase C (offline routing) and Phase E (offline map) data artifacts —
  NZ Valhalla tile tarball and NZ PMTiles basemap cutout. Both need an OSM NZ extract pulled
  first; exact source/size to be confirmed with the user before downloading.
- **NZ Valhalla tile tarball built.** Source: Geofabrik NZ extract
  (`download.geofabrik.de/australia-oceania/new-zealand-latest.osm.pbf`, ~385 MB). Built via
  `ghcr.io/gis-ops/docker-valhalla/valhalla` run through Podman (build-only, dev-machine step,
  never runs on a user's device). Output: `nz_valhalla_tiles.tar`, 298.7 MB, 713 tiles, sitting
  in `build-data/` pending upload as a GitHub Release asset (Track 1b) and the in-app downloader
  (Track 1c) that reads it into `LocalTileFiles`'s expected path. Not yet side-loaded/tested on
  device.
- **NSLR speed-limit data fetched and inspected** (Track 2, data-only scope): 64,059 features as
  GeoJSON (`build-data/nslr_full.geojson`, ~1.1 GB raw — needs stripping down before any on-device
  use). Schema notes:
  - **Geometry is Polygon (zone areas), not line segments per road** — the lookup approach needs
    to be point-in-polygon against current GPS position, not nearest-edge matching. Revises the
    "nearest-segment" assumption from the rebuild plan.
  - `speedCategoryName` is one of `Permanent` / `Variable` / `Seasonal` — three real limit
    behaviors, not one. `Variable` zones carry a `speedLimitZoneVariableSpeed` field with multiple
    comma-separated values (e.g. school-zone limits that apply only at certain times) — no
    machine-readable time-window field observed alongside it, so applying `Variable` limits
    correctly needs more investigation (likely tied to `speedLimitZoneReasonName` values like
    "The presence of a school").
  - `whenEffective`/`whenIneffective` are epoch-millis — zones can be time-bounded independent of
    the seasonal/variable fields (legislated changes over time), so a naive "load once, use
    forever" import would go stale; needs a re-fetch/versioning story eventually, not just a
    one-time bundle.
  - `speedLimitZoneMaxValue`/`MinValue` are strings like `"60 km/h"` (unit embedded, needs
    parsing); `speedLimitZoneValue` is a bare number string.
  - Licence: CC BY 4.0 — needs its own attribution line (separate from the OSM/ODbL one) once
    wired into the app.
  - Open question carried forward, not decided: how to reduce 64k polygons + all this metadata
    down to whatever's actually needed on-device (likely just geometry + a resolved speed value
    per zone, precomputed for `Permanent` zones and handled separately for `Variable`/`Seasonal`)
    — real design work for the session that actually wires this into the dial.
- **In-app offline tile downloader shipped** (`ride/OfflineTileDownloader.kt`, wired into
  `SettingsScreen.kt` under `RouterBackend.LOCAL`) — replaces the adb-push workflow entirely.
  Plain HTTPS GET straight into `LocalTileFiles`'s expected path, `.part`-file-then-rename so an
  interrupted download can't leave a half-written tarball where Valhalla reads from. URL is
  hardcoded to the single hosted NZ release for now (see below); revisit as a setting once more
  than one region exists.
- **Repo published**: `Hayden-Schmidt/MotoNav`, now public. Found and fixed a broken branch
  state on push — `main` (the default branch) held two disconnected junk commits unrelated to the
  real project, while all actual history was sitting on `master`. Replaced `main`'s history with
  `master`'s, deleted the stray `master`, repo now has one clean default branch.
- **NZ tile tarball hosted** as a GitHub Release asset:
  https://github.com/Hayden-Schmidt/MotoNav/releases/tag/nz-tiles-2026-09-18 — direct asset URL
  baked into `OfflineTileDownloader`. Not yet tested end-to-end on a device (download → tilesReady
  → LOCAL routing → aeroplane-mode ride, per the Phase C exit test).
- **Two pre-existing build breaks found and fixed while verifying compile** (unrelated to today's
  changes, just never previously caught by a clean `compileDebugKotlin`):
  - Ferrostar `0.56.0` requires core library desugaring, which `app/build.gradle.kts` never
    enabled. Added `isCoreLibraryDesugaringEnabled = true` and `desugar_jdk_libs:2.1.5` (2.1.4
    wasn't new enough — Ferrostar pins a minimum).
  - `RideState.kt`'s `toRideState()` called `.toInt()` directly on
    `trip.currentStepGeometryIndex`, which is nullable in the Ferrostar version actually in use —
    changed to `?.toInt() ?: 0`.
- **First real device run, on the OnePlus 15 — found and fixed two more issues:**
  - **Crash on launch**: `RideSessionService.bleLink` was an eagerly-initialized field
    (`= BleLink(this)`), constructed before Android attaches the Service's Context (construction
    runs before `attachBaseContext`), so `getSystemService` hit a null base context. Same class
    of bug `fusedLocationClient` already avoided via `by lazy` — made `bleLink` lazy too.
  - **Destination search returning US/Malaysia results ahead of NZ ones**: `geocode()` sent no
    location bias to Photon at all. Added `lat`/`lon` query params biased to an NZ centroid
    (`-41.0, 174.0`) — a ranking hint, not a hard filter, matching the app's existing NZ-only scope
    (same reasoning as the hardcoded Albany test destination). Live-GPS biasing would be more
    precise but wasn't needed to fix the actual complaint.
- **Phase C exit test passed**: in-app tile download → `RouterBackend.LOCAL` → route resolved on
  the OnePlus 15 with the device in aeroplane mode. Offline routing chain (build → host → in-app
  download → on-device routing) confirmed working end-to-end.
- **Revised Phase C/E design:** don't hardcode "whole of NZ" as the offline region — Valhalla's
  routing graph can't do partial/on-demand tile streaming the way visual map tiles can (it needs
  a complete tile set for a bounded region up front), so offline routing is inherently
  region-bounded, not per-tile-cacheable. "Whole of NZ" only looked like the natural unit because
  NZ is small (~268,000 km²); it does not scale to Australia/US-sized regions as a default.
  Correct design: a user-selectable bbox region (state/island/custom trip area), with NZ as the
  first default, driving the Valhalla tile-build.
- **Phase E (offline PMTiles basemap) deferred, likely dropped:** the visual basemap only ever
  renders on the pre-ride destination-picker screen — the live nav dial is a custom-drawn
  arrow/schematic, not a map render, so it does zero work during the actual ride. Destination
  entry works fine via text search alone (Photon), which doesn't need a visual map. No reason to
  build/ship an offline basemap cutout for the off-grid use case at all; revisit only if
  search-only destination entry proves annoying in practice. Narrows "next work" to just the
  Valhalla routing-tile region picker — that's the only piece offline riding actually depends on.
- **No live traffic data** in this stack (OSM/Valhalla static tiles have no congestion feed) —
  no traffic-aware ETA, no traffic-triggered rerouting, only route-deviation rerouting. Real
  gap vs. the old Google Navigation SDK approach, not previously called out in the rebuild plan
  alongside speed limits/twisty routing. Matters more in cities than on the backcountry rides this
  app targets; noted as an accepted open gap, not scheduled.

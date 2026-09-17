# MotoNav UI Spec — Round Dial (ESP32 + Phone)

**Status:** Draft v1 — scoping pass, not yet approved to build
**Scope:** Phase 1 UI (Google Maps only, phone screen) designed dial-first so it ports to Phase 2 (ESP32 round display) with no rework. Informed by the Beeline Moto II case study (`docs/RESEARCH_NOTES.md`) and the screen references in `UI-Guidelines/`.

---

## 1. Data inventory — what actually drives these screens

This spec is scoped strictly to what we can source today, with speculative fields called out separately so no screen design silently assumes data we don't have.

### Confirmed available now (from `NavState`, notification-listening)

| Field | Source | Reliability |
|---|---|---|
| `maneuverType` | Keyword-derived from Google Maps notification text | Best-effort — English keyword matching, no route geometry |
| `distanceToTurnMeters` | Google Maps notification | Direct from source app |
| `streetName` | Regex-extracted from maneuver text (`onto/on <street>` pattern) | Partial — null on roundabout/arrive/reroute text |
| `etaText` | Google Maps notification | Direct from source app |
| `remainingDistanceMeters` | Google Maps notification | Direct from source app |
| `isRerouting` | Google Maps notification | Direct from source app |
| `sourceApp` | Which parser matched | Direct (always `GOOGLE_MAPS` in Phase 1 scope) |
| `lastUpdated` | Timestamp on every `NavState` emission | Direct — this is what staleness detection is built on |

**Not available from notifications, confirmed:** route polyline/geometry, destination coordinates, turn angle (only a bucketed type), traffic data, speed limit.

### Speculative / planned, not yet implemented

| Field | Planned source | Status |
|---|---|---|
| Current speed | Phone's own GPS (`FusedLocationProvider`), independent of notification data | Not wired up — no location code exists in the app yet. Needs its own permission + battery-cost tradeoff decision before it's real. |
| Current heading | Phone's own magnetometer | Not wired up. Only needed for compass mode (Phase 3). |
| Destination coordinates | Android share-intent capture (Option 1 from the Phase 3 gap analysis in `docs/RESEARCH_NOTES.md`) | Not implemented. Blocks compass mode entirely until built. |
| Device battery % | ESP32 hardware, once it exists | Phase 2, hardware not built |
| BLE link/signal state | ESP32 hardware, once it exists | Phase 2, hardware not built |
| Waze fields | Waze notification parser | Not built — explicit Phase 1 non-goal per the PRD |

This spec only designs screens for the confirmed-available column, plus one clearly-marked reserved slot for compass mode so the phone UI doesn't need restructuring when Phase 3 lands.

---

## 2. Dial constraints (ESP32-first)

No ESP32 round-display hardware has been selected yet — this is an open item, not settled by this spec. For layout math this spec assumes a square-ish round LCD module in the size class commonly used for this kind of build (roughly 300–420px diameter, similar to what Beeline's own puck uses), since the layout principles below hold regardless of exact pixel count. Confirm actual panel resolution before implementing pixel-exact Compose/embedded layouts.

Because we have maneuver **type**, not maneuver **geometry**, we cannot draw Beeline's schematic route-line sketch (their `blk_nav.png` shows the actual upcoming road shape). Our dial substitutes a large centered directional icon (reusing the existing `ManeuverType.icon()` set already in the codebase) — this is a deliberate, data-driven departure from the Beeline reference, not an oversight.

---

## 3. Screen inventory

Five states, driven directly by `NavState` (or its absence):

### 3.1 Idle — no active navigation
`NavState` is `null`.
- Dial: dim/low-contrast. Center: app glyph or a simple "—" placeholder. No arrow, no numbers — nothing to show yet.
- Purpose: confirms the app/device is alive and listening without competing for attention when there's nothing to navigate.

### 3.2 Active turn-by-turn (Route mode)
`NavState` present, `isRerouting == false`, data fresh (see staleness rule below).
- **Top zone (~55% of dial):** large centered directional icon from `ManeuverType.icon()`. Fixed-position, non-rotating — we don't have heading data to justify a compass-rotating treatment, so the icon just displays the upcoming maneuver type plainly (this mirrors Beeline's fixed-arrow convention but is simpler since we have no route line to rotate under it).
- **Bottom zone (~35% of dial):** `distanceToTurnMeters`, large numerals, unit below (m/km) — same numeral-dominant treatment as Beeline's `blk_nav.png`.
- **Outer ring:** solid, full-brightness stroke = data is fresh. This is the dial's only "chrome."
- Street name is **not** shown on the dial (no room, and it's frequently null) — reserved for the phone's rectangular extra-metrics area (§4).

### 3.3 Rerouting
`NavState.isRerouting == true`.
- Same layout as 3.2, but outer ring switches to a dashed/pulsing stroke instead of solid — direct reuse of the solid-vs-dotted convention documented from Beeline's `blk_rerouting_*.png` in `UI-Guidelines/README.md`, repurposed here to mean "Maps itself is recalculating" rather than "our data is stale" (see 3.4 for that distinct case).
- Directional icon dims or is replaced with a simple recalculating glyph (spinner-style, no directional claim while the source app doesn't have one either).

### 3.4 Stale data (new state — not present in Beeline, needed because of our architecture)
Derived, not sourced directly: `now - lastUpdated` exceeds a threshold (exact value TBD during Phase 1 build/testing — start around 5–8 seconds given the PRD's 2-second update-latency target, tune from real ride data).
- This state exists because our data pipeline can silently stop updating (notification dropped, listener killed, Maps backgrounded) in a way Beeline's own first-party pipeline can't. Beeline never needed this screen; we do.
- Dial: last-known icon/distance shown at reduced opacity, outer ring turns to a distinct broken/hazard pattern (visually different from the rerouting dash so a rider can tell "Maps is rerouting" apart from "MotoNav lost the feed").
- This is the single highest-priority *new* design decision in this spec — it's the biggest architecture-driven UX gap versus the commercial product we're referencing.

### 3.5 Arrived
`maneuverType == ARRIVE`.
- Center: flag/checkmark glyph (already has an icon mapped — `Icons.Filled.Flag`), no distance countdown.
- Same "confirm/done" visual weight Beeline uses on their ride-summary and waypoint-skip screens (single dominant glyph, no competing numerals).

### Reserved, not built in Phase 1
**Compass mode slot** — per §1, blocked on destination-coordinate capture. When Phase 3 ships it, it becomes a 6th state reusing the exact layout Beeline uses (`blk_Journey_tracking.png`: bearing readout + tumbler-style distance, no maneuver icon at all). Noting it here now so the phone screen's mode-switching affordance (§4) can reserve a slot rather than being retrofitted later.

---

## 4. Phone screen: same round puck + extra metrics

The phone screen is **not a different design** — it's the identical round dial from §3, centered on the rectangular canvas, plus a metrics band using the extra screen real estate the ESP32 won't have. This keeps the two surfaces visually consistent (useful for testing Phase 1 against what Phase 2 will eventually show) and avoids designing two unrelated UIs.

Layout, top to bottom:

1. **Status strip (top, small)** — source-app badge (Google Maps icon, small, non-dominant — matches the "one accent, one primary focus" principle from the Beeline case study) and a stale-data text label when 3.4 is active (dial alone may not be legible enough at a glance while riding; text backs it up).
2. **Round dial (center, dominant)** — exactly §3's states, same icon set, same layout ratios, scaled up to fill the available width.
3. **Metrics band (bottom)** — data the ESP32 dial has no room for, shown only here:
   - Street name (when available — null gracefully hidden, not shown as blank)
   - ETA text
   - Remaining distance
   - (Reserved row, not populated in Phase 1: current speed, once GPS is wired per §1 — leave layout space but do not fake the value)

This band is intentionally sparse in Phase 1 — it exists to prove out the layout pattern (dial + supplementary strip) before Phase 2 hardware exists, not to cram in everything we could theoretically show.

---

## 5. State → data → fallback summary

| State | Trigger | Dial content | Fallback if source field is null |
|---|---|---|---|
| Idle | `NavState == null` | Dim glyph, no numerals | N/A |
| Route mode | `NavState` present, not rerouting, fresh | Maneuver icon + distance | `maneuverType` defaults to `UNKNOWN` → generic straight-arrow icon, never a blank dial |
| Rerouting | `isRerouting == true` | Dashed ring + recalculating glyph | — |
| Stale | `now - lastUpdated` > threshold | Last-known state, dimmed, broken ring | — |
| Arrived | `maneuverType == ARRIVE` | Flag/checkmark | — |
| (Reserved) Compass | Not built — Phase 3 | Bearing + distance, no icon | Blocked entirely until destination-coordinate capture exists |

---

## 6. Open questions before implementation

1. Exact ESP32 round-panel part/resolution — needed to pin real pixel dimensions instead of the ~300–420px assumption above.
2. Staleness threshold — needs real on-road testing against the PRD's 2-second update-latency target, not a desk guess.
3. Whether "stale" should also trigger an audible/haptic alert on phone (round dial alone may go unnoticed mid-ride) — out of scope for this visual spec, flagged for the interaction-design pass.
4. Color treatment intentionally left open — this spec only fixes layout/hierarchy/state logic, not a palette, per the earlier steer that branding isn't the target.

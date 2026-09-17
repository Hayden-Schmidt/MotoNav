# UI Guidelines — Beeline Moto II reference set

Reference screenshots pulled from Beeline's App Store listing and beeline.co product page (Sept 2026), kept here purely for **app flow, screen layout, and icon usage** — not for branding/color replication (see `docs/RESEARCH_NOTES.md` for why exact brand colors were never a target). Used to inform MotoNav Phase 1 UI (single-arrow Google Maps view) and later phases (round display).

## Folder contents

### `beeline-app-store/` — phone app screens (App Store listing, full 1284×2778 res)

| File | Screen | Layout notes |
|---|---|---|
| `screenshot_5.png` | Route confirm screen (2 stacked device mockups showing route-style tabs Fast/Balanced/Quiet + Compass) | Top: back chevron + "Options" pill, top-right. Map fills upper ~55% of screen. Below map: mode toggle row ("One way" / "Generate: X km"), then a white card: Start/Via/End rows with pin icons, then route-style segmented control (4th option is literally **"Compass"** — confirms dead-reckoning mode is a peer option to Fast/Fun/Quiet, not a separate app mode), then stat row (distance / time / elevation icons), then filter chips (bike-lane %, tolls, etc.), then 3-button footer: **More info / Save / Go** (Go is the only filled/colored button — clear visual hierarchy for the primary action). |
| `screenshot_6.png` | Same route-confirm screen, round-trip variant with 4 numbered waypoint pins on map | Confirms numbered pin convention (1/2/3/4) for multi-stop routes, color-coded (orange = via stop, green = final stop, dark = start/end). Right-edge floating icons: reorder (up/down arrows) and add-stop (list+ icon), stacked vertically next to the Start/Via/End card — good pattern for keeping route-editing controls out of the main card. |
| `screenshot_7.png` | **Full-screen active navigation (phone-only turn-by-turn)** | Top black bar: turn arrow icon + distance ("73 m") + gear/settings icon, right-aligned. Below that, two floating circular buttons: "Arrow" (compass toggle) top-left, speaker/mute top-right — both floating over the map, not in a toolbar. Map is 3D-tilted with building extrusion. Bottom black bar: ETA + time on left, distance-remaining on right, big circular stop button bottom-right. This is the single most useful reference for our Phase 1 full-screen nav layout. |
| `screenshot_8.png` | Ride history / Journeys tab | Bottom tab bar: Ride / Journeys / Settings (3 tabs, icon + label). Journeys list uses a small route-thumbnail sketch (not a map) + title + date/time + duration + distance per row. Not directly relevant to Phase 1 but useful if we ever add ride logging. |

### `beeline-co-device-screens/` — round display renders (actual product screens, transparent bg)

These are the single most useful asset for Phase 2 (ESP32 display) — each one is a distinct device *state*, not a mockup:

| File | State | Layout notes |
|---|---|---|
| `blk_nav.png` | Turn-by-turn, upcoming turn 300m away | Top ~55%: mini route line (schematic, not a real map) with current-position arrow fixed near bottom-center, always pointing up (map rotates under it, not the arrow). Bottom ~35%: large turn icon + distance, dark banner strip. Speed-limit sign (red circle) floats top-right, overlapping the map area. |
| `blk_speedo_...png` | Speedometer state | Circular gauge ring (white ticks) sweeping ~270°, red dot marks current-speed position on the ring. Center: speed-limit sign + big current-speed number + unit. Bottom: pill-shaped secondary readout (average speed). Single-glance hierarchy: biggest text = current speed. |
| `blk_journey_eta.png` | Trip ETA/stats state | White accent ring around the whole display (this is a different visual treatment from nav mode's plain black bg) — likely denotes "info/stats" mode vs "active guidance" mode. Big yellow duration text top, two-column white sub-stats (departure/arrival time) with icon labels beneath, distance at bottom, small pin+dot icon at very bottom (page-position indicator, see next row). |
| `blk_Journey_tracking.png` | **Compass mode** — bearing + distance, no turns | Red triangle fixed at top of bezel = "north/heading" reference marker, NOT the direction-of-travel arrow. Big degree+cardinal readout ("330° NW") dominates center. Below: 3-digit odometer-style tumbler distance readout + unit. Clock at bottom. Two small dots bottom-center = page/screen indicator (cycling between multiple info screens via button press). **This is the literal dead-reckoning screen** — no map, no maneuver icon, just heading + distance, confirming the architecture conclusion in RESEARCH_NOTES.md. |
| `blk_ride_summary.png` | Post-ride summary | 3 stacked rows (distance / duration / avg speed), each icon+value, then a big yellow checkmark button at bottom — single clear "done" affordance. |
| `blk_strart_ride_from_device.png` | Idle / pre-ride home screen | Minimal: one outlined circle-dot icon + "Start ride" label, center-left; a ">" swipe-forward affordance icon on the right edge (cycles to other idle-state screens — battery, brightness, etc., per the meetmobility review notes in RESEARCH_NOTES.md). |
| `blk_rerouting_...png` | Rerouting / next-turn-after-current | Dotted-line path (vs. solid line in `blk_nav.png`) from current-position arrow up to the next maneuver icon — dotted appears to mean "provisional/recalculating" vs. solid "confirmed route." Useful distinction to reuse: dotted = uncertain, solid = confirmed. |
| `blk_skip_waypoiny_...png` | Waypoint skip/confirm prompt | "1/3 waypoints" counter, left/right chevron arrows either side (physical button mapping — matches the 4-button bezel), waypoint icon top, yellow checkmark confirm button bottom. Same checkmark-button pattern as ride_summary — consistent "confirm/dismiss" affordance across screens. |
| `Moto_II_BLK_Nav.png`, `Moto_II_GMG_Nav.png`, `Moto_II_SVR_Nav.png` | Device-on-bike product photos, 3 color variants, all showing the nav screen live | Context shots only — mount position (top of yoke/triple clamp), viewing angle from rider's seated position. |

### `beeline-co-app-screens/` — phone app, in-context (on-bike) photos

| File | Content |
|---|---|
| `moto_app_1.webp` | Route-confirm screen full view, matches `screenshot_5`/`6` layout, phone held in hand near handlebar mount |
| `moto_app_3.webp` | **Same full-screen turn-by-turn layout as `screenshot_7`**, shown alongside the round display mounted on the yoke — good side-by-side reference for how phone-screen and device-screen states correspond during the same ride moment (phone shows full map + arrow, device shows just the schematic turn) |
| `moto_app_4.webp` | Route-confirm screen, round-trip, device visible mounted top of triple clamp for scale/position reference |
| `moto_app_planning.webp` | Destination search screen: back chevron, End-field with entered text, current-location row pinned above results, list of matched addresses (pin icon + name + subtitle + distance-away), native iOS keyboard docked at bottom. Standard search-then-select pattern. |

## Key layout takeaways for MotoNav Phase 1 (single arrow, Google Maps only)

1. **Two-zone screen split**: map/schematic occupies the top majority, a dark info band anchors the bottom third with distance/ETA — consistent across both phone (`screenshot_7`) and device (`blk_nav.png`) layouts. Worth carrying into our Compose full-screen nav view.
2. **Fixed-arrow, rotating-world convention** on the device (`blk_nav.png`): the arrow never moves, the route line rotates under it. Simpler to implement than a rotating arrow icon and matches Beeline's approach exactly.
3. **Solid vs. dotted route line** distinguishes confirmed vs. rerouting state — a cheap visual signal worth copying for our own reroute/stale-data indicator (relevant since our notification data can lag or drop).
4. **One dominant color accent** (yellow, in Beeline's case) reserved for the single primary action per screen (Go, checkmark, confirm) — everything else stays monochrome. Doesn't require us to use yellow; the *pattern* (one accent color, one primary action) is the transferable part.
5. **Compass/dead-reckoning is a route-style tab, not a separate app mode** — sits alongside Fast/Fun/Quiet in the same segmented control. If we build Phase 3 dual-mode (per the destination-coordinate gap analysis in `docs/RESEARCH_NOTES.md`), this is a clean UI slot to copy: one more segmented-control option, not a parallel UI.

No hex colors or typefaces extracted from these assets (out of scope per your steer — flow/layout/icons only, not branding).

# MotoNav — Phase 1 PRD (Android Companion Navigation Display)

**Status:** Final v1 — approved to build
**Working title:** MotoNav (rename after POC — branding pass planned)
**Phase:** 1 of 3 (Android-only POC → ESP32 display → multi-rider pairing)

---

## Problem Statement

Motorcyclists using Google Maps or Waze on a phone mount get a UI designed for car dashboards: small text, cluttered map view, and information that's hard to read at a glance while riding. There's no lightweight, glanceable, motorcycle-specific navigation display that rides on top of the navigation apps riders already trust and already have routes/traffic data for — commercial options (e.g. Beeline Moto) exist but are closed hardware/software with no room for customization.

Riders currently either squint at a full map UI mounted awkwardly on the bars, or use a phone mount and accept the readability compromise. This is a personal/hobby build to fix that for the builder (Hayden) first, with an eye toward it being usable by other riders later.

## Goals

1. Produce a working Android app that displays a glanceable arrow + distance + street name + ETA/speed, driven by whatever Google Maps or Waze is already navigating.
2. Prove the notification-listener data pipeline is reliable enough for real riding (not just desk testing) — target: correctly reflects the active maneuver within 2 seconds of the source app updating it, no missed turns across a 30+ minute test ride.
3. Validate the auto-launch and background power model doesn't meaningfully drain battery when not riding, and reliably activates when riding starts.
4. Ship internally testable via Play Console's closed testing track so it can be installed without a USB cable / manual APK sideload once past first build.
5. Establish a clean internal data interface (parsed maneuver: direction, distance, street name, ETA) that Phase 2 (ESP32 BLE output) can consume without rework.

## Non-Goals (Phase 1)

- **No ESP32/hardware output.** That's Phase 2. Phase 1 is phone-screen-only.
- **No multi-rider pairing/binding.** That's Phase 3, and only relevant once hardware exists.
- **No turn-by-turn routing of our own.** We never calculate routes — we only read and redisplay what Maps/Waze is already doing. No offline map data, no rerouting logic.
- **No support for nav apps beyond Google Maps and Waze in Phase 1.** A generic/other-app fallback parser is explicitly deferred — parsing two well-known notification formats is already two integration surfaces; adding a "guess any app" heuristic now is scope creep with low reliability payoff.
- **No public app store release.** Play Console *closed testing track* only for Phase 1 — not a production listing. Public release/branding is a later decision.

## Prior Art (validates this approach)

- **`REDLINE`** (Arjun P.) — production Android app for a motorcycle with a factory Bluetooth instrument cluster. Uses the exact same architecture as this PRD: intercepts Google Maps turn-by-turn notifications via `NotificationListenerService`, encodes maneuvers, and pushes them over BLE to a custom dashboard — no cloud, no account, phone-only. 14k lines, 205 tests, shipped and working. Confirms this is a proven pattern, not a novel risk. His protocol reverse-engineering writeup (decompile with JADX, hook with Frida, capture Android's Bluetooth HCI snoop log) is a useful reference for Phase 2's ESP32 protocol design if we ever need to interoperate with existing hardware rather than building our own receiver from scratch.
- **Beeline Moto** — commercial competitor, confirmed (via Pure Maps maintainer discussion) to use its own proprietary app + non-standardized BLE protocol, same as our plan. No industry-standard "phone-to-handlebar-nav" protocol exists; every product builds its own translation layer. Validates that our from-scratch protocol approach for Phase 2 isn't skipping a shortcut that exists elsewhere.
- **Chigee / Carpuride** — different category (CarPlay/Android Auto screen-mirroring boxes, not custom protocol receivers). Useful market context, not a technical precedent for this build.

## User Stories

**Primary user: Hayden, riding solo, phone in a bar/tank mount**
- As a rider, I want to see a large arrow showing my next turn direction so I don't have to interpret a small map at a glance.
- As a rider, I want to see the distance to my next turn counting down so I know when to prepare to turn.
- As a rider, I want to see the upcoming street name so I can confirm I'm about to take the right turn.
- As a rider, I want to see ETA and current speed so I have trip context without switching apps.
- As a rider, I want the app to launch itself when I connect my helmet/bike Bluetooth and start navigating, so I don't have to fumble with my phone before riding.
- As a rider, I want to control whether the screen stays on/unlocked, because I ride in different conditions (some rides use an external display later, some don't) and want that choice to persist as my default.
- As a rider, I want the app to *not* drain my battery when I'm not riding, so it's safe to leave installed and idle.

**Secondary (future, not Phase 1, informs architecture)**
- As a rider, I want to pair my own ESP32 display so I don't need to mount my phone at all. *(Phase 2)*
- As a rider in a group, I want my display to never accidentally connect to someone else's ESP32 unit. *(Phase 3)*

## Requirements

### Must-Have (P0)

**Notification capture & parsing**
- [ ] `NotificationListenerService` implemented and requests the required Android permission with a clear in-app explanation screen (not just the bare OS prompt).
- [ ] Parser for Google Maps navigation notifications extracting: maneuver/direction type, distance to next maneuver, upcoming street name, ETA, ETA distance remaining.
- [ ] Parser for Waze navigation notifications extracting the same fields.
- [ ] Parsers are isolated behind a common interface (e.g. `NavDataSource`) so source app is abstracted from the rest of the app — required for Phase 2 reuse.
- [ ] Graceful handling when a notification's fields don't parse cleanly (e.g. format changed) — app shows a "waiting for navigation data" state rather than crashing or showing stale/garbage data.

**Display UI**
- [ ] Full-screen glanceable layout: turn arrow (icon per maneuver type: straight, left, right, slight variants, U-turn, roundabout, arrive), distance-to-turn (large numerals), upcoming street name, ETA, remaining trip distance, current speed.
- [ ] High-contrast design suitable for outdoor daylight glare and glance-at-speed reading (no small text, no low-contrast color pairs).
- [ ] "Waiting for navigation" idle state shown when no active nav session is detected.

**Screen/power behavior**
- [ ] Settings option: Always-on / No always-on / Always-on-unless-external-display-connected.
- [ ] Selected setting persists as default across app restarts.
- [ ] Foreground service with wake lock is only active while the condition for the selected setting is met — not held constantly regardless of setting.

**Auto-launch**
- [ ] Settings option: Self-launch trigger = Off / On Bluetooth connect / On nav start / Bluetooth + nav start.
- [ ] "Bluetooth + nav start" logic: on nav-start detection, check current Bluetooth connection state; if the paired device is connected, launch app; if not, do nothing (leave for manual open).
- [ ] Selected trigger persists as default.
- [ ] Manual launch (tapping the app icon) always works regardless of auto-launch setting.

**Background power management**
- [ ] `NotificationListenerService` registration remains passive/idle when not riding — no polling.
- [ ] Bluetooth connection state monitored via system broadcast receiver (`ACTION_ACL_CONNECTED`/`DISCONNECTED`) for the paired device — no active/continuous BT scanning.
- [ ] Foreground service + wake lock + screen-on override are only held while the paired Bluetooth device is connected; torn down immediately on disconnect.
- [ ] Verify via battery stats that idle (BT disconnected, not riding) drain is negligible over a multi-hour test period.

**Distribution**
- [ ] App buildable and installable via Play Console closed testing track.
- [ ] Basic crash reporting/logging in place before first real-world test ride (so failures during testing are diagnosable).

**OEM notification-access variance**
- [ ] In-app permission setup screen deep-links to the correct OS settings page for notification access, with instructions; flagged as needing OEM-specific patches after initial testing (not a Phase 1 blocker beyond stock Android + OnePlus behavior).

### Nice-to-Have (P1)

- [ ] **Waze police/hazard alert display, best-effort via notification-listening.** Same mechanism as Waze turn-by-turn parsing (not the live-map API, which is confirmed blocked). Genuinely unconfirmed whether Waze posts these to the notification tray at all — implement alongside the turn-by-turn parser and test on-device; if alerts never arrive, degrade gracefully (feature silently absent) without affecting turn-by-turn reliability. Do not block Phase 1 exit criteria on this working.
- [ ] Manual override button to re-sync/refresh notification state if the app appears stuck (in case a notification update is missed).
- [ ] Basic ride log (start/stop time, distance) — not required for the core use case but low-cost given data is already flowing.
- [ ] Visual/haptic alert on upcoming turn within a configurable threshold (e.g. buzz/flash at 100m).
- [ ] Rename/rebrand pass (app name, icon) — explicitly deferred to post-POC per your answer, tracked here so it's not forgotten.

### Future Considerations (P2) — informs architecture, not built now

- [ ] BLE peripheral role for broadcasting parsed `NavDataSource` output to an ESP32 (Phase 2). Requirement here: keep the internal data model serialization-ready (simple struct/JSON, no UI-coupled types) so this is additive, not a rewrite.
- [ ] Multi-device pairing/binding with bonded-device confirmation ceremony (Phase 3).
- [ ] System notification (instead of full auto-launch) as an alternative auto-launch UX, flagged by you as a post-POC idea.
- [ ] Generic/fallback nav-app notification parser.
- [ ] ~~Waze backend-routing investigation~~ **DEAD END** — same `waze.com/live-map` domain, same reCAPTCHA Enterprise WAF confirmed blocking the georss endpoint above. Not worth testing separately; almost certainly blocked identically. Notification-listening (via `GMapsParser`-style parsing, once the Waze-specific parser is written) remains the only viable path for Waze turn-by-turn.
- [ ] **Live rotating arrow** (arrow continuously points at the actual upcoming turn as you approach, like Beeline Moto/Android Auto — vs. Phase 1's stepped arrow that snaps between discrete notification updates). Requires a different data pipeline than notification-listening: either the Google Navigation SDK (no offline mode — ruled out earlier) or your own GPS heading + extracted/estimated turn coordinates. Three sub-options to evaluate in Phase 2 planning, not decided yet:
  - Stay with stepped arrow indefinitely (simplest, matches current architecture).
  - Investigate whether turn coordinates can be extracted from notification data at all, before committing engineering time.
  - Add a basic GPS-heading compass overlay alongside the stepped arrow — partial smoothness without full route awareness.

## Success Metrics

**Leading (Phase 1 exit criteria — measured via your own test rides, not analytics infra)**
- Maneuver display updates within 2 seconds of the source notification changing, verified across at least 3 test rides.
- Zero missed turns (arrow fails to update before you reach the turn) across a 30+ minute continuous ride test.
- Auto-launch (BT+nav mode) fires correctly in at least 9/10 trigger attempts.
- Idle battery drain (BT disconnected, app installed, not riding) is not distinguishable from baseline phone drain over a multi-hour period.

**Lagging (once used regularly)**
- You choose to use MotoNav over the raw Maps/Waze UI on your next 5 rides after Phase 1 ships, without reverting.
- No crash-driven ride interruptions across the first month of casual use.

*(No formal analytics/telemetry is in scope for Phase 1 — this is a single-user POC. Metrics are evaluated manually by you.)*

## Open Questions

- ~~**[Engineering]** Does Google Maps' current notification payload expose street name as a distinct field?~~ **RESOLVED.** No — confirmed via `3v1n0/GMapsParser` (open-source, LGPL-3.0, available on JitPack): Google Maps notifications expose `nextDirection.localeString` (a combined string like "Turn right onto Main St"), `remainingDistance`, `eta`, and even the turn-arrow `actionIcon` as a Bitmap — but no separated street-name field. If we want street name as its own UI element, it needs string-parsing out of `localeString` ourselves. Recommendation: adopt `GMapsParser`'s `navparser` library directly as a dependency instead of writing a Maps parser from scratch — it already does the notification-extras extraction and structuring.
- **[Engineering]** Waze's notification structure for turn-by-turn — still open. No equivalent purpose-built library found; Waze notifications are known to be less structured than Maps'. Next step: inspect `prusux/reNavigator`'s Waze-handling logic (found during research, not yet reviewed) before falling back to manual on-device capture.
- ~~**[Engineering] Waze police/hazard alerts — can this be read from notifications?**~~ **RESOLVED — dead end, confirmed via live testing + community reports.** Not readable via notification-listening (confirmed earlier). The alternative — polling Waze's unofficial public live-map endpoint (`live-map/api/georss`) as used by prior-art projects `muke24/Alert-Detector` and `guberm/waze-alerts-monitor` — **has been blocked by Waze since March 2026.** Confirmed three ways: (1) our own live test from two different networks (sandbox + Hayden's phone on mobile data, real coordinates) both returned 403; (2) `guberm/waze-alerts-monitor`'s own issue tracker has an open, unresolved issue (#1, filed 19 Mar 2026): *"waze implemented a recaptcha enterprise WAF, requests to https://www.waze.com/live-map will result in 403"*; (3) independent web reports confirm Waze added reCAPTCHA Enterprise bot-protection to this endpoint in 2025/2026, and that it blocks even residential-IP requests — this is deliberate anti-scraping infrastructure, not a fixable header/IP issue.
  - **Conclusion: the public live-map API is a dead end** for this feature — it's blocked at the infrastructure level (reCAPTCHA Enterprise WAF), not a fixable header/IP issue.
  - Also forecloses the separate "Waze backend-routing" P2 lead (`/driving-directions`, `routesWithDirections`) noted below — same domain, same WAF, almost certainly blocked identically.
  - **Revised path: fall back to notification-listening, same mechanism as turn-by-turn, scoped as best-effort (P1, not P0).** Per earlier research, Waze police/hazard alerts are described everywhere as an interactive in-app popup ("Not There"/"Thanks" buttons), which strongly suggests a drawn overlay rather than a tray notification — meaning there's a real chance this still doesn't work even via notification-listening. This has **not been confirmed on-device either way** (unlike the live-map API, which we've now conclusively ruled out via live testing). Ship this as a best-effort attempt: implement a Waze alert-notification parser alongside the turn-by-turn one, with graceful degradation (silently show nothing, don't crash or block turn-by-turn) if alert notifications never arrive in the tray. Treat it as genuinely unresolved until tested on your device during the Waze turn-by-turn work.
  - If notification-listening also fails to surface alerts, the only remaining paths are a large step up in scope/risk: (a) Waze's authenticated mobile-app API (session-token extraction or full traffic reverse-engineering — fragile, ToS-hostile), or (b) an independent crowdsourced alert layer. Neither recommended for this project; noted only so the door isn't silently forgotten.
- **[Engineering]** Confirm `NotificationListenerService` behavior on OnePlus's OxygenOS specifically (battery optimization exemptions, background restrictions) before assuming stock-Android behavior holds.
- **[Design]** Exact arrow iconography/visual style — not specified yet. Needs a quick design pass before UI build (can reuse Material icons initially, refine later).
- **[Stakeholder/You]** Confirm minimum Android OS version to target (affects `NotificationListenerService` API behavior and foreground service restrictions, which have tightened in recent Android versions).

## Timeline Considerations

- No hard external deadline — personal project, paced by your availability.
- Suggested internal phasing within Phase 1 itself (to de-risk early):
  1. Spike: capture and inspect real Waze notification payloads on-device (Maps side already resolved via `GMapsParser` research — no spike needed there). This single spike also answers the still-open Waze turn-by-turn structure question and the best-effort police-alert question at the same time, since both are read from the same notification capture.
  2. Build Maps parser (adopt `GMapsParser`'s `navparser` library) + basic UI, test standalone (no auto-launch/power logic yet).
  3. Add Waze parser, informed by step 1's capture.
  4. Add settings (screen behavior, auto-launch modes) and background power management.
  5. Play Console closed-track distribution setup.
  6. Real-world test rides against exit criteria above.
- Phase 2 (ESP32) and Phase 3 (multi-rider pairing) are explicitly sequenced after Phase 1 exit criteria are met — not started in parallel.

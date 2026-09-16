---
name: project-conventions
description: Non-obvious constraints for MotoNav's Android/Kotlin code — background knowledge to apply automatically when touching source
user-invocable: false
---

# MotoNav conventions

- `NavState` (`NavDataSource.kt`) must stay serialization-ready: simple types only, no UI-coupled classes. Phase 2 broadcasts this same shape over BLE to an ESP32 receiver — don't add fields or types that can't cross that boundary.
- `minSdk = 26` is pinned to `NotificationListenerService` availability, not arbitrary — don't lower it without checking the PRD's open questions.
- Google Maps parsing should delegate to the `GMapsParser`/`navparser` dependency rather than hand-rolling notification-extras parsing (see `docs/RESEARCH_NOTES.md`).
- Waze has no equivalent parsing library — any Waze `NavDataParser` implementation needs an on-device notification capture spike first (see PRD Open Questions / Timeline step 1).
- Full rationale for architectural decisions lives in `docs/MotoNav_PRD_Phase1.md`; check it before deviating from the documented Phase 1 architecture.

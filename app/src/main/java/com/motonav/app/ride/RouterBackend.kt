package com.motonav.app.ride

// User-configurable from day one, not hardcoded — FOSSGIS explicitly asks published apps not to
// hardcode their service URL, and a hardcoded shared endpoint is the thing that kills a forkable
// project. See docs/MotoNav_REBUILD_PLAN_OSM.md Phase A item 4. LOCAL (Phase C) routes entirely
// on-device against side-loaded tiles — see ride/LocalRouteProvider.kt — and has no endpoint URL.
enum class RouterBackend { FOSSGIS, STADIA_MAPS, LOCAL }

// FOSSGIS (valhalla1.openstreetmap.de): full planet, fair-use, needs no key. Stadia Maps free tier
// needs a user-supplied API key (200,000 credits/month, no card) — never shipped in the repo, see
// plan §5.1. Never called for LOCAL — RideSessionService branches on RouterBackend before
// reaching this, see initializeFerrostarCore().
fun RouterBackend.endpointUrl(apiKey: String): String = when (this) {
    RouterBackend.FOSSGIS -> "https://valhalla1.openstreetmap.de/route"
    RouterBackend.STADIA_MAPS -> "https://api.stadiamaps.com/route/v1?api_key=$apiKey"
    RouterBackend.LOCAL -> error("RouterBackend.LOCAL routes on-device; it has no HTTP endpoint")
}

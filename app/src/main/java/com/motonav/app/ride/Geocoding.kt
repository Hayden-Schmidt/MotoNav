package com.motonav.app.ride

import com.motonav.app.nav.GeocodeResult
import com.motonav.app.nav.parsePhotonResults
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Phase E item 1 — geocoding via a configurable Photon-compatible endpoint, defaulting to
// Photon's own public demo server: fair-use, no key, "extensive usage will be throttled or
// completely banned" per its own docs — fine today, a liability once the repo has traction (plan
// Phase E item 1). Configurable exactly like RouterBackend.endpointUrl — never hardcode a shared
// endpoint, see plan §5.1. Self-hosting Photon (~95GB planet DB) is explicitly out of scope.
const val DEFAULT_GEOCODER_BASE_URL = "https://photon.komoot.io"

/** [baseUrl] is the server root (e.g. "https://photon.komoot.io"); this appends Photon's own API path. */
suspend fun geocode(client: OkHttpClient, baseUrl: String, query: String): List<GeocodeResult> =
    withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url = "${baseUrl.trimEnd('/')}/api/?q=${URLEncoder.encode(query, "UTF-8")}&limit=8"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) emptyList() else parsePhotonResults(body)
        }
    }

package com.motonav.app.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class GeocodingTest {

    // A trimmed real Photon response shape (fields reordered/renamed nowhere — this is what
    // https://photon.komoot.io/api/?q=Albany actually returns, minus the fields we don't use).
    private val sampleResponse = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[174.7000,-36.7290]},
           "properties":{"osm_id":1,"name":"Westfield Albany","city":"Auckland","country":"New Zealand"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[-73.7997,42.6526]},
           "properties":{"osm_id":2,"name":"Albany","country":"United States"}}
        ]}
    """.trimIndent()

    @Test
    fun `parses every feature's coordinates and label`() {
        val results = parsePhotonResults(sampleResponse)
        assertEquals(2, results.size)
        assertEquals(GeocodeResult("Westfield Albany, Auckland, New Zealand", -36.7290, 174.7000), results[0])
        assertEquals(GeocodeResult("Albany, United States", 42.6526, -73.7997), results[1])
    }

    @Test
    fun `no features parses to an empty list, not a crash`() {
        assertEquals(emptyList<GeocodeResult>(), parsePhotonResults("""{"type":"FeatureCollection","features":[]}"""))
    }

    @Test
    fun `a feature with no usable name is skipped`() {
        val response = """
            {"features":[{"type":"Feature","geometry":{"coordinates":[1.0,2.0]},"properties":{}}]}
        """.trimIndent()
        assertEquals(emptyList<GeocodeResult>(), parsePhotonResults(response))
    }
}

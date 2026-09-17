package com.motonav.app.nav

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate

class ElevationTest {

    @Test
    fun `height request json carries every shape point`() {
        val json = buildValhallaHeightRequestJson(
            listOf(GeographicCoordinate(-36.85, 174.76), GeographicCoordinate(-36.86, 174.77)),
        )
        assertEquals(
            "{\"shape\":[{\"lat\":-36.85,\"lon\":174.76},{\"lat\":-36.86,\"lon\":174.77}],\"range\":false}",
            json,
        )
    }

    @Test
    fun `heights parse in order, nulls preserved for uncovered points`() {
        val response = "{\"shape\":[{\"lat\":1.0,\"lon\":2.0}],\"height\":[12.3, null, -4.5]}"
        assertEquals(listOf(12.3, null, -4.5), parseValhallaHeights(response))
    }

    @Test
    fun `missing height section parses to an empty list, not a crash`() {
        assertEquals(emptyList<Double?>(), parseValhallaHeights("{\"shape\":[]}"))
    }
}

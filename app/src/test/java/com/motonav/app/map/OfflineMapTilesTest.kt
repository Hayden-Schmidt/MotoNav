package com.motonav.app.map

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapTilesTest {

    @Test
    fun `pmtiles url uses the file scheme, not the unsupported asset scheme`() {
        val url = pmtilesUrlForFile(File("/data/data/com.motonav.app/files/nz.pmtiles"))
        assertEquals("pmtiles://file:///data/data/com.motonav.app/files/nz.pmtiles", url)
        assertTrue(url.startsWith("pmtiles://file://"))
    }

    @Test
    fun `style json embeds the exact source url and references the pmtiles vector source`() {
        val style = styleJsonForSource("pmtiles://file:///x/nz.pmtiles")
        assertTrue(style.contains("\"url\": \"pmtiles://file:///x/nz.pmtiles\""))
        assertTrue(style.contains("\"type\": \"vector\""))
    }
}

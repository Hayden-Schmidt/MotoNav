package com.motonav.app.notification

import org.junit.Assert.assertNotNull
import org.junit.Test

class ManeuverIconTest {

    @Test
    fun `every maneuver type maps to an icon`() {
        ManeuverType.entries.forEach { type ->
            assertNotNull("missing icon for $type", type.icon())
        }
    }
}

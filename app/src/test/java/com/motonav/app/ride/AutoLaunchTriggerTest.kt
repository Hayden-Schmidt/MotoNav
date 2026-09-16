package com.motonav.app.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoLaunchTriggerTest {

    @Test
    fun `off never launches`() {
        assertEquals(false, shouldLaunch(AutoLaunchMode.OFF, TriggerEvent.BT_CONNECTED, btConnected = true))
        assertEquals(false, shouldLaunch(AutoLaunchMode.OFF, TriggerEvent.NAV_START, btConnected = true))
    }

    @Test
    fun `bt_connect mode only reacts to bt connect event`() {
        assertEquals(true, shouldLaunch(AutoLaunchMode.BT_CONNECT, TriggerEvent.BT_CONNECTED, btConnected = true))
        assertEquals(false, shouldLaunch(AutoLaunchMode.BT_CONNECT, TriggerEvent.NAV_START, btConnected = true))
    }

    @Test
    fun `nav_start mode only reacts to nav start event, regardless of bt state`() {
        assertEquals(true, shouldLaunch(AutoLaunchMode.NAV_START, TriggerEvent.NAV_START, btConnected = false))
        assertEquals(false, shouldLaunch(AutoLaunchMode.NAV_START, TriggerEvent.BT_CONNECTED, btConnected = true))
    }

    @Test
    fun `bt_and_nav_start requires nav start event with bt already connected`() {
        assertEquals(true, shouldLaunch(AutoLaunchMode.BT_AND_NAV_START, TriggerEvent.NAV_START, btConnected = true))
        assertEquals(false, shouldLaunch(AutoLaunchMode.BT_AND_NAV_START, TriggerEvent.NAV_START, btConnected = false))
        assertEquals(false, shouldLaunch(AutoLaunchMode.BT_AND_NAV_START, TriggerEvent.BT_CONNECTED, btConnected = true))
    }
}

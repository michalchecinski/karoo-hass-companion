package com.karoohass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionSemanticsTest {
    @Test fun `action mapping only exposes supported Home Assistant services`() {
        assertEquals("turn_on", ActionKind.RUN_SCRIPT.serviceName())
        assertEquals("lock", ActionKind.LOCK.serviceName())
        assertEquals("unlock", ActionKind.UNLOCK.serviceName())
        assertEquals("open_cover", ActionKind.OPEN_COVER.serviceName())
        assertEquals("close_cover", ActionKind.CLOSE_COVER.serviceName())
        assertEquals("stop_cover", ActionKind.STOP_COVER.serviceName())
        assertEquals("toggle", ActionKind.TOGGLE.serviceName())
        assertEquals("turn_on", ActionKind.TURN_ON.serviceName())
        assertEquals("turn_off", ActionKind.TURN_OFF.serviceName())
    }

    @Test fun `only stateful actions have a provable expected final state`() {
        assertEquals("locked", ActionKind.LOCK.expectedState())
        assertEquals("unlocked", ActionKind.UNLOCK.expectedState())
        assertEquals("open", ActionKind.OPEN_COVER.expectedState())
        assertEquals("closed", ActionKind.CLOSE_COVER.expectedState())
        assertEquals("on", ActionKind.TURN_ON.expectedState())
        assertEquals("off", ActionKind.TURN_OFF.expectedState())
        assertNull(ActionKind.RUN_SCRIPT.expectedState())
        assertNull(ActionKind.STOP_COVER.expectedState())
        assertNull(ActionKind.TOGGLE.expectedState())
    }
}

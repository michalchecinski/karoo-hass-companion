package com.karoohass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionSemanticsTest {
    @Test fun `lock entity exposes one state-aware control`() {
        assertEquals(listOf(ActionKind.CONTROL_LOCK), snapshot("lock.front_door", "locked").availableActionKinds())
    }

    @Test fun `locked resolves to confirmed unlock`() {
        val resolved = action(ActionKind.CONTROL_LOCK, "lock.front_door").resolve(snapshot("lock.front_door", "locked"))!!

        assertEquals("unlock", resolved.serviceName)
        assertEquals("Unlock", resolved.operationLabel)
        assertEquals("unlocked", resolved.expectedState)
        assertTrue(resolved.requiresConfirmation)
    }

    @Test fun `unlocked and open locks resolve to lock`() {
        listOf("unlocked", "open").forEach { state ->
            val resolved = action(ActionKind.CONTROL_LOCK, "lock.front_door").resolve(snapshot("lock.front_door", state))!!
            assertEquals("lock", resolved.serviceName)
            assertEquals("locked", resolved.expectedState)
            assertFalse(resolved.requiresConfirmation)
        }
    }

    @Test fun `configured confirmation also protects lock`() {
        val resolved = action(ActionKind.CONTROL_LOCK, "lock.front_door", confirm = true).resolve(snapshot("lock.front_door", "unlocked"))!!

        assertTrue(resolved.requiresConfirmation)
    }

    @Test fun `transitional jammed and unavailable locks do not resolve`() {
        listOf("locking", "unlocking", "opening", "jammed", "unknown", "unavailable").forEach { state ->
            assertNull(action(ActionKind.CONTROL_LOCK, "lock.front_door").resolve(snapshot("lock.front_door", state)))
        }
    }

    @Test fun `cover exposes one control when it has a directional feature`() {
        assertEquals(
            listOf(ActionKind.CONTROL_COVER),
            snapshot("cover.garage", "closed", features = COVER_OPEN or COVER_STOP).availableActionKinds(),
        )
        assertEquals(emptyList<ActionKind>(), snapshot("cover.read_only", "closed").availableActionKinds())
    }

    @Test fun `closed and open covers resolve using supported features`() {
        val control = action(ActionKind.CONTROL_COVER, "cover.garage")
        val open = control.resolve(snapshot("cover.garage", "closed", features = COVER_OPEN or COVER_CLOSE))!!
        val close = control.resolve(snapshot("cover.garage", "open", features = COVER_OPEN or COVER_CLOSE))!!

        assertEquals("open_cover", open.serviceName)
        assertEquals("open", open.expectedState)
        assertTrue(open.requiresConfirmation)
        assertEquals("close_cover", close.serviceName)
        assertEquals("closed", close.expectedState)
        assertFalse(close.requiresConfirmation)
    }

    @Test fun `moving cover resolves to request-only stop when supported`() {
        listOf("opening", "closing").forEach { state ->
            val resolved = action(ActionKind.CONTROL_COVER, "cover.garage").resolve(snapshot("cover.garage", state, features = COVER_STOP))!!
            assertEquals("stop_cover", resolved.serviceName)
            assertNull(resolved.expectedState)
            assertTrue(resolved.refreshAfterRequest)
            assertFalse(resolved.requiresConfirmation)
        }
    }

    @Test fun `configured confirmation protects close and stop`() {
        val control = action(ActionKind.CONTROL_COVER, "cover.garage", confirm = true)

        assertTrue(control.resolve(snapshot("cover.garage", "open", features = COVER_CLOSE))!!.requiresConfirmation)
        assertTrue(control.resolve(snapshot("cover.garage", "opening", features = COVER_STOP))!!.requiresConfirmation)
    }

    @Test fun `cover does not resolve an operation without its feature`() {
        val control = action(ActionKind.CONTROL_COVER, "cover.garage")

        assertNull(control.resolve(snapshot("cover.garage", "closed", features = COVER_CLOSE)))
        assertNull(control.resolve(snapshot("cover.garage", "open", features = COVER_OPEN)))
        assertNull(control.resolve(snapshot("cover.garage", "opening", features = COVER_OPEN or COVER_CLOSE)))
    }

    @Test fun `state labels and action hints are readable`() {
        val lock = action(ActionKind.CONTROL_LOCK, "lock.front_door")
        val cover = action(ActionKind.CONTROL_COVER, "cover.garage")

        assertEquals("Unlocking…", snapshot("lock.front_door", "unlocking").displayState())
        assertEquals("Tap to unlock", lock.actionHint(snapshot("lock.front_door", "locked")))
        assertEquals("Wait until movement finishes", lock.actionHint(snapshot("lock.front_door", "locking")))
        assertEquals("Jammed", snapshot("lock.front_door", "jammed").displayState())
        assertEquals("Action unavailable", lock.actionHint(snapshot("lock.front_door", "jammed")))
        assertEquals("Closing…", snapshot("cover.garage", "closing", features = COVER_STOP).displayState())
        assertEquals("Tap to stop", cover.actionHint(snapshot("cover.garage", "closing", features = COVER_STOP)))
        assertEquals("Wait until movement finishes", cover.actionHint(snapshot("cover.garage", "closing", features = COVER_OPEN or COVER_CLOSE)))
        assertEquals("Action unsupported", cover.actionHint(snapshot("cover.garage", "closed", features = COVER_CLOSE)))
    }

    @Test fun `unexpected state uses readable fallback`() {
        assertEquals("Partially open", snapshot("cover.garage", "partially_open", features = COVER_OPEN).displayState())
    }

    @Test fun `state-aware management labels contain only the entity name`() {
        val entity = snapshot("lock.front_door", "locked")

        assertEquals("Test entity", action(ActionKind.CONTROL_LOCK, entity.entityId).label(entity))
    }

    @Test fun `script and toggle retain existing services`() {
        val script = action(ActionKind.RUN_SCRIPT, "script.arrive_home").resolve(null)!!
        val toggle = action(ActionKind.TOGGLE, "light.porch").resolve(snapshot("light.porch", "off"))!!

        assertEquals("turn_on", script.serviceName)
        assertEquals("toggle", toggle.serviceName)
        assertTrue(toggle.completesOnStateChange)
        assertEquals("off", toggle.startingState)
    }

    @Test fun `button and scene expose one stateless action`() {
        val button = snapshot("button.garage_remote", "unknown")
        val scene = snapshot("scene.arrive_home", "scening")

        assertEquals(listOf(ActionKind.PRESS_BUTTON), button.availableActionKinds())
        assertEquals(listOf(ActionKind.ACTIVATE_SCENE), scene.availableActionKinds())
        assertEquals("press", action(ActionKind.PRESS_BUTTON, button.entityId).resolve(null)!!.serviceName)
        assertEquals("turn_on", action(ActionKind.ACTIVATE_SCENE, scene.entityId).resolve(null)!!.serviceName)
        assertTrue(ActionKind.PRESS_BUTTON.isStatelessControl())
        assertTrue(ActionKind.ACTIVATE_SCENE.isStatelessControl())
    }

    @Test fun `stateless management labels describe the action`() {
        val button = action(ActionKind.PRESS_BUTTON, "button.garage_remote")
        val scene = action(ActionKind.ACTIVATE_SCENE, "scene.arrive_home")

        assertEquals("Press Test entity", button.label())
        assertEquals("Activate Test entity", scene.label())
    }

    @Test fun `button and scene use only optional confirmation`() {
        mapOf(ActionKind.PRESS_BUTTON to "button.test", ActionKind.ACTIVATE_SCENE to "scene.test").forEach { (kind, entityId) ->
            assertFalse(action(kind, entityId).resolve(null)!!.requiresConfirmation)
            assertTrue(action(kind, entityId, confirm = true).resolve(null)!!.requiresConfirmation)
        }
    }

    @Test fun `duplicate identity includes entity and action kind`() {
        val actions = listOf(action(ActionKind.PRESS_BUTTON, "button.garage_remote"))

        assertTrue(hasActionIdentity(actions, "button.garage_remote", ActionKind.PRESS_BUTTON))
        assertFalse(hasActionIdentity(actions, "button.garage_remote", ActionKind.ACTIVATE_SCENE))
        assertFalse(hasActionIdentity(actions, "button.other_remote", ActionKind.PRESS_BUTTON))
    }

    private fun action(
        kind: ActionKind,
        entityId: String,
        confirm: Boolean = false,
    ) = QuickAccessAction(
        id = "action-id",
        entityId = entityId,
        domain = entityId.substringBefore('.'),
        kind = kind,
        requiresConfirmation = confirm,
        displayName = "Test entity",
    )

    private fun snapshot(
        entityId: String,
        state: String,
        features: Int = 0,
    ) = EntitySnapshot(
        entityId = entityId,
        domain = entityId.substringBefore('.'),
        state = state,
        supportedFeatures = features,
        available = state !in setOf("unknown", "unavailable"),
        friendlyName = "Test entity",
    )

    private companion object {
        const val COVER_OPEN = 1
        const val COVER_CLOSE = 2
        const val COVER_STOP = 8
    }
}

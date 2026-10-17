package com.karoohass

import com.karoohass.core.ActionKind
import com.karoohass.core.EntitySnapshot
import com.karoohass.core.QuickAccessAction
import com.karoohass.core.resolve
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDispatchTest {
    private val preset =
        QuickAccessAction(
            id = "preset",
            entityId = "cover.garage",
            domain = "cover",
            kind = ActionKind.SET_COVER_POSITION,
            protected = true,
            targetPosition = 50,
        )

    @Test
    fun `closing or equal preset requires confirmation if it becomes opening during PIN entry`() =
        runBlocking {
            for (initialPosition in listOf(80, 50)) {
                val beforePin = preset.resolve(snapshot(initialPosition))!!
                assertFalse(beforePin.requiresConfirmation)
                val afterPin =
                    actionForDispatch(beforePin, confirmed = false) { entityId ->
                        assertEquals(preset.entityId, entityId)
                        snapshot(20)
                    }

                assertTrue(afterPin.needsConfirmation)
                assertEquals(50, afterPin.resolved!!.targetPosition)
                assertTrue(afterPin.resolved.mandatoryConfirmation)
            }
        }

    @Test
    fun `confirmation for the same preset survives PIN entry and another refresh`() =
        runBlocking {
            val beforePin = preset.resolve(snapshot(20))!!
            val afterPin = actionForDispatch(beforePin, confirmed = true) { snapshot(10) }

            assertFalse(afterPin.needsConfirmation)
            assertEquals(preset, afterPin.resolved!!.action)
            assertTrue(afterPin.resolved.mandatoryConfirmation)
        }

    @Test
    fun `closing preset can dispatch after PIN if it still closes`() =
        runBlocking {
            val decision = actionForDispatch(preset.resolve(snapshot(80))!!, confirmed = false) { snapshot(70) }

            assertFalse(decision.needsConfirmation)
            assertFalse(decision.resolved!!.mandatoryConfirmation)
        }

    @Test
    fun `failed refresh and unavailable position block even a confirmed preset`() =
        runBlocking {
            val beforePin = preset.resolve(snapshot(80))!!
            for (refreshed in listOf(null, snapshot(null), snapshot(20).copy(available = false), snapshot(20).copy(supportedFeatures = 0))) {
                val decision = actionForDispatch(beforePin, confirmed = true) { refreshed }

                assertNull(decision.resolved)
                assertFalse(decision.needsConfirmation)
            }
        }

    @Test
    fun `optional confirmation remains required for closing presets`() =
        runBlocking {
            val resolved = preset.copy(requiresConfirmation = true).resolve(snapshot(80))!!

            assertTrue(actionForDispatch(resolved, confirmed = false) { snapshot(70) }.needsConfirmation)
            assertFalse(actionForDispatch(resolved, confirmed = true) { snapshot(70) }.needsConfirmation)
        }

    @Test
    fun `other controls retain their resolved operation without refreshing`() =
        runBlocking {
            val resolved = preset.copy(domain = "lock", kind = ActionKind.CONTROL_LOCK).resolve(snapshot(80).copy(domain = "lock", state = "locked"))!!
            val decision = actionForDispatch(resolved, confirmed = true) { error("Unexpected refresh") }

            assertEquals(resolved, decision.resolved)
            assertFalse(decision.needsConfirmation)
        }

    private fun snapshot(position: Int?) =
        EntitySnapshot(
            entityId = preset.entityId,
            domain = "cover",
            state = "closing",
            supportedFeatures = 4,
            available = true,
            friendlyName = "Garage",
            currentPosition = position,
        )
}

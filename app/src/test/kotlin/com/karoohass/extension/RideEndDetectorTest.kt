package com.karoohass.extension

import io.hammerhead.karooext.models.RideState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideEndDetectorTest {
    private val detector = RideEndDetector()

    @Test
    fun `startup and repeated idle never announce a ride`() {
        repeat(3) { assertFalse(detector.onState(RideState.Idle)) }
    }

    @Test
    fun `manual pause auto pause and resume do not announce a ride`() {
        assertFalse(detector.onState(RideState.Recording))
        assertFalse(detector.onState(RideState.Paused(auto = true)))
        assertFalse(detector.onState(RideState.Recording))
        assertFalse(detector.onState(RideState.Paused(auto = false)))
        assertFalse(detector.onState(RideState.Recording))
        assertTrue(detector.onState(RideState.Idle))
        assertFalse(detector.onState(RideState.Idle))
    }

    @Test
    fun `finishing from paused announces once for each ride`() {
        repeat(2) {
            assertFalse(detector.onState(RideState.Recording))
            assertFalse(detector.onState(RideState.Paused(auto = false)))
            assertTrue(detector.onState(RideState.Idle))
            assertFalse(detector.onState(RideState.Idle))
        }
    }

    @Test
    fun `extension can join an already paused ride`() {
        assertFalse(detector.onState(RideState.Paused(auto = true)))
        assertTrue(detector.onState(RideState.Idle))
    }

    @Test
    fun `reconnect at idle does not announce an old ride`() {
        detector.onState(RideState.Recording)
        detector.reset()
        assertFalse(detector.onState(RideState.Idle))
        assertFalse(detector.onState(RideState.Recording))
        assertTrue(detector.onState(RideState.Idle))
    }
}

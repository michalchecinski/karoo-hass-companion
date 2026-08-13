package com.karoohass.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepTest {
    @Test
    fun `action management is unavailable until security choices are complete`() {
        assertFalse(OnboardingStep.CONNECT.allowsActionManagement())
        assertFalse(OnboardingStep.CONNECTION_POLICY.allowsActionManagement())
        assertFalse(OnboardingStep.PIN_MODE.allowsActionManagement())
        assertTrue(OnboardingStep.FIRST_ACTION.allowsActionManagement())
        assertTrue(OnboardingStep.COMPLETE.allowsActionManagement())
    }
}

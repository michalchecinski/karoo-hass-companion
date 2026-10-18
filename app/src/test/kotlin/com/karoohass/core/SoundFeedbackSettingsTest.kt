package com.karoohass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundFeedbackSettingsTest {
    @Test
    fun `fresh and existing installations stay silent until opted in`() {
        assertFalse(AppSettings().soundFeedbackEnabled)
        assertFalse(decodeSettings(null).soundFeedbackEnabled)
        val oldSettings = decodeSettings("""{"origin":"https://home.example","policy":"ALLOW_COMPANION_FALLBACK","pinMode":"SELECTED_ACTIONS"}""")
        assertFalse(oldSettings.soundFeedbackEnabled)
        assertEquals("https://home.example", oldSettings.origin)
        assertEquals(ConnectionPolicy.ALLOW_COMPANION_FALLBACK, oldSettings.connectionPolicy)
        assertEquals(PinMode.SELECTED_ACTIONS, oldSettings.pinMode)
        assertEquals(OnboardingStep.COMPLETE, oldSettings.onboardingStep)
    }

    @Test
    fun `sound preference round trips without losing configured actions`() {
        val settings =
            AppSettings(
                origin = "https://home.example",
                soundFeedbackEnabled = true,
                actions =
                    listOf(
                        QuickAccessAction("test", "button.test", "button", ActionKind.PRESS_BUTTON),
                        QuickAccessAction("preset", "cover.test", "cover", ActionKind.SET_COVER_POSITION, targetPosition = 70),
                    ),
            )
        val decoded = decodeSettings(encodeSettings(settings).toString())
        assertTrue(decoded.soundFeedbackEnabled)
        assertEquals(settings, decoded)
        assertFalse(decodeSettings(encodeSettings(decoded.copy(soundFeedbackEnabled = false)).toString()).soundFeedbackEnabled)
    }
}

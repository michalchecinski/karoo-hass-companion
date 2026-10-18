package com.karoohass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsCodecTest {
    @Test fun `older saved actions decode without a position target`() {
        val settings =
            decodeSettings(
                """
                {
                  "actions":[
                    {"id":"cover","entityId":"cover.garage","domain":"cover","kind":"CONTROL_COVER","order":0}
                  ]
                }
                """.trimIndent(),
            )

        assertNull(settings.actions.single().targetPosition)
        assertEquals(OnboardingStep.COMPLETE, settings.onboardingStep)
    }

    @Test fun `position presets persist their valid target only`() {
        val settings =
            AppSettings(
                actions =
                    listOf(
                        QuickAccessAction(
                            id = "preset",
                            entityId = "cover.garage",
                            domain = "cover",
                            kind = ActionKind.SET_COVER_POSITION,
                            targetPosition = 42,
                        ),
                    ),
            )

        val encodedAction = encodeSettings(settings).getJSONArray("actions").getJSONObject(0)
        val decoded = decodeSettings(encodeSettings(settings).toString())

        assertEquals(42, encodedAction.getInt("targetPosition"))
        assertEquals(42, decoded.actions.single().targetPosition)
    }

    @Test fun `invalid saved position target is ignored`() {
        val settings =
            decodeSettings(
                """
                {
                  "actions":[
                    {"id":"preset","entityId":"cover.garage","domain":"cover","kind":"SET_COVER_POSITION","targetPosition":100}
                  ]
                }
                """.trimIndent(),
            )

        assertNull(settings.actions.single().targetPosition)
    }
}

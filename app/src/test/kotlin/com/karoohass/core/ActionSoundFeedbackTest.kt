package com.karoohass.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionSoundFeedbackTest {
    @Test
    fun `disabling or backgrounding suppresses later cues without replay`() {
        var enabled = true
        var foreground = true
        val played = mutableListOf<ActionCue>()
        val feedback = ActionSoundFeedback({ enabled }, { foreground }) { played += it }
        feedback.emit(ActionCue.ACCEPTED)
        enabled = false
        feedback.emit(ActionCue.COMPLETED)
        enabled = true
        foreground = false
        feedback.emit(ActionCue.UNCERTAIN)
        foreground = true
        assertEquals(listOf(ActionCue.ACCEPTED), played)
        feedback.emit(ActionCue.FAILED)
        assertEquals(listOf(ActionCue.ACCEPTED, ActionCue.FAILED), played)
    }
}

package com.karoohass.core

enum class ActionCue { ACCEPTED, COMPLETED }

/** Sound is best-effort feedback, never part of the action's result. */
class ActionSoundFeedback(
    private val enabled: () -> Boolean,
    private val foreground: () -> Boolean,
    private val play: (ActionCue) -> Unit,
) {
    fun emit(cue: ActionCue) {
        if (enabled() && foreground()) runCatching { play(cue) }
    }
}

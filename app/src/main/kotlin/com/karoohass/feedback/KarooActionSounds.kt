package com.karoohass.feedback

import android.content.Context
import com.karoohass.core.ActionCue
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.PlayBeepPattern

/** Delegates audio to Karoo; disconnected effects are dropped, never replayed. */
class KarooActionSounds(context: Context) {
    private val service = KarooSystemService(context.applicationContext)
    private var connectionAttempted = false

    fun connect() {
        if (connectionAttempted) return
        connectionAttempted = true
        runCatching { service.connect() }
    }

    fun disconnect() {
        if (!connectionAttempted) return
        connectionAttempted = false
        runCatching { service.disconnect() }
    }

    fun play(cue: ActionCue) {
        if (service.connected) service.dispatch(pattern(cue))
    }

    internal fun pattern(cue: ActionCue): PlayBeepPattern {
        fun tone(
            frequency: Int?,
            duration: Int,
        ) = PlayBeepPattern.Tone(frequency, duration)
        return PlayBeepPattern(
            when (cue) {
                ActionCue.ACCEPTED -> listOf(tone(4000, 200))
                ActionCue.COMPLETED -> listOf(tone(5000, 200), tone(null, 100), tone(5000, 200))
            },
        )
    }
}

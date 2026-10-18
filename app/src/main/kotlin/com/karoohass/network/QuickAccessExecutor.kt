package com.karoohass.network

import com.karoohass.core.ActionCue
import com.karoohass.core.ActionOutcome
import com.karoohass.core.EntitySnapshot
import com.karoohass.core.ResolvedAction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Emits sounds from execution events, never from replayable UI state. */
class QuickAccessExecutor(private val repository: HomeAssistantRepository) {
    suspend fun execute(
        resolved: ResolvedAction,
        onOutcome: (ActionOutcome) -> Unit,
        onSnapshot: (EntitySnapshot) -> Unit,
        onCue: (ActionCue) -> Unit,
    ): ActionOutcome {
        onOutcome(ActionOutcome.SENDING)
        val requested = repository.execute(resolved)
        currentCoroutineContext().ensureActive()
        onOutcome(requested)
        onCue(
            when (requested) {
                // SENDING returned by execute means a validated accepted response,
                // unlike the initial SENDING published before the HTTP request.
                ActionOutcome.SENDING, ActionOutcome.REQUESTED -> ActionCue.ACCEPTED
                ActionOutcome.FAILED -> ActionCue.FAILED
                ActionOutcome.UNKNOWN -> ActionCue.UNCERTAIN
                ActionOutcome.COMPLETED -> ActionCue.COMPLETED
            },
        )
        val updated =
            when {
                requested == ActionOutcome.SENDING && resolved.expectedState != null -> repository.awaitState(resolved.action.entityId) { it.state == resolved.expectedState }
                requested == ActionOutcome.REQUESTED && (resolved.completesOnStateChange || resolved.refreshAfterRequest) ->
                    repository.awaitState(resolved.action.entityId) { resolved.startingState == null || it.state != resolved.startingState }
                        ?: if (resolved.refreshAfterRequest) repository.refresh(resolved.action.entityId) else null
                else -> null
            }
        currentCoroutineContext().ensureActive()
        val result =
            if (updated != null) {
                onSnapshot(updated)
                if (resolved.refreshAfterRequest) requested else ActionOutcome.COMPLETED
            } else if (requested == ActionOutcome.SENDING || requested == ActionOutcome.REQUESTED && resolved.completesOnStateChange) {
                ActionOutcome.UNKNOWN
            } else {
                requested
            }
        if (result != requested) {
            onOutcome(result)
            when (result) {
                ActionOutcome.COMPLETED -> onCue(ActionCue.COMPLETED)
                ActionOutcome.UNKNOWN -> onCue(ActionCue.UNCERTAIN)
                else -> Unit
            }
        }
        return result
    }
}

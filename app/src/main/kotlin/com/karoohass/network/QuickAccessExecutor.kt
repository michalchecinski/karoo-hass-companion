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
        val executionContext = currentCoroutineContext()
        val updateSnapshot: (EntitySnapshot) -> Unit = { snapshot ->
            executionContext.ensureActive()
            onSnapshot(snapshot)
        }
        onOutcome(ActionOutcome.SENDING)
        val requested = repository.execute(resolved)
        currentCoroutineContext().ensureActive()
        onOutcome(requested)
        when (requested) {
            // SENDING returned by execute means a validated accepted response,
            // unlike the initial SENDING published before the HTTP request.
            ActionOutcome.SENDING, ActionOutcome.REQUESTED -> onCue(ActionCue.ACCEPTED)
            ActionOutcome.COMPLETED -> onCue(ActionCue.COMPLETED)
            else -> Unit
        }
        val updated =
            when {
                requested == ActionOutcome.SENDING && resolved.expectedState != null ->
                    repository.awaitState(resolved.action.entityId, { it.state == resolved.expectedState }, updateSnapshot)
                requested == ActionOutcome.REQUESTED && resolved.completesOnStateChange ->
                    repository.awaitState(resolved.action.entityId, { resolved.startingState == null || it.state != resolved.startingState }, updateSnapshot)
                requested == ActionOutcome.REQUESTED && resolved.refreshAfterRequest ->
                    repository.awaitState(
                        resolved.action.entityId,
                        {
                            resolved.targetPosition?.let { target -> it.currentPosition == target }
                                ?: (resolved.startingState == null || it.state != resolved.startingState)
                        },
                        updateSnapshot,
                    ) ?: repository.refresh(resolved.action.entityId)?.also(updateSnapshot)
                else -> null
            }
        currentCoroutineContext().ensureActive()
        val result =
            if (updated != null) {
                if (resolved.refreshAfterRequest) requested else ActionOutcome.COMPLETED
            } else if (requested == ActionOutcome.SENDING || requested == ActionOutcome.REQUESTED && resolved.completesOnStateChange) {
                ActionOutcome.UNKNOWN
            } else {
                requested
            }
        if (result != requested) {
            onOutcome(result)
            if (result == ActionOutcome.COMPLETED) onCue(ActionCue.COMPLETED)
        }
        return result
    }
}

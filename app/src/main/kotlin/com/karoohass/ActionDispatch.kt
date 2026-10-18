package com.karoohass

import com.karoohass.core.ActionKind
import com.karoohass.core.EntitySnapshot
import com.karoohass.core.ResolvedAction
import com.karoohass.core.resolve

internal data class ActionAuthorization(
    val confirmed: Boolean = false,
    val pinAuthorized: Boolean = false,
)

internal data class ActionDispatchDecision(
    val resolved: ResolvedAction?,
    val needsConfirmation: Boolean,
)

internal suspend fun actionForDispatch(
    original: ResolvedAction,
    confirmed: Boolean,
    refresh: suspend (String) -> EntitySnapshot?,
): ActionDispatchDecision {
    // PIN entry and confirmation can outlive the position used to prepare a preset.
    val resolved =
        if (original.action.kind == ActionKind.SET_COVER_POSITION) {
            original.action.resolve(refresh(original.action.entityId))
        } else {
            original
        }
    return ActionDispatchDecision(resolved, resolved?.requiresConfirmation == true && !confirmed)
}

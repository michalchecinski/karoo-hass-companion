package com.karoohass.core

enum class ConnectionPolicy { WIFI_ONLY, ALLOW_COMPANION_FALLBACK }

enum class PinMode { DISABLED, WHOLE_APP, SELECTED_ACTIONS }

enum class OnboardingStep {
    CONNECT,
    CONNECTION_POLICY,
    PIN_MODE,
    FIRST_ACTION,
    COMPLETE,
}

enum class ActionKind { RUN_SCRIPT, PRESS_BUTTON, ACTIVATE_SCENE, CONTROL_LOCK, CONTROL_COVER, TOGGLE }

enum class ActionOutcome { SENDING, REQUESTED, COMPLETED, FAILED, UNKNOWN }

data class QuickAccessAction(
    val id: String,
    val entityId: String,
    val domain: String,
    val kind: ActionKind,
    val protected: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val icon: String? = null,
    val order: Long = 0,
    val displayName: String? = null,
)

fun hasActionIdentity(
    actions: Collection<QuickAccessAction>,
    entityId: String,
    kind: ActionKind,
) = actions.any { it.entityId == entityId && it.kind == kind }

data class ResolvedAction(
    val action: QuickAccessAction,
    val serviceName: String,
    val operationLabel: String,
    val expectedState: String? = null,
    val startingState: String? = null,
    val completesOnStateChange: Boolean = false,
    val refreshAfterRequest: Boolean = false,
    val mandatoryConfirmation: Boolean = false,
) {
    val requiresConfirmation: Boolean
        get() = mandatoryConfirmation || action.requiresConfirmation

    fun confirmationLabel(entity: EntitySnapshot?): String =
        "$operationLabel ${entity?.friendlyName ?: action.displayName ?: action.entityId}"
}

data class EntitySnapshot(
    val entityId: String,
    val domain: String,
    val state: String,
    val supportedFeatures: Int,
    val available: Boolean,
    val lastUpdated: String? = null,
    val friendlyName: String,
    val icon: String? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
)

data class AppSettings(
    val origin: String? = null,
    val connectionPolicy: ConnectionPolicy = ConnectionPolicy.WIFI_ONLY,
    val pinMode: PinMode = PinMode.DISABLED,
    val actions: List<QuickAccessAction> = emptyList(),
    val onboardingStep: OnboardingStep = OnboardingStep.CONNECT,
)

fun OnboardingStep.allowsActionManagement() = this == OnboardingStep.FIRST_ACTION || this == OnboardingStep.COMPLETE

fun EntitySnapshot.availableActionKinds(): List<ActionKind> =
    when (domain) {
        "script" -> listOf(ActionKind.RUN_SCRIPT)
        "button" -> listOf(ActionKind.PRESS_BUTTON)
        "scene" -> listOf(ActionKind.ACTIVATE_SCENE)
        "lock" -> listOf(ActionKind.CONTROL_LOCK)
        "cover" -> if (supportedFeatures and COVER_DIRECTIONAL_FEATURES != 0) listOf(ActionKind.CONTROL_COVER) else emptyList()
        "light", "switch" -> listOf(ActionKind.TOGGLE)
        else -> emptyList()
    }

fun QuickAccessAction.resolve(entity: EntitySnapshot?): ResolvedAction? =
    when (kind) {
        ActionKind.RUN_SCRIPT -> ResolvedAction(this, "turn_on", "Run")
        ActionKind.PRESS_BUTTON -> ResolvedAction(this, "press", "Press")
        ActionKind.ACTIVATE_SCENE -> ResolvedAction(this, "turn_on", "Activate")
        ActionKind.TOGGLE ->
            ResolvedAction(
                action = this,
                serviceName = "toggle",
                operationLabel = "Toggle",
                startingState = entity?.state,
                completesOnStateChange = true,
            )
        ActionKind.CONTROL_LOCK -> resolveLock(entity)
        ActionKind.CONTROL_COVER -> resolveCover(entity)
    }

fun QuickAccessAction.label(entity: EntitySnapshot? = null): String {
    val operation =
        when (kind) {
            ActionKind.RUN_SCRIPT -> "Run"
            ActionKind.PRESS_BUTTON -> "Press"
            ActionKind.ACTIVATE_SCENE -> "Activate"
            ActionKind.TOGGLE -> "Toggle"
            ActionKind.CONTROL_LOCK, ActionKind.CONTROL_COVER -> null
        }
    return listOfNotNull(operation, entity?.friendlyName ?: displayName ?: entityId).joinToString(" ")
}

fun EntitySnapshot.displayState(): String =
    when (domain) {
        "lock" ->
            when (state) {
                "locked" -> "Locked"
                "unlocked" -> "Unlocked"
                "locking" -> "Locking…"
                "unlocking" -> "Unlocking…"
                "open" -> "Open"
                "opening" -> "Opening…"
                "jammed" -> "Jammed"
                "unknown" -> "Unknown"
                "unavailable" -> "Unavailable"
                else -> state.toReadableState()
            }
        "cover" ->
            when (state) {
                "open" -> "Open"
                "closed" -> "Closed"
                "opening" -> "Opening…"
                "closing" -> "Closing…"
                "unknown" -> "Unknown"
                "unavailable" -> "Unavailable"
                else -> state.toReadableState()
            }
        else -> state
    }

fun QuickAccessAction.actionHint(entity: EntitySnapshot?): String {
    val resolved = resolve(entity)
    return when {
        kind !in setOf(ActionKind.CONTROL_LOCK, ActionKind.CONTROL_COVER) -> ""
        entity == null -> "State unavailable"
        !entity.available -> "Action unavailable"
        resolved != null -> "Tap to ${resolved.operationLabel.lowercase()}"
        kind == ActionKind.CONTROL_LOCK && entity.state in LOCK_TRANSITIONAL_STATES -> "Wait until movement finishes"
        kind == ActionKind.CONTROL_COVER && entity.state in COVER_TRANSITIONAL_STATES && entity.supportedFeatures and COVER_SUPPORT_STOP == 0 -> "Wait until movement finishes"
        kind == ActionKind.CONTROL_COVER && entity.state in setOf("open", "closed") -> "Action unsupported"
        else -> "Action unavailable"
    }
}

fun ActionKind.isStatelessControl() = this in setOf(ActionKind.RUN_SCRIPT, ActionKind.PRESS_BUTTON, ActionKind.ACTIVATE_SCENE)

private fun QuickAccessAction.resolveLock(entity: EntitySnapshot?): ResolvedAction? {
    if (entity == null || entity.domain != "lock" || !entity.available) return null
    return when (entity.state) {
        "locked" -> ResolvedAction(this, "unlock", "Unlock", expectedState = "unlocked", startingState = entity.state, mandatoryConfirmation = true)
        "unlocked", "open" -> ResolvedAction(this, "lock", "Lock", expectedState = "locked", startingState = entity.state)
        else -> null
    }
}

private fun QuickAccessAction.resolveCover(entity: EntitySnapshot?): ResolvedAction? {
    if (entity == null || entity.domain != "cover" || !entity.available) return null
    return when (entity.state) {
        "closed" ->
            if (entity.supportedFeatures and COVER_SUPPORT_OPEN != 0) {
                ResolvedAction(this, "open_cover", "Open", expectedState = "open", startingState = entity.state, mandatoryConfirmation = true)
            } else {
                null
            }
        "open" ->
            if (entity.supportedFeatures and COVER_SUPPORT_CLOSE != 0) {
                ResolvedAction(this, "close_cover", "Close", expectedState = "closed", startingState = entity.state)
            } else {
                null
            }
        "opening", "closing" ->
            if (entity.supportedFeatures and COVER_SUPPORT_STOP != 0) {
                ResolvedAction(this, "stop_cover", "Stop", startingState = entity.state, refreshAfterRequest = true)
            } else {
                null
            }
        else -> null
    }
}

private fun String.toReadableState(): String =
    replace('_', ' ').replaceFirstChar { character -> character.titlecase() }

private const val COVER_SUPPORT_OPEN = 1
private const val COVER_SUPPORT_CLOSE = 2
private const val COVER_SUPPORT_STOP = 8
private const val COVER_DIRECTIONAL_FEATURES = COVER_SUPPORT_OPEN or COVER_SUPPORT_CLOSE or COVER_SUPPORT_STOP
private val LOCK_TRANSITIONAL_STATES = setOf("locking", "unlocking", "opening")
private val COVER_TRANSITIONAL_STATES = setOf("opening", "closing")

package com.karoohass.core

enum class ConnectionPolicy { WIFI_ONLY, ALLOW_COMPANION_FALLBACK }
enum class PinMode { DISABLED, WHOLE_APP, SELECTED_ACTIONS }
enum class ActionKind { RUN_SCRIPT, LOCK, UNLOCK, OPEN_COVER, CLOSE_COVER, STOP_COVER, TOGGLE, TURN_ON, TURN_OFF }
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
)

fun EntitySnapshot.availableActionKinds(): List<ActionKind> = when (domain) {
    "script" -> listOf(ActionKind.RUN_SCRIPT)
    "lock" -> listOf(ActionKind.LOCK, ActionKind.UNLOCK)
    "cover" -> buildList {
        if (supportedFeatures and COVER_SUPPORT_OPEN != 0) add(ActionKind.OPEN_COVER)
        if (supportedFeatures and COVER_SUPPORT_CLOSE != 0) add(ActionKind.CLOSE_COVER)
        if (supportedFeatures and COVER_SUPPORT_STOP != 0) add(ActionKind.STOP_COVER)
    }
    "light", "switch" -> listOf(ActionKind.TOGGLE)
    else -> emptyList()
}

fun ActionKind.serviceName(): String = when (this) {
    ActionKind.RUN_SCRIPT -> "turn_on"
    ActionKind.LOCK -> "lock"
    ActionKind.UNLOCK -> "unlock"
    ActionKind.OPEN_COVER -> "open_cover"
    ActionKind.CLOSE_COVER -> "close_cover"
    ActionKind.STOP_COVER -> "stop_cover"
    ActionKind.TOGGLE -> "toggle"
    ActionKind.TURN_ON -> "turn_on"
    ActionKind.TURN_OFF -> "turn_off"
}

fun ActionKind.expectedState(): String? = when (this) {
    ActionKind.LOCK -> "locked"
    ActionKind.UNLOCK -> "unlocked"
    ActionKind.OPEN_COVER -> "open"
    ActionKind.CLOSE_COVER -> "closed"
    ActionKind.TURN_ON -> "on"
    ActionKind.TURN_OFF -> "off"
    ActionKind.RUN_SCRIPT, ActionKind.STOP_COVER, ActionKind.TOGGLE -> null
}

fun QuickAccessAction.label(entity: EntitySnapshot? = null): String {
    val operation = when (kind) {
        ActionKind.RUN_SCRIPT -> "Run"; ActionKind.LOCK -> "Lock"; ActionKind.UNLOCK -> "Unlock"
        ActionKind.OPEN_COVER -> "Open"; ActionKind.CLOSE_COVER -> "Close"; ActionKind.STOP_COVER -> "Stop"
        ActionKind.TOGGLE -> "Toggle"
        ActionKind.TURN_ON -> "Turn on"; ActionKind.TURN_OFF -> "Turn off"
    }
    return "$operation ${entity?.friendlyName ?: displayName ?: entityId}"
}

private const val COVER_SUPPORT_OPEN = 1
private const val COVER_SUPPORT_CLOSE = 2
private const val COVER_SUPPORT_STOP = 8

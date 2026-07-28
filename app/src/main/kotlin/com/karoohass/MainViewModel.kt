package com.karoohass

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karoohass.auth.OAuthManager
import com.karoohass.core.ActionKind
import com.karoohass.core.ActionOutcome
import com.karoohass.core.AppSettings
import com.karoohass.core.ConnectionPolicy
import com.karoohass.core.EntitySnapshot
import com.karoohass.core.PinMode
import com.karoohass.core.QuickAccessAction
import com.karoohass.core.SettingsStore
import com.karoohass.core.expectedState
import com.karoohass.network.DirectWifiTransport
import com.karoohass.network.HomeAssistantRepository
import com.karoohass.network.KarooTransport
import com.karoohass.network.PolicyTransport
import com.karoohass.security.IdleLockCancellation
import com.karoohass.security.IdleLockScheduler
import com.karoohass.security.PinStore
import com.karoohass.security.TokenStore
import com.karoohass.security.WHOLE_APP_IDLE_TIMEOUT_MILLIS
import com.karoohass.security.WholeAppIdleLockController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class UiState(
    val settings: AppSettings = AppSettings(),
    val snapshots: Map<String, EntitySnapshot> = emptyMap(),
    val screen: Screen = Screen.HOME,
    val busy: Boolean = false,
    val message: String? = null,
    val outcome: ActionOutcome? = null,
    val outcomeActionId: String? = null,
    val pending: QuickAccessAction? = null,
    val unlocking: Boolean = false,
    val wholeAppLocked: Boolean = false,
)

enum class Screen { HOME, SETUP, AUTH, MANAGE, PIN }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)
    private val tokens = TokenStore(application)
    private val pinStore = PinStore(application)
    private val oauth = OAuthManager(application, tokens)
    private val rawSettings = settingsStore.settings
    private val screen = MutableStateFlow(Screen.HOME)
    private val snapshots = MutableStateFlow<Map<String, EntitySnapshot>>(emptyMap())
    private val work = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val pending = MutableStateFlow<QuickAccessAction?>(null)
    private val outcome = MutableStateFlow<ActionOutcome?>(null)
    private val outcomeActionId = MutableStateFlow<String?>(null)
    private val unlocking = MutableStateFlow(false)
    private val wholeAppLocked = MutableStateFlow(true)
    private val idleLockController =
        WholeAppIdleLockController(
            timeoutMillis = WHOLE_APP_IDLE_TIMEOUT_MILLIS,
            nowMillis = { SystemClock.elapsedRealtime() },
            scheduler =
                IdleLockScheduler { delayMillis, action ->
                    val job =
                        viewModelScope.launch {
                            delay(delayMillis)
                            action()
                        }
                    IdleLockCancellation(job::cancel)
                },
            onLock = { wholeAppLocked.value = true },
        )
    private var authorizationUrl: String? = null
    private val directTransport = DirectWifiTransport(application)
    private val policyTransport = PolicyTransport({ state.value.settings.connectionPolicy }, directTransport, KarooTransport(application))
    private val repository = HomeAssistantRepository({ state.value.settings.origin }, policyTransport, tokens) { oauth.refresh(policyTransport) }
    private val wifiRepository = HomeAssistantRepository({ state.value.settings.origin }, directTransport, tokens) { oauth.refresh(directTransport) }
    val state: StateFlow<UiState> =
        combine(
            combine(rawSettings, screen) { settings, current -> settings to current },
            combine(snapshots, work) { loaded, isBusy -> loaded to isBusy },
            combine(message, pending) { notice, request -> notice to request },
            combine(outcome, outcomeActionId, unlocking, wholeAppLocked) { result, actionId, isUnlocking, isWholeAppLocked ->
                PinUiState(result, actionId, isUnlocking, isWholeAppLocked)
            },
        ) { settingsAndScreen, snapshotsAndWork, messageAndPending, outcomeAndAuth ->
            UiState(
                settings = settingsAndScreen.first,
                snapshots = snapshotsAndWork.first,
                screen = settingsAndScreen.second,
                busy = snapshotsAndWork.second,
                message = messageAndPending.first,
                outcome = outcomeAndAuth.outcome,
                outcomeActionId = outcomeAndAuth.actionId,
                pending = messageAndPending.second,
                unlocking = outcomeAndAuth.unlocking,
                wholeAppLocked = outcomeAndAuth.wholeAppLocked,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            val persisted = rawSettings.first()
            wholeAppLocked.value = persisted.pinMode == PinMode.WHOLE_APP && persisted.origin != null
            val oauthOrigin = oauth.configuredOrigin()
            if (persisted.origin == null && oauthOrigin != null && runCatching { tokens.load() }.getOrNull() != null) {
                settingsStore.update { current -> current.copy(origin = oauthOrigin) }
            }
            if (persisted.pinMode != PinMode.WHOLE_APP && persisted.origin != null && persisted.actions.isNotEmpty() && runCatching { tokens.load() }.getOrNull() != null) {
                refreshEntities(silent = true)
            }
        }
    }

    fun openSetup() {
        message.value = null
        screen.value = Screen.SETUP
    }

    fun openEntityChooser() {
        screen.value = Screen.MANAGE
        discover()
    }

    fun home() {
        screen.value = Screen.HOME
        pending.value = null
        message.value = null
    }

    fun back() {
        screen.value =
            when (screen.value) {
                Screen.MANAGE, Screen.AUTH -> Screen.SETUP
                Screen.SETUP -> Screen.HOME
                else -> Screen.HOME
            }
        message.value = null
    }

    fun setOrigin(raw: String): String? {
        val normalized = oauth.normalizeOrigin(raw) ?: return "Enter a trusted HTTPS Home Assistant origin"
        viewModelScope.launch { settingsStore.update { it.copy(origin = normalized) } }
        return null
    }

    /** Starts OAuth from the validated input, not from DataStore's eventually-consistent state. */
    fun beginAuthentication(rawOrigin: String): String? {
        val origin = oauth.normalizeOrigin(rawOrigin) ?: return "Enter a trusted HTTPS Home Assistant origin"
        authorizationUrl = oauth.authorizationUrl(origin)
        viewModelScope.launch { settingsStore.update { it.copy(origin = origin) } }
        screen.value = Screen.AUTH
        return null
    }

    fun currentAuthorizationUrl() = authorizationUrl

    fun receiveOAuthCallback(uri: Uri) {
        if (!oauth.receive(uri)) {
            message.value = "Sign-in callback was incomplete. Please try again."
            return
        }
        message.value = "Finishing sign-in…"
        callbackReceived()
    }

    fun callbackReceived() =
        viewModelScope.launch {
            work.value = true
            runCatching { oauth.consumeCallback() }
                .onSuccess { result ->
                    when (result) {
                        true -> {
                            message.value = null
                            openEntityChooser()
                        }
                        false -> message.value = "Sign-in could not be completed"
                        null -> Unit
                    }
                }
                .onFailure { error ->
                    Log.e("KarooHassOAuth", "Token exchange failed", error)
                    message.value = "Could not finish sign-in: ${error.message ?: "Wi-Fi is required"}"
                }
            work.value = false
        }

    fun savePolicy(policy: ConnectionPolicy) = viewModelScope.launch { settingsStore.update { it.copy(connectionPolicy = policy) } }

    fun savePinMode(
        mode: PinMode,
        pin: String? = null,
    ) =
        viewModelScope.launch {
            if (unlocking.value) return@launch
            if (mode == PinMode.DISABLED) {
                message.value = "Enter your current PIN to disable protection"
                return@launch
            }
            if (!pinStore.configured() && pin == null) {
                message.value = "Choose a 4–6 digit PIN"
                return@launch
            }
            unlocking.value = true
            val pinSet = if (!pinStore.configured()) withContext(Dispatchers.Default) { runCatching { pinStore.set(pin!!) } } else Result.success(Unit)
            if (pinSet.isSuccess) {
                settingsStore.update { it.copy(pinMode = mode) }
                if (mode == PinMode.WHOLE_APP) {
                    idleLockController.lockNow()
                } else {
                    idleLockController.clear()
                    wholeAppLocked.value = false
                }
                message.value = "PIN protection saved"
            } else {
                message.value = "PIN must contain 4–6 digits"
            }
            unlocking.value = false
        }

    fun disablePinProtection(pin: String) =
        viewModelScope.launch {
            if (unlocking.value) return@launch
            unlocking.value = true
            val result = withContext(Dispatchers.Default) { pinStore.verify(pin) }
            when (result) {
                is com.karoohass.security.PinResult.Success -> {
                    pinStore.clear()
                    settingsStore.update { it.copy(pinMode = PinMode.DISABLED) }
                    idleLockController.clear()
                    wholeAppLocked.value = false
                    message.value = "PIN protection disabled"
                }
                is com.karoohass.security.PinResult.Locked -> message.value = "PIN locked for ${result.remainingMs / 1000}s"
                else -> message.value = "Incorrect PIN"
            }
            unlocking.value = false
        }

    fun discover() = viewModelScope.launch { refreshEntities(silent = false) }

    private suspend fun refreshEntities(silent: Boolean) {
        work.value = true
        runCatching { wifiRepository.discover() }.onSuccess { found ->
            val byId = found.associateBy { it.entityId }
            snapshots.value = byId
            settingsStore.update { old -> old.copy(actions = old.actions.map { action -> byId[action.entityId]?.let { entity -> action.copy(displayName = entity.friendlyName, icon = entity.icon ?: action.icon) } ?: action }) }
        }.onFailure { if (!silent) message.value = it.message ?: "Could not load entities over Wi-Fi" }
        work.value = false
    }

    fun add(
        entity: EntitySnapshot,
        kind: ActionKind,
        protected: Boolean,
        confirm: Boolean,
    ) = viewModelScope.launch { settingsStore.update { old -> if (old.actions.any { it.entityId == entity.entityId && it.kind == kind }) old else old.copy(actions = old.actions + QuickAccessAction(UUID.randomUUID().toString(), entity.entityId, entity.domain, kind, protected, confirm || kind in setOf(ActionKind.UNLOCK, ActionKind.OPEN_COVER), entity.icon, (old.actions.maxOfOrNull { a -> a.order } ?: -1) + 1, entity.friendlyName)) } }

    fun remove(action: QuickAccessAction) = viewModelScope.launch { settingsStore.update { it.copy(actions = it.actions.filterNot { item -> item.id == action.id }) } }

    fun move(
        action: QuickAccessAction,
        offset: Int,
    ) =
        viewModelScope.launch {
            settingsStore.update { old ->
                val list = old.actions.sortedBy { it.order }.toMutableList()
                val from = list.indexOfFirst { it.id == action.id }
                val to = (from + offset).coerceIn(0, list.lastIndex)
                if (from >= 0) {
                    list.add(to, list.removeAt(from))
                    old.copy(actions = list.mapIndexed { index, item -> item.copy(order = index.toLong()) })
                } else {
                    old
                }
            }
        }

    fun invoke(action: QuickAccessAction) {
        val wholeAppProtected = state.value.settings.pinMode == PinMode.WHOLE_APP
        val selectedActionProtected =
            state.value.settings.pinMode == PinMode.SELECTED_ACTIONS &&
                action.protected
        val needsAuth = wholeAppProtected && wholeAppLocked.value || selectedActionProtected
        if (needsAuth) {
            pending.value = if (wholeAppProtected) null else action
            screen.value = Screen.PIN
        } else {
            begin(action)
        }
    }

    fun submitPin(pin: String) =
        viewModelScope.launch {
            if (unlocking.value) return@launch
            unlocking.value = true
            val result = withContext(Dispatchers.Default) { pinStore.verify(pin) }
            unlocking.value = false
            when (result) {
                is com.karoohass.security.PinResult.Success -> {
                    message.value = null
                    val action = pending.value
                    val wholeAppProtected = state.value.settings.pinMode == PinMode.WHOLE_APP
                    if (wholeAppProtected) {
                        wholeAppLocked.value = false
                        idleLockController.unlock()
                        work.value = true
                    }
                    screen.value = Screen.HOME
                    pending.value = null
                    if (wholeAppProtected) refreshEntities(silent = true)
                    action?.let(::begin)
                }
                is com.karoohass.security.PinResult.Locked -> message.value = "PIN locked for ${result.remainingMs / 1000}s"
                else -> message.value = "Incorrect PIN"
            }
        }

    fun confirm(action: QuickAccessAction) = begin(action)

    private fun begin(action: QuickAccessAction) =
        viewModelScope.launch {
            outcomeActionId.value = action.id
            outcome.value = null
            message.value = null
            work.value = true
            try {
                var snapshot = snapshots.value[action.entityId]
                val needsCurrentState = action.kind.expectedState() != null || action.kind == ActionKind.TOGGLE
                if (needsCurrentState && (snapshot == null || System.currentTimeMillis() - snapshot.fetchedAt > 60_000)) {
                    val refreshed = runCatching { repository.refresh(action.entityId) }.getOrNull()
                    if (refreshed == null || !refreshed.available) {
                        if (action.kind.expectedState() != null) {
                            outcome.value = ActionOutcome.FAILED
                            message.value = "Action unavailable: state could not be refreshed"
                            return@launch
                        }
                    } else {
                        snapshot = refreshed
                        snapshots.value = snapshots.value + (action.entityId to refreshed)
                    }
                }
                outcome.value = ActionOutcome.SENDING
                val requested = repository.execute(action)
                outcome.value = requested
                val expected = action.kind.expectedState()
                val updated =
                    when {
                        requested == ActionOutcome.SENDING && expected != null -> repository.awaitState(action.entityId) { it.state == expected }
                        requested == ActionOutcome.REQUESTED && action.kind == ActionKind.TOGGLE -> repository.awaitState(action.entityId) { snapshot == null || it.state != snapshot.state }
                        else -> null
                    }
                if (updated != null) {
                    snapshots.value = snapshots.value + (action.entityId to updated)
                    outcome.value = ActionOutcome.COMPLETED
                } else if (requested == ActionOutcome.SENDING || action.kind == ActionKind.TOGGLE) {
                    outcome.value = ActionOutcome.UNKNOWN
                }
                message.value =
                    when (outcome.value) {
                        ActionOutcome.FAILED -> "Action was not sent. Please try again."
                        ActionOutcome.UNKNOWN -> "Action outcome is uncertain."
                        else -> null
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                outcome.value = ActionOutcome.UNKNOWN
                message.value = "Action outcome is uncertain."
                Log.e("KarooHassAction", "Action failed unexpectedly", error)
            } finally {
                work.value = false
            }
        }

    fun signOutAndReset() =
        viewModelScope.launch {
            oauth.revoke()
            pinStore.clear()
            settingsStore.update { AppSettings() }
            snapshots.value = emptyMap()
            outcome.value = null
            outcomeActionId.value = null
            idleLockController.clear()
            wholeAppLocked.value = false
            screen.value = Screen.SETUP
        }

    fun onUserInteraction(): Boolean {
        val wholeAppProtected =
            state.value.settings.pinMode == PinMode.WHOLE_APP &&
                state.value.settings.origin != null
        if (!wholeAppProtected) return true
        if (wholeAppLocked.value) return false

        idleLockController.recordInteraction()
        return !wholeAppLocked.value
    }

    fun foregroundChanged(foreground: Boolean) {
        if (!foreground && state.value.settings.pinMode == PinMode.WHOLE_APP) {
            idleLockController.lockNow()
        }
    }

    fun enforceWholeAppPin() {
        if (state.value.settings.pinMode == PinMode.WHOLE_APP && state.value.settings.origin != null) {
            idleLockController.lockNow()
        }
    }

    private data class PinUiState(
        val outcome: ActionOutcome?,
        val actionId: String?,
        val unlocking: Boolean,
        val wholeAppLocked: Boolean,
    )
}

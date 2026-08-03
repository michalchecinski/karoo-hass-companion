package com.karoohass

import android.app.Application
import android.net.Uri
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
import com.karoohass.core.ResolvedAction
import com.karoohass.core.SettingsStore
import com.karoohass.core.actionHint
import com.karoohass.core.resolve
import com.karoohass.network.DirectWifiTransport
import com.karoohass.network.HomeAssistantRepository
import com.karoohass.network.KarooTransport
import com.karoohass.network.PolicyTransport
import com.karoohass.security.PinStore
import com.karoohass.security.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    val pending: ResolvedAction? = null,
    val confirmation: ResolvedAction? = null,
    val authorizedUntil: Long = 0,
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
    private val pending = MutableStateFlow<ResolvedAction?>(null)
    private val confirmation = MutableStateFlow<ResolvedAction?>(null)
    private val outcome = MutableStateFlow<ActionOutcome?>(null)
    private val outcomeActionId = MutableStateFlow<String?>(null)
    private val authorizedUntil = MutableStateFlow(0L)
    private val unlocking = MutableStateFlow(false)
    private val wholeAppLocked = MutableStateFlow(true)
    private var actionIntentGeneration = 0L
    private var authorizationUrl: String? = null
    private val directTransport = DirectWifiTransport(application)
    private val policyTransport = PolicyTransport({ state.value.settings.connectionPolicy }, directTransport, KarooTransport(application))
    private val repository = HomeAssistantRepository({ state.value.settings.origin }, policyTransport, tokens) { oauth.refresh(policyTransport) }
    private val wifiRepository = HomeAssistantRepository({ state.value.settings.origin }, directTransport, tokens) { oauth.refresh(directTransport) }
    val state: StateFlow<UiState> =
        combine(
            combine(rawSettings, screen) { settings, current -> settings to current },
            combine(snapshots, work) { loaded, isBusy -> loaded to isBusy },
            combine(message, pending, confirmation) { notice, request, confirmationRequest -> ActionUiState(notice, request, confirmationRequest) },
            combine(outcome, outcomeActionId, authorizedUntil, unlocking, wholeAppLocked) { result, actionId, auth, isUnlocking, isWholeAppLocked -> PinUiState(result, actionId, auth, isUnlocking, isWholeAppLocked) },
        ) { settingsAndScreen, snapshotsAndWork, messageAndPending, outcomeAndAuth ->
            UiState(settingsAndScreen.first, snapshotsAndWork.first, settingsAndScreen.second, snapshotsAndWork.second, messageAndPending.message, outcomeAndAuth.outcome, outcomeAndAuth.actionId, messageAndPending.pending, messageAndPending.confirmation, outcomeAndAuth.authorizedUntil, outcomeAndAuth.unlocking, outcomeAndAuth.wholeAppLocked)
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
        confirmation.value = null
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
                if (mode == PinMode.WHOLE_APP) wholeAppLocked.value = true
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
    ) = viewModelScope.launch { settingsStore.update { old -> if (old.actions.any { it.entityId == entity.entityId && it.kind == kind }) old else old.copy(actions = old.actions + QuickAccessAction(UUID.randomUUID().toString(), entity.entityId, entity.domain, kind, protected, confirm, entity.icon, (old.actions.maxOfOrNull { a -> a.order } ?: -1) + 1, entity.friendlyName)) } }

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
        if (!work.compareAndSet(expect = false, update = true)) return
        prepare(action, ++actionIntentGeneration)
    }

    private fun prepare(
        action: QuickAccessAction,
        intentGeneration: Long,
    ) =
        viewModelScope.launch {
            outcomeActionId.value = action.id
            outcome.value = null
            message.value = null
            var snapshot = snapshots.value[action.entityId]
            val mustRefresh = action.kind in setOf(ActionKind.CONTROL_LOCK, ActionKind.CONTROL_COVER)
            val refreshIfStale = action.kind == ActionKind.TOGGLE && (snapshot == null || System.currentTimeMillis() - snapshot.fetchedAt > 60_000)
            if (mustRefresh || refreshIfStale) {
                val refreshed = runCatching { repository.refresh(action.entityId) }.getOrNull()
                if (refreshed != null) {
                    snapshot = refreshed
                    snapshots.value = snapshots.value + (action.entityId to refreshed)
                } else if (mustRefresh) {
                    work.value = false
                    message.value = "Action unavailable: state could not be refreshed"
                    return@launch
                }
            }
            if (intentGeneration != actionIntentGeneration) {
                work.value = false
                return@launch
            }
            val resolved = action.resolve(snapshot)
            work.value = false
            if (resolved == null) {
                message.value = action.actionHint(snapshot)
                return@launch
            }
            if (resolved.requiresConfirmation) {
                confirmation.value = resolved
            } else {
                authorizeOrBegin(resolved)
            }
        }

    private fun authorizeOrBegin(resolved: ResolvedAction) {
        val action = resolved.action
        val needsAuth = state.value.settings.pinMode == PinMode.WHOLE_APP && System.currentTimeMillis() >= authorizedUntil.value || state.value.settings.pinMode == PinMode.SELECTED_ACTIONS && action.protected
        if (needsAuth) {
            pending.value = resolved
            screen.value = Screen.PIN
        } else {
            begin(resolved)
        }
    }

    fun confirmResolvedAction() {
        val resolved = confirmation.value ?: return
        confirmation.value = null
        authorizeOrBegin(resolved)
    }

    fun dismissConfirmation() {
        confirmation.value = null
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
                    val resolved = pending.value
                    val wholeAppProtected = state.value.settings.pinMode == PinMode.WHOLE_APP
                    if (wholeAppProtected) {
                        authorizedUntil.value = System.currentTimeMillis() + 120_000
                        wholeAppLocked.value = false
                        work.value = true
                    }
                    screen.value = Screen.HOME
                    pending.value = null
                    if (wholeAppProtected) refreshEntities(silent = true)
                    resolved?.let(::begin)
                }
                is com.karoohass.security.PinResult.Locked -> message.value = "PIN locked for ${result.remainingMs / 1000}s"
                else -> message.value = "Incorrect PIN"
            }
        }

    private fun begin(resolved: ResolvedAction) {
        if (!work.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val action = resolved.action
            outcomeActionId.value = action.id
            outcome.value = null
            message.value = null
            try {
                outcome.value = ActionOutcome.SENDING
                val requested = repository.execute(resolved)
                outcome.value = requested
                val updated =
                    when {
                        requested == ActionOutcome.SENDING && resolved.expectedState != null -> repository.awaitState(action.entityId) { it.state == resolved.expectedState }
                        requested == ActionOutcome.REQUESTED && resolved.completesOnStateChange -> repository.awaitState(action.entityId) { resolved.startingState == null || it.state != resolved.startingState }
                        requested == ActionOutcome.REQUESTED && resolved.refreshAfterRequest ->
                            repository.awaitState(action.entityId) { resolved.startingState == null || it.state != resolved.startingState }
                                ?: repository.refresh(action.entityId)
                        else -> null
                    }
                if (updated != null) {
                    snapshots.value = snapshots.value + (action.entityId to updated)
                    if (!resolved.refreshAfterRequest) outcome.value = ActionOutcome.COMPLETED
                } else if (requested == ActionOutcome.SENDING || resolved.completesOnStateChange) {
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
            val completedOutcome = outcome.value
            kotlinx.coroutines.delay(2_500)
            if (outcomeActionId.value == action.id && outcome.value == completedOutcome) {
                outcome.value = null
                outcomeActionId.value = null
            }
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
            confirmation.value = null
            wholeAppLocked.value = false
            screen.value = Screen.SETUP
        }

    fun foregroundChanged(foreground: Boolean) {
        if (!foreground) {
            actionIntentGeneration++
            confirmation.value = null
            if (pending.value != null) {
                pending.value = null
                if (screen.value == Screen.PIN) screen.value = Screen.HOME
            }
        }
        if (!foreground && state.value.settings.pinMode == PinMode.WHOLE_APP) {
            authorizedUntil.value = 0
            wholeAppLocked.value = true
        }
    }

    fun enforceWholeAppPin() {
        if (state.value.settings.pinMode == PinMode.WHOLE_APP && state.value.settings.origin != null) wholeAppLocked.value = true
    }

    private data class PinUiState(
        val outcome: ActionOutcome?,
        val actionId: String?,
        val authorizedUntil: Long,
        val unlocking: Boolean,
        val wholeAppLocked: Boolean,
    )

    private data class ActionUiState(
        val message: String?,
        val pending: ResolvedAction?,
        val confirmation: ResolvedAction?,
    )
}

package com.karoohass.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.karoohass.MainViewModel
import com.karoohass.R
import com.karoohass.Screen
import com.karoohass.UiState
import com.karoohass.auth.OAuthCallbackActivity
import com.karoohass.auth.isOAuthCallbackUri
import com.karoohass.core.ActionKind
import com.karoohass.core.ActionOutcome
import com.karoohass.core.ConnectionPolicy
import com.karoohass.core.EntitySnapshot
import com.karoohass.core.OnboardingStep
import com.karoohass.core.PinMode
import com.karoohass.core.QuickAccessAction
import com.karoohass.core.actionHint
import com.karoohass.core.availableActionKinds
import com.karoohass.core.displayState
import com.karoohass.core.hasActionIdentity
import com.karoohass.core.isStatelessControl
import com.karoohass.core.label

private val BackControlSize = 54.dp
private val BackControlBottomInset = 10.dp
private val HomeGridBottomPadding = BackControlSize + BackControlBottomInset + BackControlBottomInset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    model: MainViewModel,
    onExit: () -> Unit,
) {
    val displayedScreen =
        if (
            state.settings.origin != null &&
            state.settings.pinMode == PinMode.WHOLE_APP &&
            state.wholeAppLocked
        ) {
            Screen.PIN
        } else {
            state.screen
        }
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (displayedScreen) {
                Screen.HOME ->
                    Box(Modifier.padding(top = if (state.settings.origin != null) 48.dp else 0.dp)) {
                        Home(state, model::invoke, model::openSetup)
                    }
                Screen.SETUP -> Setup(state, model)
                Screen.AUTH -> OAuthWebView(model.currentAuthorizationUrl())
                Screen.ONBOARDING_POLICY -> OnboardingPolicy(state, model)
                Screen.ONBOARDING_PIN -> OnboardingPin(state, model)
                Screen.MANAGE -> Manage(state, model)
                Screen.PIN -> PinEntry(state, model)
            }
            if (displayedScreen == Screen.HOME && state.settings.origin != null) {
                IconButton(onClick = model::openSetup, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                }
            }
            if (displayedScreen != Screen.PIN) {
                Image(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "Back",
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = BackControlBottomInset)
                            .size(BackControlSize)
                            .clickable(onClick = if (displayedScreen == Screen.HOME) onExit else model::back),
                )
            }
            if (displayedScreen == Screen.MANAGE) {
                Button(
                    onClick = model::home,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 16.dp),
                ) {
                    Text("Done")
                }
            }
            if (displayedScreen != Screen.SETUP) state.message?.let { Text(it, Modifier.align(Alignment.BottomCenter).padding(12.dp), color = MaterialTheme.colorScheme.error) }
        }
    }
    state.confirmation?.takeIf { displayedScreen == Screen.HOME }?.let { resolved ->
        AlertDialog(
            onDismissRequest = model::dismissConfirmation,
            title = { Text("Confirm action") },
            text = { Text("${resolved.confirmationLabel(state.snapshots[resolved.action.entityId])}?") },
            confirmButton = { TextButton(onClick = model::confirmResolvedAction) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = model::dismissConfirmation) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun Home(
    state: UiState,
    invoke: (QuickAccessAction) -> Unit,
    setup: () -> Unit,
) {
    if (state.settings.origin == null) {
        Empty("Connect to Home Assistant to add Quick Access controls.", "Set up", setup)
        return
    }
    if (state.settings.actions.isEmpty()) {
        Empty("No Quick Access actions yet.", "Manage actions", setup)
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 10.dp, bottom = HomeGridBottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.settings.actions.size) { index ->
            val action = state.settings.actions[index]
            val entity = state.snapshots[action.entityId]
            val actionOutcome = state.outcome.takeIf { state.outcomeActionId == action.id }
            ElevatedCard(
                Modifier
                    .heightIn(min = 110.dp)
                    .fillMaxWidth()
                    .testTag("quick-access-${action.id}")
                    .clickable(enabled = !state.busy && (entity?.available != false)) { invoke(action) },
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        HomeAssistantIcon(action.icon ?: entity?.icon, action.domain)
                    }
                    Text(
                        entity?.friendlyName ?: action.displayName ?: action.entityId,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!action.kind.isStatelessControl()) {
                        Text(
                            when {
                                entity == null && state.busy -> "Loading…"
                                entity == null -> "State unavailable"
                                else -> entity.displayState()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = if (entity?.available == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (action.kind in setOf(ActionKind.CONTROL_LOCK, ActionKind.CONTROL_COVER)) {
                        Text(
                            when {
                                actionOutcome != null -> actionOutcome.statusText()
                                state.busy && state.outcomeActionId == action.id -> "Checking state…"
                                else -> action.actionHint(entity)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = if (actionOutcome in setOf(ActionOutcome.FAILED, ActionOutcome.UNKNOWN) || entity?.available == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    } else if (actionOutcome != null) {
                        Text(
                            actionOutcome.statusText(),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = if (actionOutcome in setOf(ActionOutcome.FAILED, ActionOutcome.UNKNOWN)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (action.protected) {
                        Text("PIN protected", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun ActionOutcome.statusText() =
    when (this) {
        ActionOutcome.SENDING -> "Sending…"
        ActionOutcome.REQUESTED -> "Requested"
        ActionOutcome.COMPLETED -> "Completed"
        ActionOutcome.FAILED -> "Failed"
        ActionOutcome.UNKNOWN -> "Outcome uncertain"
    }

@Composable
private fun HomeAssistantIcon(
    icon: String?,
    domain: String,
) {
    val resource =
        when {
            icon?.contains("light", ignoreCase = true) == true || icon?.contains("bulb", ignoreCase = true) == true || domain == "light" -> R.drawable.ic_ha_light
            icon?.contains("script", ignoreCase = true) == true || domain == "script" -> R.drawable.ic_ha_script
            icon?.contains("button", ignoreCase = true) == true || domain == "button" -> R.drawable.ic_ha_button
            icon?.contains("scene", ignoreCase = true) == true || domain == "scene" -> R.drawable.ic_ha_scene
            icon?.contains("lock", ignoreCase = true) == true || domain == "lock" -> R.drawable.ic_ha_lock
            icon?.contains("cover", ignoreCase = true) == true || icon?.contains("garage", ignoreCase = true) == true || domain == "cover" -> R.drawable.ic_ha_cover
            icon?.contains("switch", ignoreCase = true) == true || domain == "switch" -> R.drawable.ic_ha_switch
            else -> R.drawable.ic_ha_entity
        }
    Icon(painterResource(resource), contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
}

@Composable private fun Empty(
    text: String,
    button: String,
    onClick: () -> Unit,
) = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onClick) { Text(button) }
}

@Composable private fun Setup(
    state: UiState,
    model: MainViewModel,
) {
    var url by remember(state.settings.origin) { mutableStateOf(state.settings.origin ?: "https://") }
    var pin by remember { mutableStateOf("") }
    var selectedPinMode by remember(state.settings.pinMode) { mutableStateOf(state.settings.pinMode) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var currentPin by remember { mutableStateOf("") }
    var showEraseConfirmation by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentPadding = PaddingValues(bottom = BackControlSize),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Set up Home Assistant", style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.setup_origin_help),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.setup_privacy_notice),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { OutlinedTextField(url, { url = it }, label = { Text("Home Assistant URL") }, singleLine = true) }
        item {
            if (state.settings.origin == null || state.settings.onboardingStep == OnboardingStep.CONNECT) {
                Button(onClick = { error = model.beginAuthentication(url) }) { Text(stringResource(R.string.continue_to_home_assistant)) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connected to ${state.settings.origin}", color = MaterialTheme.colorScheme.primary)
                    if (state.settings.onboardingStep == OnboardingStep.COMPLETE) {
                        Button(onClick = model::openEntityChooser) { Text("Manage Quick Access") }
                    } else {
                        Button(onClick = model::continueOnboarding) { Text("Continue setup") }
                    }
                }
            }
        }
        if (state.settings.onboardingStep == OnboardingStep.COMPLETE) {
            item { Text("Connection policy") }
            item {
                ConnectionPolicy.entries.forEach { policy ->
                    Row(Modifier.fillMaxWidth().clickable { model.savePolicy(policy) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(policy == state.settings.connectionPolicy, { model.savePolicy(policy) })
                        Column {
                            Text(policy.title())
                            Text(policy.description(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Text("PIN protection") }
            item {
                PinMode.entries.forEach { mode ->
                    val selectMode = {
                        selectedPinMode = mode
                        confirmation = null
                        error = null
                    }
                    Row(Modifier.fillMaxWidth().clickable(onClick = selectMode), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(mode == selectedPinMode, selectMode)
                        Column {
                            Text(mode.title())
                            Text(mode.description(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (selectedPinMode == PinMode.DISABLED) {
                item {
                    Text(
                        "Without PIN protection, anyone with access to this Karoo can use its Home Assistant controls.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (selectedPinMode != PinMode.DISABLED) {
                item { OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text("4–6 digit PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)) }
                item {
                    Button(onClick = {
                        model.savePinMode(selectedPinMode, pin.ifBlank { null })
                    }, enabled = !state.unlocking) { Text("Save PIN protection") }
                }
                if (state.unlocking) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Saving PIN protection…")
                        }
                    }
                }
            }
            if (selectedPinMode == PinMode.DISABLED && state.settings.pinMode != PinMode.DISABLED) {
                item { Text("Enter your current PIN to disable protection.") }
                item { OutlinedTextField(currentPin, { currentPin = it.filter(Char::isDigit).take(6) }, label = { Text("Current PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)) }
                item {
                    Button(onClick = {
                        model.disablePinProtection(currentPin)
                    }, enabled = currentPin.length in 4..6 && !state.unlocking) { Text("Disable PIN protection") }
                }
                if (state.unlocking) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Verifying PIN…")
                        }
                    }
                }
            }
        }
        item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { confirmation?.let { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item { state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) } }
        if (state.settings.origin != null) {
            item {
                Button(
                    onClick = { showEraseConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                ) { Text(stringResource(R.string.erase_account)) }
            }
        }
    }
    if (showEraseConfirmation) {
        AlertDialog(
            onDismissRequest = { showEraseConfirmation = false },
            title = { Text("Erase this account?") },
            text = { Text("This will remove the saved Home Assistant connection, Quick Access actions, and PIN. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showEraseConfirmation = false
                    model.signOutAndReset()
                }) { Text("Erase") }
            },
            dismissButton = { TextButton(onClick = { showEraseConfirmation = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OnboardingPolicy(
    state: UiState,
    model: MainViewModel,
) {
    var selected by rememberSaveable { mutableStateOf<ConnectionPolicy?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Choose connection policy", style = MaterialTheme.typography.titleMedium)
            Text("Choose how Quick Access may reach Home Assistant during normal use.")
        }
        items(ConnectionPolicy.entries) { policy ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { selected = policy }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                    RadioButton(selected == policy, { selected = policy })
                    Column(Modifier.weight(1f)) {
                        Text(policy.title())
                        Text(policy.description(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Text(
                "Companion fallback improves availability, but adds your paired phone and the Hammerhead Companion app to the connection path.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        item {
            Button(
                onClick = { selected?.let(model::saveOnboardingPolicy) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Continue") }
        }
    }
}

@Composable
private fun OnboardingPin(
    state: UiState,
    model: MainViewModel,
) {
    var selected by rememberSaveable { mutableStateOf<PinMode?>(null) }
    var pin by rememberSaveable { mutableStateOf("") }
    val needsNewPin = selected != null && selected != PinMode.DISABLED
    LazyColumn(
        Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Choose PIN protection", style = MaterialTheme.typography.titleMedium)
            Text("Choose the additional local protection applied on this Karoo.")
        }
        items(PinMode.entries) { mode ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { selected = mode }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                    RadioButton(selected == mode, { selected = mode })
                    Column(Modifier.weight(1f)) {
                        Text(mode.title())
                        Text(mode.description(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (selected == PinMode.DISABLED) {
            item {
                Text(
                    "Without PIN protection, anyone with access to this Karoo can use its Home Assistant controls.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (needsNewPin) {
            item {
                OutlinedTextField(
                    pin,
                    { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text("4–6 digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    enabled = !state.unlocking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Button(
                onClick = { selected?.let { model.saveOnboardingPinMode(it, pin.ifBlank { null }) } },
                enabled = selected != null && (!needsNewPin || pin.length in 4..6) && !state.unlocking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Continue to Quick Access") }
        }
        if (state.unlocking) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Saving PIN protection…")
                }
            }
        }
    }
}

private fun ConnectionPolicy.title() =
    when (this) {
        ConnectionPolicy.WIFI_ONLY -> "Wi-Fi only"
        ConnectionPolicy.ALLOW_COMPANION_FALLBACK -> "Allow Companion fallback"
    }

private fun ConnectionPolicy.description() =
    when (this) {
        ConnectionPolicy.WIFI_ONLY ->
            "Use only Karoo Wi-Fi. More secure, but controls are unavailable where that Wi-Fi cannot reach, such as a driveway or outside door."
        ConnectionPolicy.ALLOW_COMPANION_FALLBACK ->
            "Use your paired phone and Hammerhead Companion when Wi-Fi is absent, so Quick Access can work while riding away from home."
    }

private fun PinMode.title() =
    when (this) {
        PinMode.DISABLED -> "Disabled"
        PinMode.WHOLE_APP -> "Whole app"
        PinMode.SELECTED_ACTIONS -> "Selected actions"
    }

private fun PinMode.description() =
    when (this) {
        PinMode.DISABLED -> "Do not require additional local authorization."
        PinMode.WHOLE_APP -> "Require the PIN before Quick Access can be used."
        PinMode.SELECTED_ACTIONS -> "Require the PIN only for actions you mark as protected."
    }

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OAuthWebView(
    url: String?,
) {
    if (url == null) {
        Text("Missing Home Assistant URL", Modifier.padding(16.dp))
        return
    }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            return openOAuthCallback(context, request.url)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            url: String,
                        ): Boolean {
                            return openOAuthCallback(context, Uri.parse(url))
                        }
                    }
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun openOAuthCallback(
    context: android.content.Context,
    uri: Uri,
): Boolean {
    if (!isOAuthCallbackUri(uri.scheme, uri.host, uri.path)) return false
    Log.d("KarooHassOAuth", "Received Home Assistant authorization callback")
    context.startActivity(Intent(context, OAuthCallbackActivity::class.java).setData(uri))
    return true
}

@Composable private fun Manage(
    state: UiState,
    model: MainViewModel,
) {
    var selected by remember { mutableStateOf<EntitySnapshot?>(null) }
    var protect by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedDomain by rememberSaveable { mutableStateOf<String?>(null) }
    val entities = remember(state.snapshots) { state.snapshots.values.sortedBy { it.friendlyName } }
    val domains = remember(entities) { entities.map { it.domain }.distinct() }
    val filteredEntities =
        remember(entities, query, selectedDomain) {
            entities.filter { entity ->
                (selectedDomain == null || entity.domain == selectedDomain) &&
                    (query.isBlank() || entity.friendlyName.contains(query, ignoreCase = true) || entity.entityId.contains(query, ignoreCase = true))
            }
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        contentPadding = PaddingValues(bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Choose an entity", style = MaterialTheme.typography.titleLarge)
            Text("Select an entity to add a Quick Access action.")
            Button(onClick = model::discover, enabled = state.canDiscoverEntities) { Text(if (state.busy) "Loading…" else "Refresh entities") }
            if (!state.wifiAvailable) Text("Connect to Wi-Fi to refresh available entities. Quick Access actions can still use Companion fallback.")
        }
        item { OutlinedTextField(query, { query = it }, label = { Text("Search entities") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = selectedDomain == null, onClick = { selectedDomain = null }, label = { Text("All") }) }
                items(domains) { domain ->
                    FilterChip(selected = selectedDomain == domain, onClick = { selectedDomain = domain }, label = { Text(domain.replaceFirstChar(Char::uppercase)) })
                }
            }
        }
        if (state.showNoSupportedEntities) item { Text("No supported entities found.") }
        if (!state.busy && entities.isNotEmpty() && filteredEntities.isEmpty()) item { Text("No entities match these filters.") }
        items(filteredEntities, key = { it.entityId }) { entity ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { selected = entity }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HomeAssistantIcon(entity.icon, entity.domain)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entity.friendlyName)
                        Text("${entity.domain} • ${entity.state}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Text("Configured Quick Access actions")
        }
        items(state.settings.actions.size) { index ->
            val action = state.settings.actions[index]
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HomeAssistantIcon(action.icon, action.domain)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                action.label(state.snapshots[action.entityId]),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (state.snapshots.containsKey(action.entityId)) "Configured" else "Entity unavailable",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { model.move(action, -1) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            enabled = index > 0,
                        ) { Text("Move up", maxLines = 1) }
                        TextButton(
                            onClick = { model.move(action, 1) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            enabled = index < state.settings.actions.lastIndex,
                        ) { Text("Move down", maxLines = 1) }
                    }
                    TextButton(
                        onClick = { model.remove(action) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Remove from Quick Access") }
                }
            }
        }
    }
    selected?.let { entity ->
        ActionPicker(entity, protect, confirm, { protect = it }, { confirm = it }, { kind ->
            model.add(entity, kind, protect, confirm)
            selected = null
        }, entity.availableActionKinds().any { kind -> hasActionIdentity(state.settings.actions, entity.entityId, kind) }, { selected = null })
    }
}

@Composable
internal fun ActionPicker(
    entity: EntitySnapshot,
    protect: Boolean,
    confirmation: Boolean,
    setProtect: (Boolean) -> Unit,
    setConfirmation: (Boolean) -> Unit,
    add: (ActionKind) -> Unit,
    alreadyAdded: Boolean,
    dismiss: () -> Unit,
) {
    val kinds = entity.availableActionKinds()
    val singleKind = kinds.singleOrNull()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(entity.friendlyName) },
        text = {
            Column {
                Text(
                    stringResource(
                        when {
                            kinds.isEmpty() -> R.string.action_picker_no_supported_actions
                            singleKind == ActionKind.TOGGLE -> R.string.action_picker_add_toggle
                            singleKind == ActionKind.PRESS_BUTTON -> R.string.action_picker_add_button_press
                            singleKind == ActionKind.ACTIVATE_SCENE -> R.string.action_picker_add_scene_activation
                            singleKind == ActionKind.CONTROL_LOCK -> R.string.action_picker_add_lock_control
                            singleKind == ActionKind.CONTROL_COVER -> R.string.action_picker_add_cover_control
                            else -> R.string.action_picker_choose_operation
                        },
                    ),
                )
                if (kinds.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(protect, setProtect, modifier = Modifier.testTag("action-picker-protect"))
                        Text(stringResource(R.string.action_picker_require_pin))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(confirmation, setConfirmation, modifier = Modifier.testTag("action-picker-confirm"))
                        Text(stringResource(R.string.action_picker_confirm_action))
                    }
                    if (singleKind == ActionKind.CONTROL_LOCK) Text(stringResource(R.string.action_picker_unlock_always_confirmed), style = MaterialTheme.typography.bodySmall)
                    if (singleKind == ActionKind.CONTROL_COVER) Text(stringResource(R.string.action_picker_open_always_confirmed), style = MaterialTheme.typography.bodySmall)
                }
                if (alreadyAdded) Text(stringResource(R.string.action_picker_already_added), color = MaterialTheme.colorScheme.error)
                if (singleKind == null) kinds.forEach { kind -> TextButton(onClick = { add(kind) }) { Text(stringResource(kind.labelResource())) } }
            }
        },
        confirmButton = {
            if (singleKind != null) {
                TextButton(
                    onClick = { add(singleKind) },
                    enabled = !alreadyAdded,
                    modifier = Modifier.testTag("action-picker-add"),
                ) { Text(stringResource(R.string.action_picker_add)) }
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_picker_cancel)) } },
    )
}

@StringRes
private fun ActionKind.labelResource() =
    when (this) {
        ActionKind.RUN_SCRIPT -> R.string.action_kind_run
        ActionKind.PRESS_BUTTON -> R.string.action_kind_press
        ActionKind.ACTIVATE_SCENE -> R.string.action_kind_activate
        ActionKind.CONTROL_LOCK -> R.string.action_kind_control_lock
        ActionKind.CONTROL_COVER -> R.string.action_kind_control_cover
        ActionKind.TOGGLE -> R.string.action_kind_toggle
    }

@Composable private fun PinEntry(
    state: UiState,
    model: MainViewModel,
) {
    var pin by remember { mutableStateOf("") }
    val unlock = {
        if (pin.length in 4..6) {
            model.submitPin(pin)
            pin = ""
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter PIN")
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(6) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { unlock() }),
            enabled = !state.unlocking,
        )
        Button(onClick = unlock, enabled = pin.length in 4..6 && !state.unlocking) { Text("Unlock") }
        if (state.unlocking) CircularProgressIndicator(Modifier.padding(top = 16.dp))
        state.pending?.let { Text(it.confirmationLabel(state.snapshots[it.action.entityId])) }
    }
}

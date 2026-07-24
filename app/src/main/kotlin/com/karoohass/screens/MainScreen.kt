package com.karoohass.screens

import android.content.Intent
import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.karoohass.MainViewModel
import com.karoohass.R
import com.karoohass.Screen
import com.karoohass.UiState
import com.karoohass.auth.OAuthCallbackActivity
import com.karoohass.core.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: UiState, model: MainViewModel) {
    var confirm by remember { mutableStateOf<QuickAccessAction?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Home Assistant") }, actions = { if (state.screen == Screen.HOME && state.settings.origin != null) TextButton(onClick = model::openManage) { Text("Manage") } }) },
        bottomBar = {
            if (state.screen != Screen.HOME) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 6.dp)) {
                    FilledTonalButton(onClick = model::home) { Text("Back") }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.screen) {
                Screen.HOME -> Home(state, { action -> if (action.requiresConfirmation) confirm = action else model.invoke(action) }, model::openSetup)
                Screen.SETUP -> Setup(state, model)
                Screen.AUTH -> OAuthWebView(model.currentAuthorizationUrl(), model::receiveOAuthCallback)
                Screen.MANAGE -> Manage(state, model)
                Screen.PIN -> PinEntry(state, model)
            }
            state.message?.let { Text(it, Modifier.align(Alignment.BottomCenter).padding(12.dp), color = MaterialTheme.colorScheme.error) }
        }
    }
    confirm?.let { action -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Confirm action") }, text = { Text("${action.label(state.snapshots[action.entityId])}?") }, confirmButton = { TextButton(onClick = { confirm = null; model.invoke(action) }) { Text("Confirm") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }) }
}

@Composable private fun Home(state: UiState, invoke: (QuickAccessAction) -> Unit, setup: () -> Unit) {
    if (state.settings.origin == null) { Empty("Connect to Home Assistant to add Quick Access controls.", "Set up", setup); return }
    if (state.settings.actions.isEmpty()) { Empty("No Quick Access actions yet.", "Manage actions", setup); return }
    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(state.settings.actions.size) { index -> val action = state.settings.actions[index]; val entity = state.snapshots[action.entityId]; ElevatedCard(Modifier.heightIn(min = 110.dp).fillMaxWidth().clickable(enabled = !state.busy && (entity?.available != false)) { invoke(action) }) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) { Text(action.kind.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelMedium); Text(entity?.friendlyName ?: action.entityId, style = MaterialTheme.typography.titleMedium); Text(when { entity == null -> "State unavailable"; !entity.available -> "Unavailable"; state.busy -> "Sending…"; else -> entity.state }, color = if (entity?.available == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant); if (action.protected) Text("PIN protected", style = MaterialTheme.typography.labelSmall) } } } }
}

@Composable private fun Empty(text: String, button: String, onClick: () -> Unit) = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(text); Spacer(Modifier.height(16.dp)); Button(onClick = onClick) { Text(button) } }

@Composable private fun Setup(state: UiState, model: MainViewModel) {
    var url by remember(state.settings.origin) { mutableStateOf(state.settings.origin ?: "https://") }; var pin by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Set up Home Assistant", style = MaterialTheme.typography.headlineSmall); Text("Use an externally reachable HTTPS address trusted by Karoo.") }; item { OutlinedTextField(url, { url = it }, label = { Text("Home Assistant URL") }, singleLine = true) }; item { if (state.settings.origin == null) Button(onClick = { error = model.beginAuthentication(url) }) { Text("Sign in with Home Assistant") } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Connected to ${state.settings.origin}", color = MaterialTheme.colorScheme.primary); Button(onClick = model::openManage) { Text("Manage Quick Access") } } }; item { Text("Connection policy") }; item { ConnectionPolicy.entries.forEach { policy -> Row(Modifier.fillMaxWidth().clickable { model.savePolicy(policy) }, verticalAlignment = Alignment.CenterVertically) { RadioButton(policy == state.settings.connectionPolicy, { model.savePolicy(policy) }); Text(if (policy == ConnectionPolicy.WIFI_ONLY) "Wi-Fi only" else "Allow Companion fallback") } } }; item { Text("PIN protection") }; item { PinMode.entries.forEach { mode -> Row(Modifier.fillMaxWidth().clickable { error = model.savePinMode(mode, pin.ifBlank { null }) }, verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == state.settings.pinMode, { error = model.savePinMode(mode, pin.ifBlank { null }) }); Text(mode.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) } } }; item { if (state.settings.pinMode != PinMode.DISABLED || pin.isNotBlank()) OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text("4–6 digit PIN") }, singleLine = true) }; item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }; item { TextButton(onClick = model::signOutAndReset) { Text("Forgot PIN / erase this account", color = MaterialTheme.colorScheme.error) } } }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OAuthWebView(url: String?, onCallback: (Uri) -> Unit) {
    if (url == null) {
        Text("Missing Home Assistant URL", Modifier.padding(16.dp))
        return
    }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        return openOAuthCallback(context, request.url, onCallback)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        return openOAuthCallback(context, Uri.parse(url), onCallback)
                    }
                }
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun openOAuthCallback(context: android.content.Context, uri: Uri, onCallback: (Uri) -> Unit): Boolean {
    val expected = Uri.parse(context.getString(R.string.oauth_redirect_uri))
    val isHttpsCallback = uri.scheme == expected.scheme && uri.host == expected.host && uri.path == expected.path
    val isCustomCallback = uri.scheme == "karoohass" && uri.host == "auth-callback"
    if (!isHttpsCallback && !isCustomCallback) return false
    if (isHttpsCallback) {
        Log.d("KarooHassOAuth", "Received Home Assistant authorization callback")
        onCallback(uri)
        return true
    }
    val callback = Uri.Builder()
        .scheme("karoohass")
        .authority("auth-callback")
        .encodedQuery(uri.encodedQuery)
        .build()
    Log.d("KarooHassOAuth", "Received fallback Home Assistant authorization callback")
    context.startActivity(Intent(context, OAuthCallbackActivity::class.java).setData(callback))
    return true
}

@Composable private fun Manage(state: UiState, model: MainViewModel) {
    var selected by remember { mutableStateOf<EntitySnapshot?>(null) }; var protect by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }
    val entities = remember(state.snapshots) { state.snapshots.values.sortedBy { it.friendlyName } }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("Quick Access management", style = MaterialTheme.typography.titleLarge); Button(onClick = model::discover, enabled = !state.busy) { Text(if (state.busy) "Loading…" else "Refresh entities") } }; item { Text("Configured actions") }; items(state.settings.actions.size) { index -> val action = state.settings.actions[index]; ListItem(headlineContent = { Text(action.label(state.snapshots[action.entityId])) }, supportingContent = { Text(if (state.snapshots.containsKey(action.entityId)) "Configured" else "Entity unavailable") }, trailingContent = { Row { TextButton(onClick = { model.move(action, -1) }) { Text("↑") }; TextButton(onClick = { model.move(action, 1) }) { Text("↓") }; TextButton(onClick = { model.remove(action) }) { Text("Remove") } } }) }; item { HorizontalDivider(); Text("Add action") }; items(entities.size) { index -> val entity = entities[index]; ElevatedCard(Modifier.fillMaxWidth().clickable { selected = entity }) { ListItem(headlineContent = { Text(entity.friendlyName) }, supportingContent = { Text("${entity.domain} • ${entity.state}") }) } } }
    selected?.let { entity -> ActionPicker(entity, protect, confirm, { protect = it }, { confirm = it }, { kind -> model.add(entity, kind, protect, confirm); selected = null }, { selected = null }) }
}

@Composable private fun ActionPicker(entity: EntitySnapshot, protect: Boolean, confirmation: Boolean, setProtect: (Boolean) -> Unit, setConfirmation: (Boolean) -> Unit, add: (ActionKind) -> Unit, dismiss: () -> Unit) { val kinds = when (entity.domain) { "script" -> listOf(ActionKind.RUN_SCRIPT); "lock" -> listOf(ActionKind.LOCK, ActionKind.UNLOCK); "cover" -> listOf(ActionKind.OPEN_COVER, ActionKind.CLOSE_COVER, ActionKind.STOP_COVER); else -> listOf(ActionKind.TURN_ON, ActionKind.TURN_OFF) }; AlertDialog(onDismissRequest = dismiss, title = { Text(entity.friendlyName) }, text = { Column { Text("Choose operation"); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(protect, setProtect); Text("Require PIN") }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(confirmation, setConfirmation); Text("Confirm action") }; kinds.forEach { kind -> TextButton(onClick = { add(kind) }) { Text(kind.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }) }

@Composable private fun PinEntry(state: UiState, model: MainViewModel) { var pin by remember { mutableStateOf("") }; Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("Enter PIN"); OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, singleLine = true); Button(onClick = { model.submitPin(pin) }, enabled = pin.length in 4..6) { Text("Unlock") }; state.pending?.let { Text(it.label(state.snapshots[it.entityId])) } } }

package com.karoohass.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.appDataStore by preferencesDataStore("karoo_hass_settings")

class SettingsStore(private val context: Context) {
    private val settingsKey = stringPreferencesKey("settings")
    val settings: Flow<AppSettings> = context.appDataStore.data.map { decodeSettings(it[settingsKey]) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { prefs ->
            prefs[settingsKey] = encodeSettings(transform(decodeSettings(prefs[settingsKey]))).toString()
        }
    }
}

internal fun decodeSettings(value: String?): AppSettings =
    runCatching {
        if (value == null) return AppSettings()
        val root = JSONObject(value)
        val actions = root.optJSONArray("actions") ?: JSONArray()
        AppSettings(
            origin = root.optString("origin").takeIf { it.isNotBlank() },
            connectionPolicy = ConnectionPolicy.valueOf(root.optString("policy", ConnectionPolicy.WIFI_ONLY.name)),
            pinMode = PinMode.valueOf(root.optString("pinMode", PinMode.DISABLED.name)),
            actions =
                List(actions.length()) { i ->
                    actions.getJSONObject(i).let { item ->
                        QuickAccessAction(
                            id = item.getString("id"),
                            entityId = item.getString("entityId"),
                            domain = item.getString("domain"),
                            kind = ActionKind.valueOf(item.getString("kind")),
                            protected = item.optBoolean("protected"),
                            requiresConfirmation = item.optBoolean("confirm"),
                            icon = item.optString("icon").takeIf { it.isNotBlank() },
                            order = item.optLong("order"),
                            displayName = item.optString("displayName").takeIf { it.isNotBlank() },
                            targetPosition = item.optInt("targetPosition", -1).takeIf { it in 1..99 },
                        )
                    }
                }.sortedBy { it.order },
            onboardingStep =
                if (root.has("onboardingStep")) {
                    OnboardingStep.valueOf(root.getString("onboardingStep"))
                } else {
                    // Settings written by versions without guided onboarding belong to an
                    // existing installation and must not trigger the fresh-install wizard.
                    OnboardingStep.COMPLETE
                },
        )
    }.getOrDefault(AppSettings())

internal fun encodeSettings(settings: AppSettings) =
    JSONObject().apply {
        put("origin", settings.origin)
        put("policy", settings.connectionPolicy.name)
        put("pinMode", settings.pinMode.name)
        put("onboardingStep", settings.onboardingStep.name)
        put(
            "actions",
            JSONArray().apply {
                settings.actions.forEach { action ->
                    put(
                        JSONObject().apply {
                            put("id", action.id)
                            put("entityId", action.entityId)
                            put("domain", action.domain)
                            put("kind", action.kind.name)
                            put("protected", action.protected)
                            put("confirm", action.requiresConfirmation)
                            put("icon", action.icon)
                            put("order", action.order)
                            put("displayName", action.displayName)
                            action.targetPosition?.let { put("targetPosition", it) }
                        },
                    )
                }
            },
        )
    }

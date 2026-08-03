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
    val settings: Flow<AppSettings> = context.appDataStore.data.map { it[settingsKey]?.let(::decode) ?: AppSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { prefs ->
            prefs[settingsKey] = encode(transform(decode(prefs[settingsKey]))).toString()
        }
    }

    private fun decode(value: String?): AppSettings =
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
                            QuickAccessAction(item.getString("id"), item.getString("entityId"), item.getString("domain"), ActionKind.valueOf(item.getString("kind")), item.optBoolean("protected"), item.optBoolean("confirm"), item.optString("icon").takeIf { it.isNotBlank() }, item.optLong("order"), item.optString("displayName").takeIf { it.isNotBlank() })
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

    private fun encode(settings: AppSettings) =
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
                            },
                        )
                    }
                },
            )
        }
}

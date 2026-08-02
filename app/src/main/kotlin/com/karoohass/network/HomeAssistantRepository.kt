package com.karoohass.network

import com.karoohass.core.ActionOutcome
import com.karoohass.core.EntitySnapshot
import com.karoohass.core.QuickAccessAction
import com.karoohass.core.expectedState
import com.karoohass.core.serviceName
import com.karoohass.security.TokenStore
import org.json.JSONArray
import org.json.JSONObject

class HomeAssistantRepository(
    private val origin: () -> String?,
    private val transport: HttpTransport,
    private val tokens: TokenStore,
    private val refresh: suspend () -> Boolean,
) {
    suspend fun discover(): List<EntitySnapshot> {
        val response = request("GET", "/api/states")
        response.error?.let { throw TransportException.Failure(it) }
        if (response.code !in 200..299) throw TransportException.Failure("Home Assistant returned HTTP ${response.code}")
        val body = response.body ?: throw TransportException.Failure("Home Assistant returned an empty response")
        return parseStates(JSONArray(String(body)))
    }

    suspend fun refresh(entityId: String): EntitySnapshot? = runCatching { request("GET", "/api/states/$entityId").body?.let { parseState(JSONObject(String(it))) } }.getOrNull()

    suspend fun execute(action: QuickAccessAction): ActionOutcome {
        val response =
            try {
                request("POST", "/api/services/${action.domain}/${action.kind.serviceName()}", JSONObject().put("entity_id", action.entityId).toString().toByteArray())
            } catch (_: TransportException) {
                return ActionOutcome.UNKNOWN
            }
        return if (response.code in 200..299) {
            if (action.kind.expectedState() == null) {
                ActionOutcome.REQUESTED
            } else {
                ActionOutcome.SENDING
            }
        } else {
            ActionOutcome.FAILED
        }
    }

    suspend fun verify(action: QuickAccessAction): ActionOutcome {
        val expected = action.kind.expectedState() ?: return ActionOutcome.REQUESTED
        return if (awaitState(action.entityId) { it.state == expected } != null) ActionOutcome.COMPLETED else ActionOutcome.UNKNOWN
    }

    suspend fun awaitState(
        entityId: String,
        matches: (EntitySnapshot) -> Boolean,
    ): EntitySnapshot? {
        repeat(8) {
            refresh(entityId)?.let { snapshot -> if (matches(snapshot)) return snapshot }
            kotlinx.coroutines.delay(500)
        }
        return refresh(entityId)?.takeIf(matches)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: ByteArray? = null,
    ): HttpResponse {
        val base = origin() ?: throw TransportException.Failure("Home Assistant is not configured")
        var token = tokens.load()?.accessToken ?: throw TransportException.Failure("Sign in is required")
        var response = transport.execute(HttpRequest(method, "$base$path", mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json"), body))
        if (response.code == 401) {
            val refreshed = refresh()
            // Service POSTs are never replayed automatically. The refreshed token is
            // retained so the user can deliberately invoke the action again.
            if (refreshed && method in setOf("GET", "HEAD")) {
                token = tokens.load()?.accessToken ?: token
                response = transport.execute(HttpRequest(method, "$base$path", mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json"), body))
            }
        }
        return response
    }

    private fun parseStates(items: JSONArray) = List(items.length()) { parseState(items.getJSONObject(it)) }.filter { it.domain in supportedDomains }

    private fun parseState(item: JSONObject): EntitySnapshot {
        val attributes = item.optJSONObject("attributes") ?: JSONObject()
        val id = item.getString("entity_id")
        return EntitySnapshot(id, id.substringBefore('.'), item.optString("state"), attributes.optInt("supported_features"), item.optString("state") !in setOf("unknown", "unavailable"), item.optString("last_updated"), attributes.optString("friendly_name", id), attributes.optString("icon").takeIf { it.isNotBlank() })
    }

    companion object {
        val supportedDomains = setOf("script", "lock", "cover", "light", "switch")
    }
}

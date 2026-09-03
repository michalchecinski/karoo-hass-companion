package com.karoohass.network

import com.karoohass.core.ActionOutcome
import com.karoohass.core.EntitySnapshot
import com.karoohass.core.ResolvedAction
import com.karoohass.security.Tokens
import org.json.JSONArray
import org.json.JSONObject

class HomeAssistantRepository(
    private val origin: () -> String?,
    private val transport: HttpTransport,
    private val tokens: () -> Tokens?,
    private val refresh: suspend () -> Boolean,
) {
    /** Safely verifies that the configured Home Assistant API can be reached. */
    suspend fun isReachable(): Boolean {
        val response = request("GET", "/api/")
        return response.error == null && response.code in 200..299
    }

    suspend fun discover(): List<EntitySnapshot> {
        val response = request("GET", "/api/states")
        response.error?.let { throw TransportException.Failure(it) }
        if (response.code !in 200..299) throw TransportException.Failure("Home Assistant returned HTTP ${response.code}")
        val body = response.body ?: throw TransportException.Failure("Home Assistant returned an empty response")
        return parseStates(JSONArray(String(body)))
    }

    suspend fun refresh(entityId: String): EntitySnapshot? = runCatching { request("GET", "/api/states/$entityId").body?.let { parseState(JSONObject(String(it))) } }.getOrNull()

    suspend fun execute(action: ResolvedAction): ActionOutcome {
        val response =
            try {
                request(
                    "POST",
                    "/api/services/${action.action.domain}/${action.serviceName}",
                    JSONObject()
                        .put("entity_id", action.action.entityId)
                        .apply { action.targetPosition?.let { put("position", it) } }
                        .toString()
                        .toByteArray(),
                )
            } catch (_: TransportException) {
                return ActionOutcome.UNKNOWN
            }
        return if (response.error != null) {
            ActionOutcome.UNKNOWN
        } else if (response.code in 200..299) {
            // The REST API returns an array for accepted service calls. Treat a missing or
            // malformed success body as uncertain rather than claiming that it was accepted.
            val responseBody = response.body ?: return ActionOutcome.UNKNOWN
            if (runCatching { JSONArray(String(responseBody, Charsets.UTF_8)) }.isFailure) return ActionOutcome.UNKNOWN
            if (action.expectedState == null) {
                ActionOutcome.REQUESTED
            } else {
                ActionOutcome.SENDING
            }
        } else {
            ActionOutcome.FAILED
        }
    }

    suspend fun verify(action: ResolvedAction): ActionOutcome {
        val expected = action.expectedState ?: return ActionOutcome.REQUESTED
        return if (awaitState(action.action.entityId, { it.state == expected }) != null) ActionOutcome.COMPLETED else ActionOutcome.UNKNOWN
    }

    suspend fun awaitState(
        entityId: String,
        matches: (EntitySnapshot) -> Boolean,
        onSnapshot: (EntitySnapshot) -> Unit = {},
    ): EntitySnapshot? {
        repeat(8) {
            refresh(entityId)?.let { snapshot ->
                onSnapshot(snapshot)
                if (matches(snapshot)) return snapshot
            }
            kotlinx.coroutines.delay(500)
        }
        return refresh(entityId)?.also(onSnapshot)?.takeIf(matches)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: ByteArray? = null,
    ): HttpResponse {
        val base = origin() ?: throw TransportException.Failure("Home Assistant is not configured")
        var token = tokens()?.accessToken ?: throw TransportException.Failure("Sign in is required")
        var response = transport.execute(HttpRequest(method, "$base$path", mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json"), body))
        if (response.code == 401) {
            val refreshed = refresh()
            // Service POSTs are never replayed automatically. The refreshed token is
            // retained so the user can deliberately invoke the action again.
            if (refreshed && method in setOf("GET", "HEAD")) {
                token = tokens()?.accessToken ?: token
                response = transport.execute(HttpRequest(method, "$base$path", mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json"), body))
            }
        }
        return response
    }

    private fun parseStates(items: JSONArray) = List(items.length()) { parseState(items.getJSONObject(it)) }.filter { it.domain in supportedDomains }

    private fun parseState(item: JSONObject): EntitySnapshot {
        val attributes = item.optJSONObject("attributes") ?: JSONObject()
        val id = item.getString("entity_id")
        val domain = id.substringBefore('.')
        val state = item.optString("state")
        val available = state != "unavailable" && (state != "unknown" || domain in statelessDomains)
        return EntitySnapshot(
            entityId = id,
            domain = domain,
            state = state,
            supportedFeatures = attributes.optInt("supported_features"),
            available = available,
            lastUpdated = item.optString("last_updated"),
            friendlyName = attributes.optString("friendly_name", id),
            icon = attributes.optString("icon").takeIf { it.isNotBlank() },
            currentPosition = parseCoverPosition(attributes.opt("current_position")),
        )
    }

    private fun parseCoverPosition(value: Any?): Int? {
        val number = value as? Number ?: return null
        val decimal = number.toDouble()
        if (!decimal.isFinite() || decimal % 1 != 0.0) return null
        return decimal.toInt().takeIf { it in 0..100 }
    }

    companion object {
        val supportedDomains = setOf("script", "button", "scene", "lock", "cover", "light", "switch")
        private val statelessDomains = setOf("script", "button", "scene")
    }
}

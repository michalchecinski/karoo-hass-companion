package com.karoohass.network

import com.karoohass.core.ActionKind
import com.karoohass.core.ActionOutcome
import com.karoohass.core.QuickAccessAction
import com.karoohass.core.resolve
import com.karoohass.security.Tokens
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAssistantRepositoryTest {
    @Test
    fun `reachability check authenticates the safe API request`() =
        runBlocking {
            val transport = RecordingTransport(HttpResponse(200, emptyMap(), "ok".toByteArray()))
            val savedTokens = Tokens("probe-" + "credential", null, 0)
            val repository = HomeAssistantRepository({ "https://home.example" }, transport, { savedTokens }) { false }

            assertTrue(repository.isReachable())
            assertEquals("GET", transport.requests.single().method)
            assertTrue(transport.requests.single().url.endsWith("/api/"))
            assertEquals("Bearer ${savedTokens.accessToken}", transport.requests.single().headers["Authorization"])
        }

    @Test
    fun `reachability check rejects HTTP errors and transport error responses`() =
        runBlocking {
            assertFalse(repository(RecordingTransport(HttpResponse(503, emptyMap(), null))).isReachable())
            assertFalse(repository(RecordingTransport(HttpResponse(200, emptyMap(), null, error = "offline"))).isReachable())
        }

    @Test
    fun `button press uses the standard service and entity only payload`() =
        runBlocking {
            val transport = RecordingTransport(HttpResponse(200, emptyMap(), "[]".toByteArray()))
            val result = repository(transport).execute(resolved(ActionKind.PRESS_BUTTON, "button.garage_remote"))

            assertEquals(ActionOutcome.REQUESTED, result)
            assertEquals("POST", transport.requests.single().method)
            assertTrue(transport.requests.single().url.endsWith("/api/services/button/press"))
            assertEquals("{\"entity_id\":\"button.garage_remote\"}", String(transport.requests.single().body!!))
        }

    @Test
    fun `scene activation uses the standard service`() =
        runBlocking {
            val transport = RecordingTransport(HttpResponse(200, emptyMap(), "[]".toByteArray()))

            assertEquals(
                ActionOutcome.REQUESTED,
                repository(transport).execute(resolved(ActionKind.ACTIVATE_SCENE, "scene.arrive_home")),
            )
            assertTrue(transport.requests.single().url.endsWith("/api/services/scene/turn_on"))
        }

    @Test
    fun `rejection is failed while malformed and transport responses are uncertain`() =
        runBlocking {
            assertEquals(ActionOutcome.FAILED, execute(HttpResponse(403, emptyMap(), "no".toByteArray())))
            assertEquals(ActionOutcome.UNKNOWN, execute(HttpResponse(200, emptyMap(), "{}".toByteArray())))
            assertEquals(ActionOutcome.UNKNOWN, execute(HttpResponse(200, emptyMap(), null)))
        }

    @Test
    fun `401 refreshes credentials without replaying service post`() =
        runBlocking {
            val transport = RecordingTransport(HttpResponse(401, emptyMap(), "[]".toByteArray()))
            var refreshed = false
            val repo =
                HomeAssistantRepository(
                    { "https://home.example" },
                    transport,
                    { Tokens("token", null, 0) },
                ) {
                    refreshed = true
                    true
                }

            assertEquals(ActionOutcome.FAILED, repo.execute(resolved(ActionKind.PRESS_BUTTON, "button.test")))
            assertTrue(refreshed)
            assertEquals(1, transport.requests.size)
        }

    @Test
    fun `stateless entities reporting unknown remain available`() =
        runBlocking {
            val states =
                """
                [
                  {"entity_id":"script.prepare_home","state":"unknown","attributes":{}},
                  {"entity_id":"button.garage_remote","state":"unknown","attributes":{}},
                  {"entity_id":"scene.arrive_home","state":"unknown","attributes":{}},
                  {"entity_id":"button.offline","state":"unavailable","attributes":{}},
                  {"entity_id":"lock.front_door","state":"unknown","attributes":{}}
                ]
                """.trimIndent().toByteArray()
            val entities = repository(RecordingTransport(HttpResponse(200, emptyMap(), states))).discover().associateBy { it.entityId }

            assertTrue(entities.getValue("script.prepare_home").available)
            assertTrue(entities.getValue("button.garage_remote").available)
            assertTrue(entities.getValue("scene.arrive_home").available)
            assertFalse(entities.getValue("button.offline").available)
            assertFalse(entities.getValue("lock.front_door").available)
        }

    private fun repository(transport: HttpTransport) =
        HomeAssistantRepository({ "https://home.example" }, transport, { Tokens("token", null, 0) }) { false }

    private suspend fun execute(response: HttpResponse) =
        repository(RecordingTransport(response)).execute(resolved(ActionKind.PRESS_BUTTON, "button.test"))

    private fun resolved(
        kind: ActionKind,
        entityId: String,
    ) =
        QuickAccessAction("id", entityId, entityId.substringBefore('.'), kind).resolve(null)!!

    private class RecordingTransport(private val response: HttpResponse) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return response
        }
    }
}

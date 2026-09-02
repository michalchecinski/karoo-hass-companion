package com.karoohass.network

import com.karoohass.core.ActionKind
import com.karoohass.core.ActionOutcome
import com.karoohass.core.QuickAccessAction
import com.karoohass.core.resolve
import com.karoohass.security.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

class HomeAssistantRepositoryMockWebServerTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `button press posts the standard path and entity only payload`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

            assertEquals(ActionOutcome.REQUESTED, repository().execute(resolved(ActionKind.PRESS_BUTTON, "button.garage_remote")))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/services/button/press", request.path)
            assertEquals("Bearer access-token", request.getHeader("Authorization"))
            assertEquals("{\"entity_id\":\"button.garage_remote\"}", request.body.readUtf8())
        }

    @Test
    fun `scene activation posts the standard path and entity only payload`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

            assertEquals(ActionOutcome.REQUESTED, repository().execute(resolved(ActionKind.ACTIVATE_SCENE, "scene.arrive_home")))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/services/scene/turn_on", request.path)
            assertEquals("{\"entity_id\":\"scene.arrive_home\"}", request.body.readUtf8())
        }

    @Test
    fun `empty successful array is requested`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

            assertEquals(ActionOutcome.REQUESTED, repository().execute(resolved(ActionKind.PRESS_BUTTON, "button.test")))
        }

    @Test
    fun `definite rejection is failed`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))

            assertEquals(ActionOutcome.FAILED, repository().execute(resolved(ActionKind.ACTIVATE_SCENE, "scene.test")))
        }

    @Test
    fun `malformed and missing successful bodies are uncertain`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.enqueue(MockResponse().setResponseCode(200))

            assertEquals(ActionOutcome.UNKNOWN, repository().execute(resolved(ActionKind.PRESS_BUTTON, "button.malformed")))
            assertEquals(ActionOutcome.UNKNOWN, repository().execute(resolved(ActionKind.ACTIVATE_SCENE, "scene.empty")))
        }

    @Test
    fun `interrupted connection is uncertain`() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            assertEquals(ActionOutcome.UNKNOWN, repository().execute(resolved(ActionKind.PRESS_BUTTON, "button.interrupted")))
        }

    @Test
    fun `401 refreshes credentials but makes exactly one service post`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(401).setBody("[]"))
            var refreshes = 0
            val repository =
                repository {
                    refreshes += 1
                    true
                }

            assertEquals(ActionOutcome.FAILED, repository.execute(resolved(ActionKind.PRESS_BUTTON, "button.expired")))
            assertEquals(1, refreshes)
            assertEquals(1, server.requestCount)
            assertEquals("/api/services/button/press", server.takeRequest().path)
        }

    private fun repository(refresh: suspend () -> Boolean = { false }) =
        HomeAssistantRepository(
            origin = { server.url("/").toString().removeSuffix("/") },
            transport = UrlConnectionTransport(),
            tokens = { Tokens("access-token", null, 0) },
            refresh = refresh,
        )

    private fun resolved(
        kind: ActionKind,
        entityId: String,
    ) =
        QuickAccessAction("id", entityId, entityId.substringBefore('.'), kind).resolve(null)!!

    private class UrlConnectionTransport : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse =
            withContext(Dispatchers.IO) {
                try {
                    val connection = URL(request.url).openConnection() as HttpURLConnection
                    connection.requestMethod = request.method
                    connection.connectTimeout = 2_000
                    connection.readTimeout = 2_000
                    request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                    request.body?.let { body ->
                        connection.doOutput = true
                        connection.outputStream.use { it.write(body) }
                    }
                    val code = connection.responseCode
                    val stream = if (code >= 400) connection.errorStream else connection.inputStream
                    val body = stream?.let { input -> BufferedInputStream(input).use { it.readBytes() } }
                    HttpResponse(code, emptyMap(), body)
                } catch (error: Exception) {
                    throw TransportException.Failure(error.message ?: "Mock server request failed")
                }
            }
    }
}

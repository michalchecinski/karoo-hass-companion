package com.karoohass.network

import com.karoohass.core.ConnectionPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyTransportTest {
    @Test fun `Wi-Fi only policy uses direct transport`() =
        runBlocking {
            val direct = RecordingTransport(HttpResponse(200, emptyMap(), null))
            val companion = RecordingTransport(HttpResponse(201, emptyMap(), null))
            val transport = PolicyTransport({ ConnectionPolicy.WIFI_ONLY }, direct, companion)
            val request = HttpRequest("GET", "https://home.example/api/states")

            assertEquals(200, transport.execute(request).code)
            assertEquals(listOf(request), direct.requests)
            assertEquals(emptyList<HttpRequest>(), companion.requests)
        }

    @Test fun `Companion fallback policy uses Karoo transport`() =
        runBlocking {
            val direct = RecordingTransport(HttpResponse(200, emptyMap(), null))
            val companion = RecordingTransport(HttpResponse(201, emptyMap(), null))
            val transport = PolicyTransport({ ConnectionPolicy.ALLOW_COMPANION_FALLBACK }, direct, companion)
            val request = HttpRequest("GET", "https://home.example/api/states")

            assertEquals(201, transport.execute(request).code)
            assertEquals(emptyList<HttpRequest>(), direct.requests)
            assertEquals(listOf(request), companion.requests)
        }

    private class RecordingTransport(private val response: HttpResponse) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return response
        }
    }
}

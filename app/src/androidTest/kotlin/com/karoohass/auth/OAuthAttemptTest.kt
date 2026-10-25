package com.karoohass.auth

import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.karoohass.network.HttpRequest
import com.karoohass.network.HttpResponse
import com.karoohass.network.HttpTransport
import com.karoohass.security.TokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class OAuthAttemptTest {
    private val context =
        object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            private val prefix = UUID.randomUUID().toString()

            override fun getSharedPreferences(
                name: String,
                mode: Int,
            ) = super.getSharedPreferences("test_${prefix}_$name", mode)
        }
    private val tokens = TokenStore(context)

    @Test fun successfulCallbackCanBeConsumedOnlyOnce() =
        runBlocking {
            var exchanges = 0
            val manager =
                OAuthManager(
                    context,
                    tokens,
                    transport {
                        exchanges++
                        tokenResponse()
                    },
                )
            accept(manager, manager.authorizationUrl("https://home.example"))
            assertEquals(true, manager.consumeCallback())
            assertNull(manager.consumeCallback())
            assertEquals(1, exchanges)
            assertEquals("synthetic-access", tokens.load()?.accessToken)
        }

    @Test fun retryClearsAcceptedOldCallbackAndChangesState() =
        runBlocking {
            val manager = OAuthManager(context, tokens, transport { error("No exchange expected") })
            val first = manager.authorizationUrl("https://home.example")
            accept(manager, first)
            val second = manager.authorizationUrl("https://home.example")
            assertNotEquals(Uri.parse(first).getQueryParameter("state"), Uri.parse(second).getQueryParameter("state"))
            assertNull(manager.consumeCallback())
            accept(manager, second)
            manager.cancelAuthorization()
            assertNull(manager.consumeCallback())
        }

    @Test fun replacedAttemptCannotSaveOldTokensOrClearNewCallback() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val response = CompletableDeferred<HttpResponse>()
            val manager =
                OAuthManager(
                    context,
                    tokens,
                    transport {
                        started.complete(Unit)
                        response.await()
                    },
                )
            accept(manager, manager.authorizationUrl("https://first.example"))
            val old = async { manager.consumeCallback() }
            started.await()
            val next = manager.authorizationUrl("https://second.example")
            accept(manager, next)
            response.complete(tokenResponse())
            assertNull(old.await())
            assertNull(tokens.load())
            // The new accepted callback remains available after the old exchange completes.
            assertEquals(true, manager.consumeCallback())
            assertEquals("synthetic-access", tokens.load()?.accessToken)
        }

    @Test fun concurrentResumeDoesNotExchangeSameCodeTwice() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val response = CompletableDeferred<HttpResponse>()
            var exchanges = 0
            val manager =
                OAuthManager(
                    context,
                    tokens,
                    transport {
                        exchanges++
                        started.complete(Unit)
                        response.await()
                    },
                )
            accept(manager, manager.authorizationUrl("https://home.example"))
            val first = async { manager.consumeCallback() }
            started.await()
            val second = async { manager.consumeCallback() }
            response.complete(tokenResponse())
            assertEquals(true, first.await())
            assertNull(second.await())
            assertEquals(1, exchanges)
        }

    private fun accept(
        manager: OAuthManager,
        url: String,
    ) {
        val state = Uri.parse(url).getQueryParameter("state")
        assertEquals(OAuthCallbackReceipt.ACCEPTED, manager.receive(Uri.parse("karoohass://auth-callback?code=synthetic-code&state=$state")))
    }

    private fun transport(block: suspend () -> HttpResponse) =
        object : HttpTransport {
            override suspend fun execute(request: HttpRequest) = block()
        }

    private fun tokenResponse() = HttpResponse(200, emptyMap(), """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","expires_in":1800}""".toByteArray())
}

package com.karoohass.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthCallbackUriTest {
    @Test
    fun `accepts the exact app OAuth callback URI`() {
        assertTrue(isOAuthCallbackUri("karoohass", "auth-callback", ""))
        assertTrue(isOAuthCallbackUri("karoohass", "auth-callback", null))
    }

    @Test
    fun `rejects malformed and unrelated callback URIs`() {
        assertFalse(isOAuthCallbackUri("https", "michalchecinski.github.io", "/karoo-hass-companion/auth-callback"))
        assertFalse(isOAuthCallbackUri("karoohass", "other-callback", ""))
        assertFalse(isOAuthCallbackUri("karoohass", "auth-callback", "/unexpected"))
    }

    @Test
    fun `accepts a callback with the expected state and authorization code`() {
        assertEquals(
            OAuthCallbackReceipt.ACCEPTED,
            validateOAuthCallback("expected-state", "authorization-code", "expected-state"),
        )
    }

    @Test
    fun `rejects a callback with missing code or a mismatched state`() {
        assertEquals(
            OAuthCallbackReceipt.INVALID,
            validateOAuthCallback("expected-state", null, "expected-state"),
        )
        assertEquals(
            OAuthCallbackReceipt.INVALID,
            validateOAuthCallback("expected-state", "authorization-code", "other-state"),
        )
    }

    @Test
    fun `ignores callbacks when no authorization is pending`() {
        assertEquals(
            OAuthCallbackReceipt.NO_PENDING_AUTHORIZATION,
            validateOAuthCallback(null, "authorization-code", "any-state"),
        )
    }
}

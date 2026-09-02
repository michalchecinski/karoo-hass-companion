package com.karoohass.auth

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
}

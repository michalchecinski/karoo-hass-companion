package com.karoohass.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginValidationTest {
    @Test fun `normalizes HTTPS hosts and IP addresses without downgrading`() {
        mapOf(
            "home.example" to "https://home.example",
            "192.168.1.10:8123" to "https://192.168.1.10:8123",
            "  HTTPS://HOME.EXAMPLE:443/  " to "https://home.example",
            "https://home.example:8443/" to "https://home.example:8443",
            "[fd00::1]:8123" to "https://[fd00::1]:8123",
            "https://[2001:db8::1]/" to "https://[2001:db8::1]",
        ).forEach { (input, expected) -> assertEquals(input, expected, validateHomeAssistantOrigin(input).origin) }
    }

    @Test fun `rejects explicit insecure and unsupported schemes`() {
        listOf("http://192.168.1.10:8123", "HTTP://home.example", "ftp://home.example").forEach {
            assertEquals(it, OriginError.HTTPS_REQUIRED, validateHomeAssistantOrigin(it).error)
            assertNull(validateHomeAssistantOrigin(it).origin)
        }
    }

    @Test fun `rejects embedded credentials and non-origin components`() {
        assertEquals(OriginError.CREDENTIALS, validateHomeAssistantOrigin("https://user:password@home.example").error)
        listOf("https://home.example/lovelace", "https://home.example?x=1", "https://home.example#section", "https://home.example?").forEach {
            assertEquals(it, OriginError.ORIGIN_ONLY, validateHomeAssistantOrigin(it).error)
        }
    }

    @Test fun `rejects invalid hosts and ports`() {
        listOf("", "https://", "https://bad host", "https://home.example:abc", "https://home.example:-1").forEach {
            assertNull(it, validateHomeAssistantOrigin(it).origin)
        }
        listOf("https://home.example:", "https://home.example:0", "https://home.example:65536").forEach {
            assertEquals(it, OriginError.INVALID_PORT, validateHomeAssistantOrigin(it).error)
        }
        assertEquals("https://home.example:65535", validateHomeAssistantOrigin("home.example:65535").origin)
    }
}

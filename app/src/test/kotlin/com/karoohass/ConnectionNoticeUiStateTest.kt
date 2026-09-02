package com.karoohass

import com.karoohass.core.ActionKind
import com.karoohass.core.AppSettings
import com.karoohass.core.ConnectionPolicy
import com.karoohass.core.OnboardingStep
import com.karoohass.core.PinMode
import com.karoohass.core.QuickAccessAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionNoticeUiStateTest {
    @Test fun `Wi-Fi only without Wi-Fi explains how to recover`() {
        val state =
            UiState(
                settings = configuredSettings(ConnectionPolicy.WIFI_ONLY),
                wifiAvailable = false,
                connectionStatus = ConnectionStatus.UNREACHABLE,
            )

        assertEquals("No Wi-Fi connection", state.connectionNotice?.title)
        assertEquals("Connect the Karoo to Wi-Fi, then try again.", state.connectionNotice?.message)
        assertFalse(state.canInvokeQuickAccessActions)
    }

    @Test fun `Companion fallback failure explains both recovery paths`() {
        val state =
            UiState(
                settings = configuredSettings(ConnectionPolicy.ALLOW_COMPANION_FALLBACK),
                wifiAvailable = false,
                connectionStatus = ConnectionStatus.UNREACHABLE,
            )

        assertEquals("Home Assistant can't be reached", state.connectionNotice?.title)
        assertEquals(
            "Connect to Wi-Fi, or check that your phone is paired, Hammerhead Companion is running, and the phone has internet access.",
            state.connectionNotice?.message,
        )
        assertFalse(state.canInvokeQuickAccessActions)
    }

    @Test fun `checking connection keeps actions disabled without an offline notice`() {
        val state = UiState(settings = configuredSettings(ConnectionPolicy.WIFI_ONLY), connectionStatus = ConnectionStatus.CHECKING)

        assertNull(state.connectionNotice)
        assertFalse(state.canInvokeQuickAccessActions)
    }

    @Test fun `successful recheck removes the notice and enables actions`() {
        val state = UiState(settings = configuredSettings(ConnectionPolicy.ALLOW_COMPANION_FALLBACK), connectionStatus = ConnectionStatus.CONNECTED)

        assertNull(state.connectionNotice)
        assertTrue(state.canInvokeQuickAccessActions)
    }

    @Test fun `whole-app PIN must be unlocked before the connection check can run`() {
        val settings = configuredSettings(ConnectionPolicy.WIFI_ONLY).copy(pinMode = PinMode.WHOLE_APP)

        assertFalse(canCheckQuickAccessConnection(settings, Screen.HOME, appInForeground = true, wholeAppLocked = true))
        assertTrue(canCheckQuickAccessConnection(settings, Screen.HOME, appInForeground = true, wholeAppLocked = false))
    }

    @Test fun `connection checks do not run while Quick Access is hidden or backgrounded`() {
        val settings = configuredSettings(ConnectionPolicy.WIFI_ONLY)

        assertFalse(canCheckQuickAccessConnection(settings, Screen.MANAGE, appInForeground = true, wholeAppLocked = false))
        assertFalse(canCheckQuickAccessConnection(settings, Screen.HOME, appInForeground = false, wholeAppLocked = false))
    }

    private fun configuredSettings(policy: ConnectionPolicy) =
        AppSettings(
            origin = "https://home.example",
            connectionPolicy = policy,
            actions = listOf(QuickAccessAction("action", "script.arrive_home", "script", ActionKind.RUN_SCRIPT)),
            onboardingStep = OnboardingStep.COMPLETE,
        )
}

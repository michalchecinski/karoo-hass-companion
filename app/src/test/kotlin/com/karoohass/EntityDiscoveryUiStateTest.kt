package com.karoohass

import com.karoohass.core.ActionKind
import com.karoohass.core.AppSettings
import com.karoohass.core.EntitySnapshot
import com.karoohass.core.QuickAccessAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityDiscoveryUiStateTest {
    @Test fun `Wi-Fi reconnection refreshes configured controls`() {
        val settings = AppSettings(origin = "https://home.example", actions = listOf(action()))

        assertTrue(shouldRefreshQuickAccessAfterWifiReconnect(false, true, settings, wholeAppLocked = false))
    }

    @Test fun `Wi-Fi reconnection does not refresh without configured controls`() {
        val settings = AppSettings(origin = "https://home.example")

        assertFalse(shouldRefreshQuickAccessAfterWifiReconnect(false, true, settings, wholeAppLocked = false))
    }

    @Test fun `unchanged Wi-Fi availability does not trigger a refresh`() {
        val settings = AppSettings(origin = "https://home.example", actions = listOf(action()))

        assertFalse(shouldRefreshQuickAccessAfterWifiReconnect(true, true, settings, wholeAppLocked = false))
    }

    @Test fun `Wi-Fi reconnection does not load state while whole app is locked`() {
        val settings = AppSettings(origin = "https://home.example", actions = listOf(action()))

        assertFalse(shouldRefreshQuickAccessAfterWifiReconnect(false, true, settings, wholeAppLocked = true))
    }

    @Test fun `failed discovery is not presented as a successful empty result`() {
        val state =
            UiState(
                snapshots = emptyMap(),
                wifiAvailable = true,
                entityDiscoveryStatus = EntityDiscoveryStatus.FAILED,
            )

        assertFalse(state.showNoSupportedEntities)
    }

    @Test fun `successful discovery with no supported entities shows the empty result`() {
        val state =
            UiState(
                snapshots = emptyMap(),
                wifiAvailable = true,
                entityDiscoveryStatus = EntityDiscoveryStatus.SUCCEEDED,
            )

        assertTrue(state.showNoSupportedEntities)
    }

    @Test fun `successful discovery with entities does not show the empty result`() {
        val entity = EntitySnapshot("light.porch", "light", "off", 0, true, friendlyName = "Porch")
        val state =
            UiState(
                snapshots = mapOf(entity.entityId to entity),
                wifiAvailable = true,
                entityDiscoveryStatus = EntityDiscoveryStatus.SUCCEEDED,
            )

        assertFalse(state.showNoSupportedEntities)
    }

    @Test fun `entity refresh is disabled and empty result hidden without Wi-Fi`() {
        val state =
            UiState(
                snapshots = emptyMap(),
                wifiAvailable = false,
                entityDiscoveryStatus = EntityDiscoveryStatus.SUCCEEDED,
            )

        assertFalse(state.canDiscoverEntities)
        assertFalse(state.showNoSupportedEntities)
    }

    @Test fun `entity refresh is enabled when Wi-Fi is available and app is idle`() {
        val state = UiState(wifiAvailable = true)

        assertTrue(state.canDiscoverEntities)
    }

    @Test fun `entity refresh is disabled while another request is active`() {
        val state = UiState(wifiAvailable = true, busy = true)

        assertFalse(state.canDiscoverEntities)
    }

    private fun action() = QuickAccessAction("id", "light.porch", "light", ActionKind.TOGGLE)
}

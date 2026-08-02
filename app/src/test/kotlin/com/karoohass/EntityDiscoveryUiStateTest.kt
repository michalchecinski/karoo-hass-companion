package com.karoohass

import com.karoohass.core.EntitySnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityDiscoveryUiStateTest {
    @Test fun `failed discovery is not presented as a successful empty result`() {
        val state =
            UiState(
                snapshots = emptyMap(),
                entityDiscoveryStatus = EntityDiscoveryStatus.FAILED,
            )

        assertFalse(state.showNoSupportedEntities)
    }

    @Test fun `successful discovery with no supported entities shows the empty result`() {
        val state =
            UiState(
                snapshots = emptyMap(),
                entityDiscoveryStatus = EntityDiscoveryStatus.SUCCEEDED,
            )

        assertTrue(state.showNoSupportedEntities)
    }

    @Test fun `successful discovery with entities does not show the empty result`() {
        val entity = EntitySnapshot("light.porch", "light", "off", 0, true, friendlyName = "Porch")
        val state =
            UiState(
                snapshots = mapOf(entity.entityId to entity),
                entityDiscoveryStatus = EntityDiscoveryStatus.SUCCEEDED,
            )

        assertFalse(state.showNoSupportedEntities)
    }
}

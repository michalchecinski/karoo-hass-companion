package com.karoohass.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karoohass.ConnectionStatus
import com.karoohass.UiState
import com.karoohass.core.ActionKind
import com.karoohass.core.ActionOutcome
import com.karoohass.core.AppSettings
import com.karoohass.core.ConnectionPolicy
import com.karoohass.core.EntitySnapshot
import com.karoohass.core.OnboardingStep
import com.karoohass.core.QuickAccessAction
import com.karoohass.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuickAccessControlsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun statelessTilesHideReportedStateAndShowRequestOutcomes() {
        val button = action("button-id", "button.ride_mode", ActionKind.PRESS_BUTTON, "Ride mode")
        val scene = action("scene-id", "scene.arrive_home", ActionKind.ACTIVATE_SCENE, "Arrive home")
        var state by mutableStateOf(
            homeState(
                actions = listOf(button, scene),
                snapshots =
                    mapOf(
                        button.entityId to snapshot(button.entityId, "button", "button-reported-state"),
                        scene.entityId to snapshot(scene.entityId, "scene", "scene-reported-state"),
                    ),
                outcome = ActionOutcome.REQUESTED,
                outcomeActionId = scene.id,
            ),
        )
        composeRule.setContent { AppTheme { Home(state, {}, {}) } }

        composeRule.onAllNodesWithText("button-reported-state").assertCountEquals(0)
        composeRule.onAllNodesWithText("scene-reported-state").assertCountEquals(0)
        composeRule.onNodeWithText("Requested").assertTextContains("Requested")

        composeRule.runOnUiThread { state = state.copy(outcome = ActionOutcome.FAILED, outcomeActionId = scene.id) }
        composeRule.onNodeWithText("Failed").assertTextContains("Failed")

        composeRule.runOnUiThread { state = state.copy(outcome = ActionOutcome.UNKNOWN, outcomeActionId = button.id) }
        composeRule.onNodeWithText("Outcome uncertain").assertTextContains("Outcome uncertain")
    }

    @Test
    fun unavailableStatelessTileCannotInvokeAction() {
        val button = action("button-id", "button.garage", ActionKind.PRESS_BUTTON, "Garage remote")
        var invocations = 0
        composeRule.setContent {
            AppTheme {
                Home(
                    homeState(listOf(button), mapOf(button.entityId to snapshot(button.entityId, "button", "unavailable", available = false))),
                    { invocations += 1 },
                    {},
                )
            }
        }

        composeRule.onNodeWithTag("quick-access-${button.id}").assertIsNotEnabled()

        assertEquals(0, invocations)
    }

    @Test
    fun offlineNoticeExplainsCompanionRecoveryAndKeepsTilesDisabled() {
        val action = action("button-id", "button.garage", ActionKind.PRESS_BUTTON, "Garage remote")
        var retries = 0
        val state =
            homeState(listOf(action), mapOf(action.entityId to snapshot(action.entityId, "button", "unknown"))).copy(
                settings = homeState(listOf(action), emptyMap()).settings.copy(connectionPolicy = ConnectionPolicy.ALLOW_COMPANION_FALLBACK),
                connectionStatus = ConnectionStatus.UNREACHABLE,
            )

        composeRule.setContent { AppTheme { Home(state, {}, {}, { retries += 1 }) } }

        composeRule.onNodeWithText("Home Assistant can't be reached").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.onNodeWithTag("quick-access-${action.id}").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun pickerOffersOneDisabledAddForAnExistingButton() {
        val button = snapshot("button.garage", "button", "unknown", friendlyName = "Garage remote")
        composeRule.setContent {
            AppTheme {
                ActionPicker(button, false, false, {}, {}, {}, alreadyAdded = true, dismiss = {})
            }
        }

        composeRule.onNodeWithText("Add a button press.").assertTextContains("Add a button press.")
        composeRule.onNodeWithText("Already added").assertTextContains("Already added")
        composeRule.onAllNodesWithText("Add").assertCountEquals(1)
        composeRule.onNodeWithTag("action-picker-add").assertIsNotEnabled()
    }

    @Test
    fun scenePickerOffersOneAddAndBothOptionalSecurityControls() {
        val scene = snapshot("scene.arrive_home", "scene", "unknown", friendlyName = "Arrive home")
        var protect by mutableStateOf(false)
        var confirm by mutableStateOf(false)
        var added: ActionKind? = null
        composeRule.setContent {
            AppTheme {
                ActionPicker(scene, protect, confirm, { protect = it }, { confirm = it }, { added = it }, alreadyAdded = false, dismiss = {})
            }
        }

        composeRule.onNodeWithText("Add a scene activation.").assertTextContains("Add a scene activation.")
        composeRule.onAllNodesWithText("Add").assertCountEquals(1)
        composeRule.onNodeWithTag("action-picker-protect").performClick()
        composeRule.onNodeWithTag("action-picker-confirm").performClick()
        composeRule.runOnIdle {
            assertTrue(protect)
            assertTrue(confirm)
        }
        composeRule.onNodeWithTag("action-picker-add").performClick()
        composeRule.runOnIdle { assertEquals(ActionKind.ACTIVATE_SCENE, added) }
    }

    private fun homeState(
        actions: List<QuickAccessAction>,
        snapshots: Map<String, EntitySnapshot>,
        outcome: ActionOutcome? = null,
        outcomeActionId: String? = null,
    ) =
        UiState(
            settings = AppSettings(origin = "https://home.example", actions = actions, onboardingStep = OnboardingStep.COMPLETE),
            snapshots = snapshots,
            outcome = outcome,
            outcomeActionId = outcomeActionId,
        )

    private fun action(
        id: String,
        entityId: String,
        kind: ActionKind,
        name: String,
    ) =
        QuickAccessAction(id = id, entityId = entityId, domain = entityId.substringBefore('.'), kind = kind, displayName = name)

    private fun snapshot(
        entityId: String,
        domain: String,
        state: String,
        available: Boolean = true,
        friendlyName: String = entityId,
    ) =
        EntitySnapshot(entityId, domain, state, 0, available, friendlyName = friendlyName)
}

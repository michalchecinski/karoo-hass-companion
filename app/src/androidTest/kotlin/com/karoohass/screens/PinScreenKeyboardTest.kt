package com.karoohass.screens

import android.graphics.Rect
import android.view.KeyEvent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.karoohass.MainActivity
import com.karoohass.MainViewModel
import com.karoohass.core.ActionKind
import com.karoohass.core.AppSettings
import com.karoohass.core.OnboardingStep
import com.karoohass.core.PinMode
import com.karoohass.core.QuickAccessAction
import com.karoohass.core.SettingsStore
import com.karoohass.security.PinStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PinScreenKeyboardTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun configureIsolatedAccount() {
        runBlocking {
            SettingsStore(context).update {
                AppSettings(
                    origin = "https://demo.example.com",
                    pinMode = PinMode.WHOLE_APP,
                    onboardingStep = OnboardingStep.COMPLETE,
                )
            }
        }
        PinStore(context).set("1234")
    }

    @After
    fun clearIsolatedAccount() {
        scenario?.close()
        PinStore(context).clear()
        runBlocking { SettingsStore(context).update { AppSettings() } }
    }

    @Test
    fun coldLaunchFocusesPinAndShowsKeyboardWithoutTapping() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertReadyForPin()
    }

    @Test
    fun recreationFocusesPinAndShowsKeyboardAgain() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertReadyForPin()
        scenario!!.recreate()
        assertReadyForPin()
    }

    @Test
    fun returningToLockedAppShowsKeyboardAgain() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertReadyForPin()
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertReadyForPin()
    }

    @Test
    fun unrelatedRecompositionDoesNotReopenDismissedKeyboard() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertReadyForPin()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitUntil(5_000) { !keyboardVisible() }
        scenario!!.onActivity { ViewModelProvider(it)[MainViewModel::class.java].openSetup() }
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        assertFalse(keyboardVisible())
    }

    @Test
    fun incorrectPinCanBeRetriedWithKeyboardDone() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertReadyForPin()
        composeRule.onNode(hasSetTextAction()).performTextInput("9999")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Incorrect PIN").fetchSemanticsNodes().isNotEmpty()
        }
        assertReadyForPin()
        composeRule.onAllNodesWithText("Incorrect PIN").assertCountEquals(1)
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithText("Incorrect PIN").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithText("Incorrect PIN").assertIsDisplayed()
        val feedbackBounds = composeRule.onNodeWithText("Incorrect PIN").fetchSemanticsNode().boundsInWindow
        val buttonBounds = composeRule.onNodeWithText("Unlock").fetchSemanticsNode().boundsInWindow
        val fieldBounds = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInWindow
        assertFalse("PIN feedback must not cover Unlock", feedbackBounds.overlaps(buttonBounds))
        assertFalse("PIN feedback must not cover the input", feedbackBounds.overlaps(fieldBounds))
        composeRule.onNodeWithText("Unlock").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("1234")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Enter PIN").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(5_000) { !keyboardVisible() }
        composeRule.onNodeWithText("Manage actions").assertIsDisplayed()
    }

    @Test
    fun protectedActionEntryFocusesPinAndShowsKeyboard() {
        val action =
            QuickAccessAction(
                id = "test-button",
                entityId = "button.test",
                domain = "button",
                kind = ActionKind.PRESS_BUTTON,
                protected = true,
                requiresConfirmation = false,
                icon = null,
                order = 0,
                displayName = "Test button",
            )
        runBlocking {
            SettingsStore(context).update { it.copy(pinMode = PinMode.SELECTED_ACTIONS, actions = listOf(action)) }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.onNodeWithText("Test button").assertIsDisplayed()
        // Exercise the real protected-action route without connecting a live HA account.
        scenario!!.onActivity { ViewModelProvider(it)[MainViewModel::class.java].invoke(action) }
        assertReadyForPin()
    }

    private fun assertReadyForPin() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        composeRule.waitUntil(5_000) { keyboardVisible() }
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithText("Unlock").assertIsDisplayed()
        // Compose visibility alone can pass for a button covered by an Android
        // keyboard overlay. Check against the actual visible window as well.
        var visibleBottom = 0
        scenario!!.onActivity {
            val view = it.window.decorView
            val frame = Rect()
            val screenLocation = IntArray(2)
            val windowLocation = IntArray(2)
            view.getWindowVisibleDisplayFrame(frame)
            view.getLocationOnScreen(screenLocation)
            view.getLocationInWindow(windowLocation)
            visibleBottom = frame.bottom - (screenLocation[1] - windowLocation[1])
        }
        assertTrue(
            "Unlock must fit above the software keyboard",
            composeRule.onNodeWithText("Unlock").fetchSemanticsNode().boundsInWindow.bottom <= visibleBottom,
        )
    }

    private fun keyboardVisible(): Boolean {
        var visible = false
        scenario!!.onActivity {
            visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }
}

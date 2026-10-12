package com.karoohass.screens

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.karoohass.MainActivity
import com.karoohass.core.AppSettings
import com.karoohass.core.OnboardingStep
import com.karoohass.core.PinMode
import com.karoohass.core.SettingsStore
import com.karoohass.security.PinStore
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    private fun assertReadyForPin() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        composeRule.waitUntil(5_000) {
            var visible = false
            scenario!!.onActivity {
                visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            visible
        }
    }
}

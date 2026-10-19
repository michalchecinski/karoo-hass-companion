package com.karoohass.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karoohass.theme.AppTheme
import org.junit.Rule
import org.junit.Test

class SoundFeedbackSettingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wholeSettingRowTogglesSoundAndExposesItsState() {
        var enabled by mutableStateOf(false)
        composeRule.setContent {
            AppTheme {
                SoundFeedbackSetting(enabled) { enabled = it }
            }
        }
        composeRule.onNodeWithText("Sound feedback").assertIsOff().performClick().assertIsOn()
        composeRule.onNodeWithText("Sound feedback").performClick().assertIsOff()
    }
}

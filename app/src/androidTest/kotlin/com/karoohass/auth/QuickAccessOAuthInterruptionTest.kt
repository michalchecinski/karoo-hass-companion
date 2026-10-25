package com.karoohass.auth

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.karoohass.MainViewModel
import com.karoohass.security.TokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class QuickAccessOAuthInterruptionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun quickAccessEntryCancelsAcceptedPendingSignIn() {
        composeRule.runOnUiThread {
            val context = composeRule.activity.application
            val model = ViewModelProvider(composeRule.activity)[MainViewModel::class.java]
            val manager = OAuthManager(context, TokenStore(context))
            assertNull(model.beginAuthentication("https://home.example"))
            val state = Uri.parse(model.currentAuthorizationUrl()).getQueryParameter("state")
            val callback = Uri.parse("karoohass://auth-callback?code=synthetic-code&state=$state")
            assertEquals(OAuthCallbackReceipt.ACCEPTED, manager.receive(callback))
            model.openQuickAccess()
            val prefs = context.getSharedPreferences("oauth", 0)
            assertNull(prefs.getString("code", null))
            assertNull(prefs.getString("state", null))
            assertEquals(OAuthCallbackReceipt.NO_PENDING_AUTHORIZATION, manager.receive(callback))
        }
    }
}

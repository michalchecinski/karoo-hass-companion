package com.karoohass

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karoohass.auth.OAuthCallbackActivity
import com.karoohass.screens.MainScreen
import com.karoohass.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val model by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val state by model.state.collectAsStateWithLifecycle()
                MainScreen(state, model, ::finish)
            }
        }
        handleOAuthCallbackError(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallbackError(intent)
    }

    override fun onResume() {
        super.onResume()
        model.foregroundChanged(true)
        model.callbackReceived()
        model.enforceWholeAppPin()
    }

    override fun onStop() {
        model.foregroundChanged(false)
        super.onStop()
    }

    private fun handleOAuthCallbackError(intent: Intent) {
        if (intent.getBooleanExtra(OAuthCallbackActivity.EXTRA_CALLBACK_ERROR, false)) {
            model.oauthCallbackFailed()
        }
    }
}

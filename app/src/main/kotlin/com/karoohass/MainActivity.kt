package com.karoohass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karoohass.screens.MainScreen
import com.karoohass.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val model by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AppTheme { val state by model.state.collectAsStateWithLifecycle(); MainScreen(state, model) } } }
    override fun onResume() { super.onResume(); model.callbackReceived(); model.enforceWholeAppPin() }
    override fun onStop() { model.foregroundChanged(false); super.onStop() }
}

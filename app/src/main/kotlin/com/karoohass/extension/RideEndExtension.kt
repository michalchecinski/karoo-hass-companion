package com.karoohass.extension

import android.util.Log
import com.karoohass.BuildConfig
import com.karoohass.MainActivity
import com.karoohass.R
import com.karoohass.core.SettingsStore
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.SystemNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RideEndExtension : KarooExtension("karoo-hass", BuildConfig.VERSION_NAME) {
    private lateinit var karooSystem: KarooSystemService
    private val detector = RideEndDetector()
    private var consumerId: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        val settingsStore = SettingsStore(applicationContext)
        // Register once: the SDK retains consumers across service reconnects.
        consumerId =
            karooSystem.addConsumer<RideState>(
                onError = { error ->
                    detector.reset()
                    Log.w("RideEndExtension", "Ride state subscription failed: $error")
                },
                onComplete = { detector.reset() },
            ) { state ->
                if (detector.onState(state)) {
                    scope.launch {
                        if (!settingsStore.settings.first().rideEndNotificationEnabled) return@launch
                        val dispatched =
                            karooSystem.dispatch(
                                SystemNotification(
                                    id = "karoo-hass-ride-ended",
                                    message = getString(R.string.ride_ended_notification_message),
                                    header = getString(R.string.app_name),
                                    action = getString(R.string.ride_ended_notification_action),
                                    actionIntent = MainActivity.ACTION_OPEN_QUICK_ACCESS,
                                ),
                            )
                        if (!dispatched) Log.w("RideEndExtension", "Ride end notification could not be dispatched")
                    }
                }
            }
        karooSystem.connect { connected ->
            if (!connected) detector.reset()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        consumerId?.let(karooSystem::removeConsumer)
        consumerId = null
        detector.reset()
        karooSystem.disconnect()
        super.onDestroy()
    }
}

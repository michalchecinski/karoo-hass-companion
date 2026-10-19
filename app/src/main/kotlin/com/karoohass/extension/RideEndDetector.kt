package com.karoohass.extension

import io.hammerhead.karooext.models.RideState

internal class RideEndDetector {
    private var rideObserved = false

    @Synchronized
    fun onState(state: RideState): Boolean =
        when (state) {
            is RideState.Recording, is RideState.Paused -> {
                rideObserved = true
                false
            }
            is RideState.Idle -> {
                val ended = rideObserved
                rideObserved = false
                ended
            }
        }

    @Synchronized
    fun reset() {
        rideObserved = false
    }
}

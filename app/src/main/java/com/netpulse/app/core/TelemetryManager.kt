package com.netpulse.app.core

import com.netpulse.app.domain.model.SpeedSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TelemetryManager {
    private val _currentSpeed = MutableStateFlow(SpeedSnapshot())
    val currentSpeed: StateFlow<SpeedSnapshot> = _currentSpeed.asStateFlow()

    fun updateSpeed(snapshot: SpeedSnapshot) {
        _currentSpeed.value = snapshot
    }

    fun reset() {
        _currentSpeed.value = SpeedSnapshot()
    }
}

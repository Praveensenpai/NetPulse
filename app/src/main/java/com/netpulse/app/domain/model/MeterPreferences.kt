package com.netpulse.app.domain.model

data class MeterPreferences(
    val isServiceEnabled: Boolean = true,
    val showNotification: Boolean = true,
    val showStatusBarIcon: Boolean = true,
    val showUploadSpeed: Boolean = true,
    val showFloatingOverlay: Boolean = false,
    val unitInBits: Boolean = false,
    val startOnBoot: Boolean = true,
    val pauseWhenScreenOff: Boolean = true,
    val overlayAlpha: Float = 0.85f
)

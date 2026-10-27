package com.netpulse.app.ui.settings

import com.netpulse.app.domain.model.MeterPreferences

data class SettingsUiState(
    val preferences: MeterPreferences = MeterPreferences(),
    val canDrawOverlays: Boolean = false
)
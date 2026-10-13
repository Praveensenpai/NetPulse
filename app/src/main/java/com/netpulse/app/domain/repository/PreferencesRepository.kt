package com.netpulse.app.domain.repository

import com.netpulse.app.domain.model.MeterPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferencesFlow: Flow<MeterPreferences>
    suspend fun getPreferences(): MeterPreferences
    suspend fun updatePreferences(transform: (MeterPreferences) -> MeterPreferences)
    suspend fun setServiceEnabled(enabled: Boolean)
    suspend fun setFloatingOverlayEnabled(enabled: Boolean)
    suspend fun setUnitInBits(inBits: Boolean)
    suspend fun setShowUploadSpeed(show: Boolean)
    suspend fun setShowStatusBarIcon(show: Boolean)
    suspend fun setPauseWhenScreenOff(pause: Boolean)
    suspend fun setStartOnBoot(start: Boolean)
}

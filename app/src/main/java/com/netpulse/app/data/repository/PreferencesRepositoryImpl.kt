package com.netpulse.app.data.repository

import com.netpulse.app.data.source.LocalDataStore
import com.netpulse.app.domain.model.MeterPreferences
import com.netpulse.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow

class PreferencesRepositoryImpl(
    private val localDataStore: LocalDataStore
) : PreferencesRepository {

    override val preferencesFlow: Flow<MeterPreferences> = localDataStore.meterPreferencesFlow

    override suspend fun getPreferences(): MeterPreferences =
        localDataStore.getMeterPreferences()

    override suspend fun updatePreferences(transform: (MeterPreferences) -> MeterPreferences) {
        localDataStore.updateMeterPreferences(transform)
    }

    override suspend fun setServiceEnabled(enabled: Boolean) {
        updatePreferences { it.copy(isServiceEnabled = enabled) }
    }

    override suspend fun setFloatingOverlayEnabled(enabled: Boolean) {
        updatePreferences { it.copy(showFloatingOverlay = enabled) }
    }

    override suspend fun setUnitInBits(inBits: Boolean) {
        updatePreferences { it.copy(unitInBits = inBits) }
    }

    override suspend fun setShowUploadSpeed(show: Boolean) {
        updatePreferences { it.copy(showUploadSpeed = show) }
    }

    override suspend fun setShowStatusBarIcon(show: Boolean) {
        updatePreferences { it.copy(showStatusBarIcon = show) }
    }

    override suspend fun setPauseWhenScreenOff(pause: Boolean) {
        updatePreferences { it.copy(pauseWhenScreenOff = pause) }
    }

    override suspend fun setStartOnBoot(start: Boolean) {
        updatePreferences { it.copy(startOnBoot = start) }
    }
}

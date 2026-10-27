package com.netpulse.app.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netpulse.app.NetPulseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data class ToggleNotification(val enabled: Boolean) : SettingsEvent
    data class ToggleStatusBarIcon(val enabled: Boolean) : SettingsEvent
    data class ToggleUploadSpeed(val enabled: Boolean) : SettingsEvent
    data class ToggleUnitInBits(val inBits: Boolean) : SettingsEvent
    data class TogglePauseScreenOff(val pause: Boolean) : SettingsEvent
    data class ToggleStartOnBoot(val start: Boolean) : SettingsEvent
    data class ToggleFloatingOverlay(val enabled: Boolean) : SettingsEvent
    data object ResetTodayUsage : SettingsEvent
    data object RefreshPermissions : SettingsEvent
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetPulseApp
    private val prefsRepo = app.preferencesRepository
    private val usageRepo = app.usageRepository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                _uiState.update {
                    it.copy(preferences = prefs, canDrawOverlays = canDrawOverlays())
                }
            }
        }
    }

    /** Called on resume so toggles reflect permissions granted outside the app. */
    fun refreshPermissions() {
        _uiState.update { it.copy(canDrawOverlays = canDrawOverlays()) }
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(getApplication())

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.ToggleNotification ->
                    prefsRepo.setShowNotification(event.enabled)
                is SettingsEvent.ToggleStatusBarIcon ->
                    prefsRepo.setShowStatusBarIcon(event.enabled)
                is SettingsEvent.ToggleUploadSpeed ->
                    prefsRepo.setShowUploadSpeed(event.enabled)
                is SettingsEvent.ToggleUnitInBits ->
                    prefsRepo.setUnitInBits(event.inBits)
                is SettingsEvent.TogglePauseScreenOff ->
                    prefsRepo.setPauseWhenScreenOff(event.pause)
                is SettingsEvent.ToggleStartOnBoot ->
                    prefsRepo.setStartOnBoot(event.start)
                is SettingsEvent.ToggleFloatingOverlay ->
                    setFloatingOverlay(event.enabled)
                SettingsEvent.ResetTodayUsage -> usageRepo.resetTodayUsage()
                SettingsEvent.RefreshPermissions -> refreshPermissions()
            }
        }
    }

    /**
     * Enabling without the overlay permission opens the grant screen and leaves the
     * preference off. It is written only once the permission is actually held, so the
     * switch never claims to be on while nothing is on screen.
     */
    private suspend fun setFloatingOverlay(enabled: Boolean) {
        if (enabled && !canDrawOverlays()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${getApplication<Application>().packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            getApplication<Application>().startActivity(intent)
            prefsRepo.setFloatingOverlayEnabled(false)
            return
        }
        prefsRepo.setFloatingOverlayEnabled(enabled)
    }
}
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
    data class ToggleStatusBarIcon(val enabled: Boolean) : SettingsEvent
    data class ToggleUploadSpeed(val enabled: Boolean) : SettingsEvent
    data class ToggleUnitInBits(val inBits: Boolean) : SettingsEvent
    data class TogglePauseScreenOff(val pause: Boolean) : SettingsEvent
    data class ToggleStartOnBoot(val start: Boolean) : SettingsEvent
    data class ToggleFloatingOverlay(val enabled: Boolean) : SettingsEvent
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetPulseApp
    private val prefsRepo = app.preferencesRepository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                val canOverlay = Settings.canDrawOverlays(getApplication())
                _uiState.update { it.copy(preferences = prefs, canDrawOverlays = canOverlay) }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.ToggleStatusBarIcon -> prefsRepo.setShowStatusBarIcon(event.enabled)
                is SettingsEvent.ToggleUploadSpeed -> prefsRepo.setShowUploadSpeed(event.enabled)
                is SettingsEvent.ToggleUnitInBits -> prefsRepo.setUnitInBits(event.inBits)
                is SettingsEvent.TogglePauseScreenOff -> prefsRepo.setPauseWhenScreenOff(event.pause)
                is SettingsEvent.ToggleStartOnBoot -> prefsRepo.setStartOnBoot(event.start)
                is SettingsEvent.ToggleFloatingOverlay -> {
                    if (event.enabled && !Settings.canDrawOverlays(getApplication())) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${getApplication<Application>().packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        getApplication<Application>().startActivity(intent)
                    }
                    prefsRepo.setFloatingOverlayEnabled(event.enabled)
                }
            }
        }
    }
}

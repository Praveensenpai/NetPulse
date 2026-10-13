package com.netpulse.app.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netpulse.app.NetPulseApp
import com.netpulse.app.core.BatteryOptimizer
import com.netpulse.app.core.Constants
import com.netpulse.app.core.SpeedFormatter
import com.netpulse.app.domain.model.SpeedSnapshot
import com.netpulse.app.service.NetPulseService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface HomeEvent {
    data class ToggleService(val enabled: Boolean) : HomeEvent
    data object RequestBatteryExemption : HomeEvent
    data object OpenSamsungCare : HomeEvent
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetPulseApp
    private val prefsRepo = app.preferencesRepository
    private val usageRepo = app.usageRepository
    private val networkMonitor = app.networkMonitor
    private val trafficSource = app.trafficDataSource

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeData()
        startLiveSampling()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                prefsRepo.preferencesFlow,
                usageRepo.observeTodayUsage(),
                networkMonitor.observeConnection()
            ) { prefs, usage, conn ->
                Triple(prefs, usage, conn)
            }.collect { (prefs, usage, conn) ->
                val isOptimized = !BatteryOptimizer.isIgnoringBatteryOptimizations(getApplication())
                _uiState.update { current ->
                    current.copy(
                        isServiceEnabled = prefs.isServiceEnabled,
                        dailyUsage = usage,
                        networkType = conn.type,
                        isBatteryOptimized = isOptimized
                    )
                }
            }
        }
    }

    private fun startLiveSampling() {
        viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.isServiceEnabled && _uiState.value.networkType != com.netpulse.app.core.ActiveNetworkType.NONE) {
                    val snapshot = trafficSource.sample()
                    val inBits = prefsRepo.getPreferences().unitInBits
                    _uiState.update { current ->
                        current.copy(
                            downloadSpeed = SpeedFormatter.formatSpeed(snapshot.downloadBytesPerSec, inBits),
                            uploadSpeed = SpeedFormatter.formatSpeed(snapshot.uploadBytesPerSec, inBits)
                        )
                    }
                }
                delay(1000L)
            }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.ToggleService -> {
                viewModelScope.launch {
                    prefsRepo.setServiceEnabled(event.enabled)
                    val intent = Intent(getApplication(), NetPulseService::class.java).apply {
                        action = if (event.enabled) Constants.ACTION_START_SERVICE else Constants.ACTION_STOP_SERVICE
                    }
                    if (event.enabled) {
                        getApplication<Application>().startForegroundService(intent)
                    } else {
                        getApplication<Application>().startService(intent)
                    }
                }
            }
            HomeEvent.RequestBatteryExemption -> {
                BatteryOptimizer.requestIgnoreBatteryOptimizations(getApplication())
            }
            HomeEvent.OpenSamsungCare -> {
                BatteryOptimizer.openSamsungDeviceCare(getApplication())
            }
        }
    }
}

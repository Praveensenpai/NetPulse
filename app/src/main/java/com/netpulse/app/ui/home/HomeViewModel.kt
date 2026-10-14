package com.netpulse.app.ui.home

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netpulse.app.NetPulseApp
import com.netpulse.app.core.BatteryOptimizer
import com.netpulse.app.core.Constants
import com.netpulse.app.core.SpeedFormatter
import com.netpulse.app.core.TelemetryManager
import com.netpulse.app.service.NetPulseService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HomeEvent {
    data class ToggleService(val enabled: Boolean) : HomeEvent
    data object RequestBatteryExemption : HomeEvent
    data object OpenSamsungCare : HomeEvent
    data object RequestNotificationPermission : HomeEvent
    data object RequestOverlayPermission : HomeEvent
    data object RefreshPermissions : HomeEvent
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetPulseApp
    private val prefsRepo = app.preferencesRepository
    private val usageRepo = app.usageRepository
    private val networkMonitor = app.networkMonitor

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeData()
        observeSpeedTelemetry()
        refreshPermissions()
    }

    fun refreshPermissions() {
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(getApplication()).areNotificationsEnabled()
        }
        val overlayGranted = Settings.canDrawOverlays(getApplication())
        val isOptimized = !BatteryOptimizer.isIgnoringBatteryOptimizations(getApplication())

        _uiState.update { current ->
            current.copy(
                hasNotificationPermission = notifGranted,
                canDrawOverlays = overlayGranted,
                isBatteryOptimized = isOptimized
            )
        }
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
                _uiState.update { current ->
                    current.copy(
                        isServiceEnabled = prefs.isServiceEnabled,
                        dailyUsage = usage,
                        networkType = conn.type
                    )
                }
            }
        }
    }

    private fun observeSpeedTelemetry() {
        viewModelScope.launch {
            TelemetryManager.currentSpeed.collect { snapshot ->
                val inBits = prefsRepo.getPreferences().unitInBits
                _uiState.update { current ->
                    current.copy(
                        downloadSpeed = SpeedFormatter.formatSpeed(snapshot.downloadBytesPerSec, inBits),
                        uploadSpeed = SpeedFormatter.formatSpeed(snapshot.uploadBytesPerSec, inBits)
                    )
                }
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
                    try {
                        if (event.enabled) {
                            getApplication<Application>().startForegroundService(intent)
                        } else {
                            getApplication<Application>().startService(intent)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            HomeEvent.RequestBatteryExemption -> {
                BatteryOptimizer.requestIgnoreBatteryOptimizations(getApplication())
            }
            HomeEvent.OpenSamsungCare -> {
                BatteryOptimizer.openSamsungDeviceCare(getApplication())
            }
            HomeEvent.RequestNotificationPermission -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, getApplication<Application>().packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    getApplication<Application>().startActivity(intent)
                }
            }
            HomeEvent.RequestOverlayPermission -> {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${getApplication<Application>().packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                getApplication<Application>().startActivity(intent)
            }
            HomeEvent.RefreshPermissions -> {
                refreshPermissions()
            }
        }
    }
}

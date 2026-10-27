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
import com.netpulse.app.domain.model.MeterPreferences
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
    data object RequestNotificationPermission : HomeEvent
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NetPulseApp
    private val prefsRepo = app.preferencesRepository
    private val usageRepo = app.usageRepository
    private val networkMonitor = app.networkMonitor

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Cached so the 1 Hz telemetry tick never touches disk. */
    private var currentPreferences = MeterPreferences()

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
        val isOptimized = !BatteryOptimizer.isIgnoringBatteryOptimizations(getApplication())

        _uiState.update { current ->
            current.copy(
                hasNotificationPermission = notifGranted,
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
                currentPreferences = prefs
                _uiState.update { current ->
                    current.copy(
                        isServiceEnabled = prefs.isServiceEnabled,
                        dailyUsage = usage,
                        networkType = conn.type,
                        showUploadSpeed = prefs.showUploadSpeed,
                        downloadSpeed = SpeedFormatter.formatSpeed(
                            current.downloadBytesPerSec,
                            prefs.unitInBits
                        ),
                        uploadSpeed = SpeedFormatter.formatSpeed(
                            current.uploadBytesPerSec,
                            prefs.unitInBits
                        )
                    )
                }
            }
        }
    }

    private fun observeSpeedTelemetry() {
        viewModelScope.launch {
            TelemetryManager.currentSpeed.collect { snapshot ->
                val inBits = currentPreferences.unitInBits
                _uiState.update { current ->
                    current.copy(
                        downloadBytesPerSec = snapshot.downloadBytesPerSec,
                        uploadBytesPerSec = snapshot.uploadBytesPerSec,
                        downloadSpeed = SpeedFormatter.formatSpeed(
                            snapshot.downloadBytesPerSec,
                            inBits
                        ),
                        uploadSpeed = SpeedFormatter.formatSpeed(
                            snapshot.uploadBytesPerSec,
                            inBits
                        )
                    )
                }
            }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.ToggleService -> setServiceEnabled(event.enabled)
            HomeEvent.RequestBatteryExemption ->
                BatteryOptimizer.requestIgnoreBatteryOptimizations(getApplication())
            HomeEvent.RequestNotificationPermission -> openNotificationSettings()
        }
    }

    private fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setServiceEnabled(enabled)
            val intent = Intent(getApplication(), NetPulseService::class.java).apply {
                action = if (enabled) Constants.ACTION_START_SERVICE else Constants.ACTION_STOP_SERVICE
            }
            try {
                if (enabled) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun openNotificationSettings() {
        val application = getApplication<Application>()
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName)
            }
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", application.packageName, null)
            )
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
    }
}
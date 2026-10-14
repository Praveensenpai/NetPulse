package com.netpulse.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.netpulse.app.NetPulseApp
import com.netpulse.app.core.ActiveNetworkType
import com.netpulse.app.core.Constants
import com.netpulse.app.domain.model.DailyUsage
import com.netpulse.app.domain.model.MeterPreferences
import com.netpulse.app.domain.model.SpeedSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetPulseService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null
    private var isScreenOn = true
    private var currentPreferences = MeterPreferences()
    private var todayUsage = DailyUsage(dateKey = "")

    private lateinit var notificationManager: SpeedNotificationManager
    private lateinit var overlayManager: FloatingOverlayManager
    private lateinit var screenReceiver: ScreenStateReceiver

    override fun onCreate() {
        super.onCreate()
        val app = NetPulseApp.instance
        notificationManager = SpeedNotificationManager(this)
        overlayManager = FloatingOverlayManager(this)

        screenReceiver = ScreenStateReceiver(
            onScreenOff = { onScreenStateChanged(screenOn = false) },
            onScreenOn = { onScreenStateChanged(screenOn = true) }
        )
        screenReceiver.register(this)

        observeConfiguration(app)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_STOP_SERVICE -> {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithNotification()
                startMonitoring()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val initialNotification = notificationManager.buildNotification(
            speed = SpeedSnapshot(),
            usage = todayUsage,
            unitInBits = currentPreferences.unitInBits,
            showUpload = currentPreferences.showUploadSpeed,
            useDynamicIcon = currentPreferences.showStatusBarIcon
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, initialNotification)
        }
    }

    private fun observeConfiguration(app: NetPulseApp) {
        serviceScope.launch {
            combine(
                app.preferencesRepository.preferencesFlow,
                app.usageRepository.observeTodayUsage()
            ) { prefs, usage ->
                Pair(prefs, usage)
            }.collect { (prefs, usage) ->
                val prevPrefs = currentPreferences
                currentPreferences = prefs
                todayUsage = usage

                if (!prefs.isServiceEnabled) {
                    stopMonitoring()
                    stopSelf()
                    return@collect
                }

                if (prefs.showFloatingOverlay && !prevPrefs.showFloatingOverlay) {
                    overlayManager.show(prefs.overlayAlpha)
                } else if (!prefs.showFloatingOverlay && prevPrefs.showFloatingOverlay) {
                    overlayManager.hide()
                }
            }
        }
    }

    private fun onScreenStateChanged(screenOn: Boolean) {
        isScreenOn = screenOn
        if (!screenOn && currentPreferences.pauseWhenScreenOff) {
            pollingJob?.cancel()
            pollingJob = null
        } else if (screenOn && pollingJob == null && currentPreferences.isServiceEnabled) {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        if (pollingJob?.isActive == true) return
        val app = NetPulseApp.instance
        app.trafficDataSource.reset()

        pollingJob = serviceScope.launch {
            while (isActive) {
                val snapshot = app.trafficDataSource.sample()
                com.netpulse.app.core.TelemetryManager.updateSpeed(snapshot)

                val conn = app.networkMonitor.getCurrentConnection()
                val totalDelta = snapshot.downloadBytesPerSec + snapshot.uploadBytesPerSec
                if (totalDelta > 0) {
                    if (conn.type == ActiveNetworkType.WIFI) {
                        app.usageRepository.recordUsage(0L, totalDelta)
                    } else if (conn.type == ActiveNetworkType.MOBILE) {
                        app.usageRepository.recordUsage(totalDelta, 0L)
                    }
                }

                if (currentPreferences.showNotification) {
                    val notif = notificationManager.buildNotification(
                        speed = snapshot,
                        usage = todayUsage,
                        unitInBits = currentPreferences.unitInBits,
                        showUpload = currentPreferences.showUploadSpeed,
                        useDynamicIcon = currentPreferences.showStatusBarIcon
                    )
                    notificationManager.updateNotification(notif)
                }

                if (currentPreferences.showFloatingOverlay) {
                    overlayManager.updateSpeed(snapshot, currentPreferences.unitInBits)
                }

                delay(Constants.DEFAULT_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun stopMonitoring() {
        pollingJob?.cancel()
        pollingJob = null
        com.netpulse.app.core.TelemetryManager.reset()
        overlayManager.hide()
    }

    override fun onDestroy() {
        stopMonitoring()
        screenReceiver.unregister(this)
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

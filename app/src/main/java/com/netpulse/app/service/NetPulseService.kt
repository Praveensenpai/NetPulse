package com.netpulse.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.netpulse.app.NetPulseApp
import com.netpulse.app.core.Constants
import com.netpulse.app.core.TelemetryManager
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
    private var currentPreferences = MeterPreferences()
    private var todayUsage = DailyUsage(dateKey = "")

    /** Last [MeterPreferences.pauseWhenScreenOff] the loop was reconciled for. */
    private var lastPauseWhenScreenOff: Boolean? = null

    private lateinit var notificationManager: SpeedNotificationManager
    private lateinit var overlayManager: FloatingOverlayManager
    private lateinit var screenReceiver: ScreenStateReceiver

    /**
     * Live display state, read from the platform instead of cached.
     *
     * A cached flag starts at an assumed value that only the next broadcast
     * corrects, and registering [ScreenStateReceiver] delivers future
     * transitions only. Any start that happens while the display is already
     * dark (boot, package replace, sticky restart) would otherwise poll with
     * the screen off until the user cycled the display.
     */
    private val isScreenOn: Boolean
        get() = (getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isInteractive ?: true

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
                if (shouldPoll(currentPreferences.pauseWhenScreenOff, isScreenOn)) {
                    startMonitoring()
                } else {
                    stopMonitoring()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val initialNotification = if (currentPreferences.showNotification) {
            notificationManager.buildNotification(
                speed = SpeedSnapshot(),
                usage = todayUsage,
                unitInBits = currentPreferences.unitInBits,
                showUpload = currentPreferences.showUploadSpeed,
                useDynamicIcon = currentPreferences.showStatusBarIcon
            )
        } else {
            notificationManager.buildServiceNotification()
        }

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

                // Always reconcile: the overlay may have been enabled before the
                // permission was granted, in which case no state transition fires.
                overlayManager.syncVisibility(prefs.showFloatingOverlay, prefs.overlayAlpha)

                // onStartCommand decides whether to poll before this flow has
                // emitted, so it can only read the in-memory default
                // (pauseWhenScreenOff = true). A start that happens while the
                // display is already dark would therefore stay paused even when
                // the stored preference says not to pause, and nothing would
                // revisit that decision until the screen cycled. Reconcile as
                // soon as the real value is known.
                if (lastPauseWhenScreenOff != prefs.pauseWhenScreenOff) {
                    lastPauseWhenScreenOff = prefs.pauseWhenScreenOff
                    reconcilePolling()
                }

                // Turning the live readout off must replace the stale speed text,
                // otherwise the shade keeps showing a frozen value.
                if (prevPrefs.showNotification != prefs.showNotification) {
                    if (prefs.showNotification) {
                        pushNotification(TelemetryManager.currentSpeed.value)
                    } else {
                        notificationManager.updateNotification(
                            notificationManager.buildServiceNotification()
                        )
                    }
                }
            }
        }
    }

    private fun onScreenStateChanged(screenOn: Boolean) = applyPolling(screenOn)

    /**
     * Aligns the polling loop with the current preference and display state.
     *
     * Safe to call repeatedly: [startMonitoring] returns early when the job is
     * already running, and cancelling an idle job is a no-op. Reads the display
     * state live because this runs when preferences arrive, and no broadcast
     * will ever report the state the screen is already in.
     */
    private fun reconcilePolling() = applyPolling(isScreenOn)

    private fun applyPolling(screenOn: Boolean) {
        if (!currentPreferences.isServiceEnabled) return
        if (shouldPoll(currentPreferences.pauseWhenScreenOff, screenOn)) {
            startMonitoring()
        } else {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    private fun startMonitoring() {
        if (pollingJob?.isActive == true) return
        val app = NetPulseApp.instance
        app.trafficDataSource.reset()

        pollingJob = serviceScope.launch {
            while (isActive) {
                val snapshot = app.trafficDataSource.sample()
                TelemetryManager.updateSpeed(snapshot)

                recordConsumption(app)
                pushNotification(snapshot)

                if (currentPreferences.showFloatingOverlay) {
                    overlayManager.updateSpeed(snapshot, currentPreferences.unitInBits)
                }

                delay(Constants.DEFAULT_REFRESH_INTERVAL_MS)
            }
        }
    }

    /**
     * Uses exact byte counters rather than the rate so gaps never skew the ledger.
     *
     * The decision is made before the counters are cleared: an unknown transport
     * holds the bytes for the next tick instead of discarding them.
     */
    private suspend fun recordConsumption(app: NetPulseApp) {
        val delta = app.trafficDataSource.peekDelta()
        if (delta.totalBytes <= 0L) return

        val type = app.networkMonitor.getCurrentConnection().type
        when (val attribution = attributeUsage(type, delta.totalBytes)) {
            is UsageAttribution.Record -> {
                app.trafficDataSource.consumeDelta()
                app.usageRepository.recordUsage(
                    mobileDelta = attribution.mobileBytes,
                    wifiDelta = attribution.wifiBytes
                )
            }
            UsageAttribution.Hold -> Unit
            UsageAttribution.Drop -> app.trafficDataSource.consumeDelta()
        }
    }

    private fun pushNotification(snapshot: SpeedSnapshot) {
        if (!currentPreferences.showNotification) return
        val notification = notificationManager.buildNotification(
            speed = snapshot,
            usage = todayUsage,
            unitInBits = currentPreferences.unitInBits,
            showUpload = currentPreferences.showUploadSpeed,
            useDynamicIcon = currentPreferences.showStatusBarIcon
        )
        notificationManager.updateNotification(notification)
    }

    private fun stopMonitoring() {
        pollingJob?.cancel()
        pollingJob = null
        TelemetryManager.reset()
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

package com.netpulse.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.netpulse.app.NetPulseApp
import com.netpulse.app.core.ActiveNetworkType
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetPulseService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null
    private var isScreenOn = true
    private var lastSnapshotSavedAt = 0L
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
                if (currentPreferences.pauseWhenScreenOff && !isScreenOn) {
                    stopMonitoring()
                } else {
                    startMonitoring()
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
            // Seed before the first poll: the poll loop reads the log and the
            // snapshot on its very first iteration, so loading them from a
            // sibling coroutine would race and lose the restart recovery.
            app.localDataStore.observeAppUsageLog()
                .firstOrNull()
                ?.let(app.appUsageTracker::seedLog)
            app.localDataStore.loadAppUsageSnapshot()
                ?.let(app.appUsageTracker::seedSnapshot)

            while (isActive) {
                val snapshot = app.trafficDataSource.sample()
                TelemetryManager.updateSpeed(snapshot)

                recordConsumption(app)
                pollAppUsage(app)
                pushNotification(snapshot)

                if (currentPreferences.showFloatingOverlay) {
                    overlayManager.updateSpeed(snapshot, currentPreferences.unitInBits)
                }

                delay(Constants.DEFAULT_REFRESH_INTERVAL_MS)
            }
        }
    }

    /** Uses exact byte counters rather than the rate so gaps never skew the ledger. */
    private suspend fun recordConsumption(app: NetPulseApp) {
        val delta = app.trafficDataSource.consumeDelta()
        if (delta.totalBytes <= 0L) return

        when (app.networkMonitor.getCurrentConnection().type) {
            ActiveNetworkType.WIFI -> app.usageRepository.recordUsage(0L, delta.totalBytes)
            ActiveNetworkType.MOBILE -> app.usageRepository.recordUsage(delta.totalBytes, 0L)
            else -> Unit
        }
    }

    /**
     * Polls per-app counters and persists the tracker state.
     *
     * The snapshot is refreshed alongside the log so a restart has a recent
     * reference point, but only every [SNAPSHOT_INTERVAL_MS]: the poll loop runs
     * at 1 Hz and a DataStore write that often is wasted IO.
     */
    private suspend fun pollAppUsage(app: NetPulseApp) {
        val now = System.currentTimeMillis()
        val grew = app.appUsageTracker.poll()
        val snapshot = app.appUsageTracker.currentSnapshot(now)
        val snapshotDue = snapshot != null && now - lastSnapshotSavedAt >= SNAPSHOT_INTERVAL_MS

        when {
            // A missing snapshot only means the sweep has not covered every app
            // yet. The log still has to be written, or early rows are lost.
            grew && snapshot != null ->
                app.localDataStore.saveAppUsageState(app.appUsageTracker.log.value, snapshot)
            grew -> app.localDataStore.saveAppUsageLog(app.appUsageTracker.log.value)
            snapshotDue -> app.localDataStore.saveAppUsageSnapshot(snapshot!!)
            else -> return
        }
        lastSnapshotSavedAt = now
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

    private companion object {
        /** Snapshot cadence; independent of the 1 Hz poll loop. */
        const val SNAPSHOT_INTERVAL_MS = 60_000L
    }
}

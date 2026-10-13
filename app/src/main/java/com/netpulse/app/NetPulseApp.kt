package com.netpulse.app

import android.app.Application
import android.content.Intent
import com.netpulse.app.core.Constants
import com.netpulse.app.core.NetworkMonitor
import com.netpulse.app.data.repository.PreferencesRepositoryImpl
import com.netpulse.app.data.repository.UsageRepositoryImpl
import com.netpulse.app.data.source.LocalDataStore
import com.netpulse.app.data.source.TrafficDataSource
import com.netpulse.app.domain.repository.PreferencesRepository
import com.netpulse.app.domain.repository.UsageRepository
import com.netpulse.app.service.NetPulseService
import com.netpulse.app.service.SpeedNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NetPulseApp : Application() {

    lateinit var localDataStore: LocalDataStore
        private set
    lateinit var preferencesRepository: PreferencesRepository
        private set
    lateinit var usageRepository: UsageRepository
        private set
    lateinit var trafficDataSource: TrafficDataSource
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        localDataStore = LocalDataStore(this)
        preferencesRepository = PreferencesRepositoryImpl(localDataStore)
        usageRepository = UsageRepositoryImpl(localDataStore)
        trafficDataSource = TrafficDataSource()
        networkMonitor = NetworkMonitor(this)

        SpeedNotificationManager.createNotificationChannel(this)

        applicationScope.launch {
            val prefs = preferencesRepository.getPreferences()
            if (prefs.isServiceEnabled) {
                val intent = Intent(this@NetPulseApp, NetPulseService::class.java).apply {
                    action = Constants.ACTION_START_SERVICE
                }
                startForegroundService(intent)
            }
        }
    }

    companion object {
        lateinit var instance: NetPulseApp
            private set
    }
}

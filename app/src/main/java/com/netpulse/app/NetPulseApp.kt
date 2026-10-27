package com.netpulse.app

import android.app.Application
import com.netpulse.app.core.AppUsageTracker
import com.netpulse.app.core.NetworkMonitor
import com.netpulse.app.data.repository.PreferencesRepositoryImpl
import com.netpulse.app.data.repository.UsageRepositoryImpl
import com.netpulse.app.data.source.AppUsageDataSource
import com.netpulse.app.data.source.LocalDataStore
import com.netpulse.app.data.source.TrafficDataSource
import com.netpulse.app.domain.repository.PreferencesRepository
import com.netpulse.app.domain.repository.UsageRepository
import com.netpulse.app.service.SpeedNotificationManager

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
    lateinit var appUsageTracker: AppUsageTracker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        localDataStore = LocalDataStore(this)
        preferencesRepository = PreferencesRepositoryImpl(localDataStore)
        usageRepository = UsageRepositoryImpl(localDataStore)
        trafficDataSource = TrafficDataSource()
        networkMonitor = NetworkMonitor(this)
        appUsageTracker = AppUsageTracker(AppUsageDataSource(this))

        SpeedNotificationManager.createNotificationChannel(this)
    }

    companion object {
        lateinit var instance: NetPulseApp
            private set
    }
}
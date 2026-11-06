package com.netpulse.app.data.repository

import com.netpulse.app.data.source.LocalDataStore
import com.netpulse.app.domain.model.DailyUsage
import com.netpulse.app.domain.model.UsageHistory
import com.netpulse.app.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow

class UsageRepositoryImpl(
    private val localDataStore: LocalDataStore
) : UsageRepository {

    override fun observeTodayUsage(): Flow<DailyUsage> {
        return localDataStore.observeDailyUsage()
    }

    override suspend fun recordUsage(mobileDelta: Long, wifiDelta: Long, hotspotDelta: Long) {
        localDataStore.addUsage(
            mobileDelta = mobileDelta,
            wifiDelta = wifiDelta,
            hotspotDelta = hotspotDelta
        )
    }

    override suspend fun syncHotspotUsage(accurateHotspotBytes: Long) {
        localDataStore.syncHotspotUsage(accurateHotspotBytes)
    }

    override suspend fun resetTodayUsage() {
        localDataStore.resetUsage()
    }

    override fun observeUsageHistory(): Flow<UsageHistory> {
        return localDataStore.observeUsageHistory()
    }
}

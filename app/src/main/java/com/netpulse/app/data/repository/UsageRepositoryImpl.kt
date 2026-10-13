package com.netpulse.app.data.repository

import com.netpulse.app.data.source.LocalDataStore
import com.netpulse.app.domain.model.DailyUsage
import com.netpulse.app.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class UsageRepositoryImpl(
    private val localDataStore: LocalDataStore
) : UsageRepository {

    override fun observeTodayUsage(): Flow<DailyUsage> {
        return localDataStore.observeDailyUsage()
    }

    override suspend fun getTodayUsage(): DailyUsage {
        return localDataStore.observeDailyUsage().first()
    }

    override suspend fun recordUsage(mobileDelta: Long, wifiDelta: Long) {
        localDataStore.addUsage(
            mobileDelta = mobileDelta,
            wifiDelta = wifiDelta
        )
    }

    override suspend fun resetTodayUsage() {
        localDataStore.resetUsage()
    }
}

package com.netpulse.app.domain.repository

import com.netpulse.app.domain.model.DailyUsage
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    fun observeTodayUsage(): Flow<DailyUsage>
    suspend fun getTodayUsage(): DailyUsage
    suspend fun recordUsage(mobileDelta: Long, wifiDelta: Long)
    suspend fun resetTodayUsage()
}

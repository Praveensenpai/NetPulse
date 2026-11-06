package com.netpulse.app.domain.repository

import com.netpulse.app.domain.model.DailyUsage
import com.netpulse.app.domain.model.UsageHistory
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    fun observeTodayUsage(): Flow<DailyUsage>
    suspend fun recordUsage(mobileDelta: Long, wifiDelta: Long, hotspotDelta: Long = 0L)
    suspend fun syncHotspotUsage(accurateHotspotBytes: Long)
    suspend fun resetTodayUsage()
    fun observeUsageHistory(): Flow<UsageHistory>
}

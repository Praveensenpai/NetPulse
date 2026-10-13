package com.netpulse.app.data.source

import android.net.TrafficStats
import com.netpulse.app.domain.model.SpeedSnapshot

class TrafficDataSource(
    private val trafficStatsProvider: TrafficStatsProvider = SystemTrafficStatsProvider
) {
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var lastTimestampMs = 0L

    fun reset() {
        lastRxBytes = -1L
        lastTxBytes = -1L
        lastTimestampMs = 0L
    }

    fun sample(): SpeedSnapshot {
        val currentRx = trafficStatsProvider.getTotalRxBytes()
        val currentTx = trafficStatsProvider.getTotalTxBytes()
        val currentTimeMs = trafficStatsProvider.getCurrentTimeMillis()

        // TrafficStats returns UNSUPPORTED (-1) on unsupported platforms
        if (currentRx < 0 || currentTx < 0) {
            return SpeedSnapshot(0L, 0L, currentTimeMs)
        }

        if (lastRxBytes < 0 || lastTxBytes < 0 || lastTimestampMs <= 0L) {
            lastRxBytes = currentRx
            lastTxBytes = currentTx
            lastTimestampMs = currentTimeMs
            return SpeedSnapshot(0L, 0L, currentTimeMs)
        }

        val elapsedMs = (currentTimeMs - lastTimestampMs).coerceAtLeast(1L)
        val elapsedSec = elapsedMs.toDouble() / 1000.0

        val rxDelta = (currentRx - lastRxBytes).coerceAtLeast(0L)
        val txDelta = (currentTx - lastTxBytes).coerceAtLeast(0L)

        // Handle counter overflow or device reboot
        val safeRxDelta = if (currentRx < lastRxBytes) 0L else rxDelta
        val safeTxDelta = if (currentTx < lastTxBytes) 0L else txDelta

        val downloadBytesPerSec = (safeRxDelta / elapsedSec).toLong()
        val uploadBytesPerSec = (safeTxDelta / elapsedSec).toLong()

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastTimestampMs = currentTimeMs

        return SpeedSnapshot(
            downloadBytesPerSec = downloadBytesPerSec,
            uploadBytesPerSec = uploadBytesPerSec,
            timestamp = currentTimeMs
        )
    }

    fun getLastDeltas(): Pair<Long, Long> {
        return Pair(lastRxBytes, lastTxBytes)
    }
}

interface TrafficStatsProvider {
    fun getTotalRxBytes(): Long
    fun getTotalTxBytes(): Long
    fun getCurrentTimeMillis(): Long
}

object SystemTrafficStatsProvider : TrafficStatsProvider {
    override fun getTotalRxBytes(): Long = TrafficStats.getTotalRxBytes()
    override fun getTotalTxBytes(): Long = TrafficStats.getTotalTxBytes()
    override fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
}

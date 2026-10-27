package com.netpulse.app.data.source

import android.net.TrafficStats
import android.os.SystemClock
import com.netpulse.app.domain.model.SpeedSnapshot

/** Raw byte counts accumulated between two consecutive [sample] calls. */
data class TrafficDelta(val rxBytes: Long, val txBytes: Long) {
    val totalBytes: Long get() = rxBytes + txBytes
}

class TrafficDataSource(
    private val trafficStatsProvider: TrafficStatsProvider = SystemTrafficStatsProvider
) {
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var lastTimestampMs = 0L

    private var pendingRxDelta = 0L
    private var pendingTxDelta = 0L

    fun reset() {
        lastRxBytes = -1L
        lastTxBytes = -1L
        lastTimestampMs = 0L
        pendingRxDelta = 0L
        pendingTxDelta = 0L
    }

    fun sample(): SpeedSnapshot {
        val currentRx = trafficStatsProvider.getTotalRxBytes()
        val currentTx = trafficStatsProvider.getTotalTxBytes()
        val currentTimeMs = trafficStatsProvider.getElapsedRealtimeMs()

        // TrafficStats returns UNSUPPORTED (-1) on unsupported platforms
        if (currentRx < 0 || currentTx < 0) {
            return SpeedSnapshot(0L, 0L, currentTimeMs)
        }

        if (lastRxBytes < 0 || lastTxBytes < 0) {
            lastRxBytes = currentRx
            lastTxBytes = currentTx
            lastTimestampMs = currentTimeMs
            pendingRxDelta = 0L
            pendingTxDelta = 0L
            return SpeedSnapshot(0L, 0L, currentTimeMs)
        }

        val elapsedSec = ((currentTimeMs - lastTimestampMs).coerceAtLeast(1L)).toDouble() / 1000.0

        // Handle counter reset (device reboot or interface reset)
        val rxDelta = if (currentRx < lastRxBytes) 0L else currentRx - lastRxBytes
        val txDelta = if (currentTx < lastTxBytes) 0L else currentTx - lastTxBytes

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastTimestampMs = currentTimeMs

        pendingRxDelta += rxDelta
        pendingTxDelta += txDelta

        return SpeedSnapshot(
            downloadBytesPerSec = (rxDelta / elapsedSec).toLong(),
            uploadBytesPerSec = (txDelta / elapsedSec).toLong(),
            timestamp = currentTimeMs
        )
    }

    /**
     * Returns the exact byte counts observed since the previous call and clears them.
     * Usage accounting must use this instead of the per-second rate, otherwise gaps
     * (screen-off, slow ticks) silently under- or over-report consumption.
     */
    fun consumeDelta(): TrafficDelta {
        val delta = TrafficDelta(pendingRxDelta, pendingTxDelta)
        pendingRxDelta = 0L
        pendingTxDelta = 0L
        return delta
    }
}

interface TrafficStatsProvider {
    fun getTotalRxBytes(): Long
    fun getTotalTxBytes(): Long
    fun getElapsedRealtimeMs(): Long
}

object SystemTrafficStatsProvider : TrafficStatsProvider {
    override fun getTotalRxBytes(): Long = TrafficStats.getTotalRxBytes()
    override fun getTotalTxBytes(): Long = TrafficStats.getTotalTxBytes()
    override fun getElapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}

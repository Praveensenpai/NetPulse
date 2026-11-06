package com.netpulse.app.data.source

import android.net.TrafficStats
import android.os.SystemClock
import com.netpulse.app.domain.model.SpeedSnapshot

/** Raw byte counts accumulated between two consecutive [sample] calls. */
data class TrafficDelta(
    val rxBytes: Long,
    val txBytes: Long,
    val hotspotBytes: Long = 0L
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

class TrafficDataSource(
    private val trafficStatsProvider: TrafficStatsProvider = SystemTrafficStatsProvider,
    private val tetheredInterfacesProvider: () -> List<String> = { emptyList() }
) {
    // Mutated from two threads: sample/peek/consume run on the polling loop's
    // Dispatchers.Default scope, while reset and catchUp come from the service
    // callbacks on the main thread. Cancellation is cooperative, so a tick that
    // is already running keeps going after stopMonitoring() nulls the job and
    // can overlap the next catchUp(). The fields below are a read-modify-write
    // set, so without exclusion two callers can count the same bytes twice or
    // drop an increment entirely. Every public method is @Synchronized; the
    // call rate is 1 Hz, so the monitor is uncontended in practice.
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var lastTimestampMs = 0L

    private var pendingRxDelta = 0L
    private var pendingTxDelta = 0L
    private var pendingHotspotDelta = 0L

    private val lastIfaceRxMap = mutableMapOf<String, Long>()
    private val lastIfaceTxMap = mutableMapOf<String, Long>()

    @Synchronized
    fun reset() {
        lastRxBytes = -1L
        lastTxBytes = -1L
        lastTimestampMs = 0L
        pendingRxDelta = 0L
        pendingTxDelta = 0L
        pendingHotspotDelta = 0L
        lastIfaceRxMap.clear()
        lastIfaceTxMap.clear()
    }

    /**
     * Folds traffic moved while polling was stopped into the pending delta.
     *
     * [reset] discards the byte baseline, so every byte transferred between the
     * last sample and the resume became invisible to the ledger: a screen-off
     * interval silently erased that period's usage. Re-baselining here instead
     * keeps the gap and returns the next tick's rate to normal.
     *
     * Safe to call before the first sample and across counter resets; both are
     * no-ops rather than negative deltas.
     */
    @Synchronized
    fun catchUp() {
        if (lastRxBytes < 0 || lastTxBytes < 0) return
        val currentRx = trafficStatsProvider.getTotalRxBytes()
        val currentTx = trafficStatsProvider.getTotalTxBytes()
        if (currentRx < 0 || currentTx < 0) return

        if (currentRx >= lastRxBytes) pendingRxDelta += currentRx - lastRxBytes
        if (currentTx >= lastTxBytes) pendingTxDelta += currentTx - lastTxBytes

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastTimestampMs = trafficStatsProvider.getElapsedRealtimeMs()
    }

    @Synchronized
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
            pendingHotspotDelta = 0L
            sampleTetheredInterfaces(currentTimeMs)
            return SpeedSnapshot(0L, 0L, currentTimeMs)
        }

        val elapsedSec = ((currentTimeMs - lastTimestampMs).coerceAtLeast(1L)).toDouble() / 1000.0

        // Handle counter reset (device reboot or interface reset)
        val rxDelta = if (currentRx < lastRxBytes) 0L else currentRx - lastRxBytes
        val txDelta = if (currentTx < lastTxBytes) 0L else currentTx - lastTxBytes

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastTimestampMs = currentTimeMs

        val tetherStats = sampleTetheredInterfaces(currentTimeMs)

        pendingRxDelta += rxDelta
        pendingTxDelta += txDelta
        pendingHotspotDelta += tetherStats.totalDelta

        // When tethering, downstream internet traffic forwarded to clients via softAP
        // appears in totalTxBytes as upload. Deducting the forwarded client download
        // preserves true uplink metrics instead of mirroring download as false upload.
        val adjustedTxDelta = (txDelta - tetherStats.txDelta).coerceAtLeast(0L)

        return SpeedSnapshot(
            downloadBytesPerSec = (rxDelta / elapsedSec).toLong(),
            uploadBytesPerSec = (adjustedTxDelta / elapsedSec).toLong(),
            timestamp = currentTimeMs
        )
    }

    private data class TetherDelta(val rxDelta: Long, val txDelta: Long) {
        val totalDelta: Long get() = rxDelta + txDelta
    }

    private fun sampleTetheredInterfaces(nowMs: Long): TetherDelta {
        val activeIfaces = tetheredInterfacesProvider()
        if (activeIfaces.isEmpty()) {
            lastIfaceRxMap.clear()
            lastIfaceTxMap.clear()
            return TetherDelta(0L, 0L)
        }

        var totalRxDelta = 0L
        var totalTxDelta = 0L

        for (iface in activeIfaces) {
            val curRx = trafficStatsProvider.getInterfaceRxBytes(iface)
            val curTx = trafficStatsProvider.getInterfaceTxBytes(iface)
            if (curRx < 0L || curTx < 0L) continue

            val prevRx = lastIfaceRxMap[iface]
            val prevTx = lastIfaceTxMap[iface]

            if (prevRx != null && curRx >= prevRx) {
                totalRxDelta += curRx - prevRx
            }
            if (prevTx != null && curTx >= prevTx) {
                totalTxDelta += curTx - prevTx
            }

            lastIfaceRxMap[iface] = curRx
            lastIfaceTxMap[iface] = curTx
        }

        return TetherDelta(totalRxDelta, totalTxDelta)
    }

    /**
     * Returns the exact byte counts observed since the previous [consumeDelta]
     * without clearing them.
     *
     * Usage accounting must decide whether the bytes can be attributed before it
     * commits to clearing them. Reading and clearing in one step forced the caller
     * to drop bytes whenever the active transport was unknown.
     */
    @Synchronized
    fun peekDelta(): TrafficDelta = TrafficDelta(pendingRxDelta, pendingTxDelta, pendingHotspotDelta)

    /**
     * Returns the exact byte counts observed since the previous call and clears them.
     * Usage accounting must use this instead of the per-second rate, otherwise gaps
     * (screen-off, slow ticks) silently under- or over-report consumption.
     */
    @Synchronized
    fun consumeDelta(): TrafficDelta {
        val delta = peekDelta()
        pendingRxDelta = 0L
        pendingTxDelta = 0L
        pendingHotspotDelta = 0L
        return delta
    }
}

interface TrafficStatsProvider {
    fun getTotalRxBytes(): Long
    fun getTotalTxBytes(): Long
    fun getElapsedRealtimeMs(): Long
    fun getInterfaceRxBytes(iface: String): Long = 0L
    fun getInterfaceTxBytes(iface: String): Long = 0L
}

object SystemTrafficStatsProvider : TrafficStatsProvider {
    override fun getTotalRxBytes(): Long = TrafficStats.getTotalRxBytes()
    override fun getTotalTxBytes(): Long = TrafficStats.getTotalTxBytes()
    override fun getElapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun getInterfaceRxBytes(iface: String): Long = TrafficStats.getRxBytes(iface)
    override fun getInterfaceTxBytes(iface: String): Long = TrafficStats.getTxBytes(iface)
}

package com.netpulse.app.domain.model

/** Mobile and Wi-Fi byte totals over some window. */
data class UsageRange(
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L
) {
    val totalBytes: Long get() = mobileBytes + wifiBytes

    operator fun plus(other: UsageRange): UsageRange = UsageRange(
        mobileBytes = mobileBytes + other.mobileBytes,
        wifiBytes = wifiBytes + other.wifiBytes
    )

    /**
     * Per-transport maximum.
     *
     * Keeps all-time monotonic for installs that upgraded before the lifetime
     * counters existed: their retained days are the only evidence of older
     * usage, and the counters only ever overtake that figure.
     */
    fun atLeast(other: UsageRange): UsageRange = UsageRange(
        mobileBytes = maxOf(mobileBytes, other.mobileBytes),
        wifiBytes = maxOf(wifiBytes, other.wifiBytes)
    )

    companion object {
        val ZERO = UsageRange()
    }
}

/**
 * Usage over the windows the UI exposes.
 *
 * [allTime] comes from monotonic lifetime counters rather than the per-day
 * buckets, so it stays exact after old days are pruned. [days] is the retained
 * per-day ledger, oldest first, for the sparkline.
 */
data class UsageHistory(
    val today: UsageRange = UsageRange.ZERO,
    val last7Days: UsageRange = UsageRange.ZERO,
    val last30Days: UsageRange = UsageRange.ZERO,
    val allTime: UsageRange = UsageRange.ZERO,
    val days: List<DailyUsage> = emptyList()
) {
    companion object {
        val EMPTY = UsageHistory()
    }
}

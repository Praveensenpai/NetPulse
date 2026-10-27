package com.netpulse.app.core

import com.netpulse.app.data.source.AppUsageDataSource
import com.netpulse.app.domain.model.AppUsage
import com.netpulse.app.domain.model.UsageLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Derives per-app activity from NetworkStatsManager counter deltas and folds
 * the deltas into a bounded minute-resolution usage log.
 *
 * NetworkStatsManager answers from persisted buckets that the system rewrites
 * periodically, so deltas arrive in bursts rather than every second and can
 * jitter slightly negative. Deltas are therefore clamped, and the reported
 * rate decays after the last observed movement instead of dropping instantly.
 */
class AppUsageTracker(private val dataSource: AppUsageDataSource) {

    private val _liveApps = MutableStateFlow<List<AppUsage>>(emptyList())
    val liveApps: StateFlow<List<AppUsage>> = _liveApps.asStateFlow()

    private val _log = MutableStateFlow<List<UsageLogEntry>>(emptyList())
    val log: StateFlow<List<UsageLogEntry>> = _log.asStateFlow()

    private var lastTotals: Map<String, Long> = emptyMap()
    private var lastPollMs = 0L
    private val rateHold = HashMap<String, Hold>()

    private data class Hold(val bytesPerSec: Long, val atMs: Long)

    fun hasUsageAccess(): Boolean = dataSource.hasUsageAccess()

    fun seedLog(entries: List<UsageLogEntry>) {
        if (_log.value.isNotEmpty()) return
        _log.value = entries.sortedByDescending { it.minuteMs }.take(MAX_LOG_ENTRIES)
    }

    fun reset() {
        lastTotals = emptyMap()
        lastPollMs = 0L
        rateHold.clear()
        _liveApps.value = emptyList()
    }

    /** @return true when the log grew and should be persisted. */
    fun poll(): Boolean {
        if (!dataSource.hasUsageAccess()) {
            reset()
            return false
        }

        val now = System.currentTimeMillis()
        val sinceQueryMs = now - lastPollMs
        val shouldQuery = lastPollMs == 0L || sinceQueryMs >= QUERY_INTERVAL_MS

        if (!shouldQuery) {
            // The query is expensive; between queries only age the held rates.
            _liveApps.value = _liveApps.value
                .map { it.copy(bytesPerSec = decayedRate(it.packageName, now)) }
                .sortedWith(
                    compareByDescending<AppUsage> { it.bytesPerSec }
                        .thenByDescending { it.totalBytes }
                )
            return false
        }

        val totals = dataSource.queryTodayTotals()
        val elapsedMs = if (lastPollMs == 0L) 0L else sinceQueryMs.coerceAtLeast(1L)

        val deltas = HashMap<String, Long>()
        val apps = ArrayList<AppUsage>(totals.size)

        for ((packageName, total) in totals) {
            val previous = lastTotals[packageName]
            val delta = if (previous == null) 0L else (total - previous).coerceAtLeast(0L)
            if (delta > 0L) deltas[packageName] = delta

            val liveRate = if (delta > 0L && elapsedMs > 0L) {
                val instant = delta * 1000L / elapsedMs
                rateHold[packageName] = Hold(instant, now)
                instant
            } else {
                decayedRate(packageName, now)
            }

            apps.add(
                AppUsage(
                    packageName = packageName,
                    label = dataSource.labelFor(packageName),
                    totalBytes = total,
                    bytesPerSec = liveRate
                )
            )
        }

        val logGrew = lastPollMs != 0L && appendToLog(deltas, now)

        lastTotals = totals
        lastPollMs = now
        _liveApps.value = apps.sortedWith(
            compareByDescending<AppUsage> { it.bytesPerSec }.thenByDescending { it.totalBytes }
        )
        return logGrew
    }

    private fun decayedRate(packageName: String, now: Long): Long {
        val hold = rateHold[packageName] ?: return 0L
        val ageMs = now - hold.atMs
        if (ageMs >= RATE_DECAY_MS) {
            rateHold.remove(packageName)
            return 0L
        }
        val remaining = 1f - (ageMs.toFloat() / RATE_DECAY_MS.toFloat())
        return (hold.bytesPerSec * remaining).toLong()
    }

    private fun appendToLog(deltas: Map<String, Long>, now: Long): Boolean {
        val minuteMs = now - (now % MINUTE_MS)
        if (deltas.isEmpty()) return false

        val current = _log.value.toMutableList()
        for ((packageName, bytes) in deltas) {
            val index = current.indexOfFirst {
                it.minuteMs == minuteMs && it.packageName == packageName
            }
            if (index >= 0) {
                val existing = current[index]
                current[index] = existing.copy(bytes = existing.bytes + bytes)
            } else {
                current.add(
                    UsageLogEntry(
                        minuteMs = minuteMs,
                        packageName = packageName,
                        label = dataSource.labelFor(packageName),
                        bytes = bytes
                    )
                )
            }
        }
        _log.value = current
            .sortedByDescending { it.minuteMs }
            .take(MAX_LOG_ENTRIES)
        return true
    }

    companion object {
        const val MAX_LOG_ENTRIES = 300
        private const val MINUTE_MS = 60_000L
        private const val RATE_DECAY_MS = 8_000L
        private const val QUERY_INTERVAL_MS = 2_000L
    }
}
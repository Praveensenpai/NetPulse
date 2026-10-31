package com.netpulse.app.core

import com.netpulse.app.data.source.AppUsageDataSource
import com.netpulse.app.data.source.AppUsageSnapshot
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
    private var pendingSnapshot: AppUsageSnapshot? = null
    private var carryUntilMs = 0L
    private var seededTotals: Map<String, Long> = emptyMap()
    private var seedUntilMs = 0L

    private data class Hold(val bytesPerSec: Long, val atMs: Long)

    fun hasUsageAccess(): Boolean = dataSource.hasUsageAccess()

    fun seedLog(entries: List<UsageLogEntry>) {
        if (_log.value.isNotEmpty()) return
        _log.value = entries.sortedByDescending { it.minuteMs }.take(MAX_LOG_ENTRIES)
    }

    /**
     * Offers totals persisted by a previous process so the first poll can
     * recover traffic that moved while the service was not running.
     *
     * Adoption is validated in [adoptSnapshot]: the snapshot must come from the
     * same accounting mode, the same day, and within [RECOVERY_WINDOW_MS].
     * Anything else is dropped, because subtracting mismatched totals would
     * invent or destroy bytes in the log.
     */
    fun seedSnapshot(snapshot: AppUsageSnapshot) {
        if (lastPollMs != 0L) return
        pendingSnapshot = snapshot
    }

    /**
     * Totals to persist, or null when there is nothing complete to persist.
     *
     * Returns null until the sweep has covered every app: a partial map is
     * actively harmful as a baseline, because every missing package looks like
     * "no previous total" on restart and its bytes are dropped.
     */
    fun currentSnapshot(nowMs: Long): AppUsageSnapshot? {
        if (lastPollMs == 0L) return null
        if (!dataSource.sweepComplete()) return null
        return AppUsageSnapshot(nowMs, dataSource.usesBaseline(), lastTotals)
    }

    fun reset() {
        lastTotals = emptyMap()
        lastPollMs = 0L
        rateHold.clear()
        pendingSnapshot = null
        carryUntilMs = 0L
        seededTotals = emptyMap()
        seedUntilMs = 0L
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
        val firstPoll = lastPollMs == 0L
        if (firstPoll) adoptSnapshot(totals, now)
        val elapsedMs = if (lastPollMs == 0L) 0L else sinceQueryMs.coerceAtLeast(1L)

        val deltas = HashMap<String, Long>()
        val apps = ArrayList<AppUsage>(totals.size)
        // netstats publishes buckets lazily, so right after a start the sweep
        // has nothing for most apps and the live list looks empty next to a
        // full usage log. Seed it from the persisted log and hold the seed
        // until the sweep reports each package, or until SEED_CARRY_MS passes.
        if (firstPoll) {
            seededTotals = seedTotalsFromLog(now)
            seedUntilMs = now + SEED_CARRY_MS
        }
        val carrySeed = now < seedUntilMs

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

            // Zero-byte packages are kept in `totals` (they are real,
            // measured values) but must not be listed in the UI.
            if (total > 0L) {
                apps.add(
                    AppUsage(
                        packageName = packageName,
                        label = dataSource.labelFor(packageName),
                        totalBytes = total,
                        bytesPerSec = liveRate
                    )
                )
            }
        }

        // Seeded rows keep the card honest until the sweep reports a real
        // measurement. Presence in `totals` is not enough to drop a seed:
        // netstats returns the package key with 0 bytes long before it
        // publishes the bucket, and dropping on key presence made the card
        // empty right after a start.
        if (carrySeed) {
            seededTotals = seededRows(seededTotals, totals)
            for ((packageName, total) in seededTotals) {
                apps.add(
                    AppUsage(
                        packageName = packageName,
                        label = dataSource.labelFor(packageName),
                        totalBytes = total,
                        bytesPerSec = 0L
                    )
                )
            }
        }

        val logGrew = appendToLog(deltas, now)

        // The per-UID sweep covers only SWEEP_SLICE apps per poll, so right
        // after a snapshot is adopted most packages are still missing from
        // `totals`. Overwriting lastTotals would erase their adopted baseline
        // and the bytes moved while the service was down would be lost when
        // the sweep finally reaches them (previous == null -> delta 0).
        // Carried entries are dropped as soon as the sweep reports the package.
        lastTotals = mergedTotals(lastTotals, totals, carry = now < carryUntilMs)
        lastPollMs = now
        _liveApps.value = apps.sortedWith(
            compareByDescending<AppUsage> { it.bytesPerSec }.thenByDescending { it.totalBytes }
        )
        return logGrew
    }

    /**
     * Restores [lastTotals] from a persisted snapshot when it is safe to compare.
     *
     * Rejected cases: totals are empty, the snapshot was produced by a different
     * accounting mode (baseline-subtracted and whole-bucket sums are not
     * comparable), it is newer than now, older than [RECOVERY_WINDOW_MS], or it
     * predates local midnight.
     */
    private fun adoptSnapshot(totals: Map<String, Long>, nowMs: Long) {
        val snapshot = pendingSnapshot ?: return
        pendingSnapshot = null
        if (totals.isEmpty()) return
        val usable = isAdoptable(
            snapshotAtMs = snapshot.atMs,
            snapshotUsesBaseline = snapshot.usesBaseline,
            nowMs = nowMs,
            currentUsesBaseline = dataSource.usesBaseline(),
            dayStartMs = AppUsageDataSource.startOfDayMs(nowMs)
        )
        if (usable) {
            lastTotals = snapshot.totals
            carryUntilMs = nowMs + CARRY_MS
        }
    }

    /**
     * Today's per-package bytes, summed from the persisted log.
     *
     * Only consulted on the first poll after a start. netstats publishes
     * per-UID buckets lazily, so for the first seconds the sweep map holds a
     * handful of packages while the log already knows the whole day. Seeding
     * from it keeps the live card from reading as "you only used one app".
     */
    private fun seedTotalsFromLog(nowMs: Long): Map<String, Long> =
        seedTotals(_log.value, AppUsageDataSource.startOfDayMs(nowMs))

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
        /**
         * Whether a persisted snapshot may be used as the previous totals.
         *
         * All four conditions guard against inventing or erasing log bytes:
         * a different accounting mode is not comparable, a future timestamp is
         * corrupt, a snapshot older than [RECOVERY_WINDOW_MS] cannot be
         * attributed to a single minute, and one from before local midnight
         * belongs to a different day's counters.
         */
        internal fun isAdoptable(
            snapshotAtMs: Long,
            snapshotUsesBaseline: Boolean,
            nowMs: Long,
            currentUsesBaseline: Boolean,
            dayStartMs: Long
        ): Boolean {
            if (snapshotUsesBaseline != currentUsesBaseline) return false
            if (snapshotAtMs > nowMs) return false
            if (nowMs - snapshotAtMs > RECOVERY_WINDOW_MS) return false
            if (snapshotAtMs < dayStartMs) return false
            return true
        }

        /**
         * Seeded rows that still stand in for a package the sweep has not
         * measured yet.
         *
         * A package is only superseded once the sweep reports a positive
         * total. Testing key presence instead dropped every seed on the first
         * poll, because netstats lists the UID with 0 bytes before the bucket
         * is published, which left the live card empty after a restart.
         */
        internal fun seededRows(
            seeded: Map<String, Long>,
            measured: Map<String, Long>
        ): Map<String, Long> =
            seeded.filter { (packageName, total) ->
                total > 0L && (measured[packageName] ?: 0L) <= 0L
            }

        /**
         * Sums today's persisted log rows into per-package totals.
         *
         * Used only to seed the live list on the first poll after a start,
         * when netstats has not yet published per-UID buckets. Rows from before
         * [dayStartMs] belong to yesterday's counters and are excluded.
         */
        internal fun seedTotals(log: List<UsageLogEntry>, dayStartMs: Long): Map<String, Long> {
            val out = HashMap<String, Long>()
            for (entry in log) {
                if (entry.minuteMs < dayStartMs || entry.bytes <= 0L) continue
                out[entry.packageName] = (out[entry.packageName] ?: 0L) + entry.bytes
            }
            return out
        }

        // Deliberately short. A longer window would fold an unbounded amount of
        // traffic into a single minute row, which misreports when the bytes
        // were actually used. Snapshots are written every 60 s, so a genuine
        // restart gap is normally a couple of minutes.
        private const val RECOVERY_WINDOW_MS = 300_000L
        // Comfortably longer than one full sweep (~400 UIDs at SWEEP_SLICE per
        // poll), so every carried baseline gets a chance to be superseded.
        private const val CARRY_MS = 180_000L
        // How long a seeded live row may stand in for a package the sweep has
        // not reported yet. netstats can take minutes to publish a bucket, so
        // this is generous; a real measurement always replaces it sooner.
        private const val SEED_CARRY_MS = 180_000L

        /**
         * Combines carried baselines with a fresh sweep result.
         *
         * While a recovered snapshot is still being superseded, packages the
         * sweep has not reached yet must keep their adopted baseline, otherwise
         * their downtime bytes are lost when they are finally queried. Fresh
         * values always win over carried ones, so the carry shrinks every poll
         * and disappears once the sweep has covered every app.
         */
        internal fun mergedTotals(
            previous: Map<String, Long>,
            fresh: Map<String, Long>,
            carry: Boolean
        ): Map<String, Long> {
            if (!carry) return fresh
            val merged = HashMap(previous)
            merged.putAll(fresh)
            return merged
        }
    }
}
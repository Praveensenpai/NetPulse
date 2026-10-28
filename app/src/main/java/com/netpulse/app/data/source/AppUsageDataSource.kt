package com.netpulse.app.data.source

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process
import android.util.Log
import java.util.Calendar

class AppUsageDataSource(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val statsManager: NetworkStatsManager? =
        context.getSystemService(NetworkStatsManager::class.java)
    private var uidToPackage: Map<Int, String> = emptyMap()
    private var packageMapBuiltAt = 0L
    private val labelCache = HashMap<String, String>()

    private val sweepTotals = HashMap<String, Long>()
    private val pendingSweep = LinkedHashMap<String, Long>()
    private var sweepDay = 0L
    private var lastFallbackAt = 0L
    private val baselines = HashMap<Int, Long>()
    private var baselineDay = 0L
    private var baselineClosed = false

    /**
     * True when this process has captured midnight baselines, so its totals are
     * baseline-subtracted rather than whole-bucket sums.
     *
     * A restart usually lands outside the baseline window, so the new process
     * reports whole-bucket sums and the two modes must not be mixed when a
     * snapshot is restored across a restart.
     */
    internal fun usesBaseline(): Boolean = baselines.isNotEmpty()

    /**
     * True when the per-UID sweep has queried every installed app at least once
     * today, so the current totals map is complete.
     *
     * Only a complete map is worth persisting as a restart baseline: a partial
     * map silently drops every app the sweep had not reached yet, which is how
     * a 12 MB transfer during downtime went missing. Device-verified before the
     * guard: the snapshot taken 10 s after start had no YouTube entry at all.
     */
    internal fun sweepComplete(): Boolean = pendingSweep.isEmpty() && sweepTotals.isNotEmpty()

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun queryTodayTotals(): Map<String, Long> {
        val manager = statsManager ?: return emptyMap()
        if (!hasUsageAccess()) return emptyMap()

        val dayStart = startOfTodayMs()
        val now = System.currentTimeMillis()
        // Buckets are only returned when fully inside [start, end] and the
        // bucket containing "now" is still open, so the window is padded past
        // it. Without this the newest bucket is invisible for up to one
        // bucketDuration, which looked like a stalled counter.
        val queryEnd = now + BUCKET_PAD_MS
        refreshPackageMapIfStale()

        if (sweepDay != dayStart) {
            sweepTotals.clear()
            pendingSweep.clear()
            sweepDay = dayStart
        }

        if (baselineDay != dayStart) {
            baselines.clear()
            baselineDay = dayStart
            // Only a service already running near midnight can capture a
            // meaningful baseline. Starting later means the day's earlier bytes
            // must come from whole-bucket sums, or they would be subtracted away.
            baselineClosed = !withinBaselineWindow(now, dayStart)
        } else if (!baselineClosed && !withinBaselineWindow(now, dayStart)) {
            baselineClosed = true
        }

        // Apps that already reported traffic are refreshed every poll, so a
        // streaming app's total advances at the poll rate instead of waiting
        // for the full sweep to come back around.
        for (packageName in sweepTotals.keys.toList()) {
            val uid = uidForPackage(packageName) ?: continue
            val bytes = todayBytes(manager, uid, dayStart, queryEnd)
            recordTotal(sweepTotals, packageName, bytes)
        }

        // Discovery is the expensive part (~400 IPC for every UID), so it is
        // spread across polls in fixed slices.
        if (pendingSweep.isEmpty()) {
            for ((uid, packageName) in uidToPackage) {
                if (packageName !in sweepTotals) pendingSweep[packageName] = uid.toLong()
            }
        }

        val batch = pendingSweep.entries.take(SWEEP_SLICE)
        for (entry in batch) {
            val uid = entry.value.toInt()
            val bytes = todayBytes(manager, uid, dayStart, queryEnd)
            recordTotal(sweepTotals, entry.key, bytes)
            pendingSweep.remove(entry.key)
        }

        // Some devices report per-UID history only for the aggregate bucket
        // (uid=-1), which leaves the per-UID pass empty. Fall back to a
        // chunked query, but at a low rate: it is a whole-day scan.
        if (sweepTotals.isEmpty() && now - lastFallbackAt > FALLBACK_INTERVAL_MS) {
            lastFallbackAt = now
            for ((key, value) in queryChunked(manager, dayStart, queryEnd)) {
                val known = sweepTotals[key] ?: 0L
                if (value > known) sweepTotals[key] = value
            }
        }
        return sweepTotals.toMap()
    }

    private fun queryChunked(
        manager: NetworkStatsManager,
        start: Long,
        end: Long
    ): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            collectChunks(manager, type, start, end, out)
        }
        return out
    }

    private fun collectChunks(
        manager: NetworkStatsManager,
        type: Int,
        start: Long,
        end: Long,
        out: MutableMap<String, Long>
    ) {
        val stats = try {
            manager.queryDetails(type, null, start, end)
        } catch (e: Exception) {
            Log.w(TAG, "queryDetails failed", e)
            return
        }
        val window = HashMap<String, Long>()
        var rows = 0
        try {
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                if (!countsTowardDay(bucket.endTimeStamp, start)) continue
                rows++
                val pkg = uidToPackage[bucket.uid] ?: continue
                window[pkg] = (window[pkg] ?: 0L) + bucket.rxBytes + bucket.txBytes
            }
        } catch (e: Exception) {
            Log.w(TAG, "bucket read failed", e)
            return
        } finally {
            stats.close()
        }
        if (rows >= ROW_CAP && end - start > MIN_SPLIT_MS) {
            val mid = start + (end - start) / 2
            collectChunks(manager, type, start, mid, out)
            collectChunks(manager, type, mid, end, out)
            return
        }
        for ((key, value) in window) out[key] = (out[key] ?: 0L) + value
    }

    private fun uidForPackage(packageName: String): Int? {
        refreshPackageMapIfStale()
        return uidToPackage.entries.firstOrNull { it.value == packageName }?.key
    }

    /**
     * Bytes attributable to today for one UID.
     *
     * The bucket covering local midnight straddles it (verified: the grid is
     * :30-aligned, so the first bucket of the day runs 23:30-01:30). Keeping it
     * whole overstates today by the pre-midnight portion. When the service is
     * running across midnight a baseline of the UID's cumulative total is taken
     * on its first visit, and today becomes `all - baseline`, which is exact.
     *
     * If no baseline was captured in time (service started later in the day)
     * this falls back to the whole-bucket sum, which is the previous behavior.
     * The first visit after midnight only records the baseline, so the figure
     * grows from zero instead of jumping up and collapsing on the next poll.
     */
    private fun todayBytes(
        manager: NetworkStatsManager,
        uid: Int,
        dayStart: Long,
        queryEnd: Long
    ): Long {
        val base = baselines[uid]
        if (base == null) {
            if (baselineClosed) return queryUid(manager, uid, dayStart, queryEnd)
            // First visit after midnight: record the cumulative total and
            // report nothing yet. Returning the whole-bucket sum here would
            // include the pre-midnight part of the straddling bucket, and the
            // next poll would drop back to the exact figure, making the
            // displayed total collapse from a large inflated value to near zero.
            baselines[uid] = queryUid(manager, uid, 0L, queryEnd)
            return 0L
        }
        val all = queryUid(manager, uid, 0L, queryEnd)
        val since = sinceBaseline(all, base)
        if (since == null) {
            // The baseline now points past the retained history. Re-baseline on
            // the current cumulative total so this app keeps reporting instead
            // of sitting at 0 B until tomorrow.
            baselines[uid] = all
            return 0L
        }
        return since
    }

    private fun queryUid(
        manager: NetworkStatsManager,
        uid: Int,
        start: Long,
        end: Long
    ): Long {
        val wifi = uidBytes(manager, ConnectivityManager.TYPE_WIFI, uid, start, end)
        val mobile = uidBytes(manager, ConnectivityManager.TYPE_MOBILE, uid, start, end)
        return wifi + mobile
    }

    private fun uidBytes(
        manager: NetworkStatsManager,
        type: Int,
        uid: Int,
        start: Long,
        end: Long
    ): Long {
        val stats = try {
            manager.queryDetailsForUid(type, null, start, end, uid)
        } catch (e: Exception) {
            Log.w(TAG, "queryDetailsForUid failed", e)
            return 0L
        }

        return try {
            val bucket = NetworkStats.Bucket()
            var sum = 0L
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                if (!countsTowardDay(bucket.endTimeStamp, start)) continue
                sum += bucket.rxBytes + bucket.txBytes
            }
            sum
        } catch (e: Exception) {
            Log.w(TAG, "bucket read failed", e)
            0L
        } finally {
            stats.close()
        }
    }

    fun labelFor(packageName: String): String {
        labelCache[packageName]?.let { return it }
        val resolved = try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
        labelCache[packageName] = resolved
        return resolved
    }

    private fun refreshPackageMapIfStale() {
        val now = System.currentTimeMillis()
        if (uidToPackage.isNotEmpty() && now - packageMapBuiltAt < PACKAGE_MAP_TTL_MS) return
        val map = HashMap<Int, String>()
        val apps = try {
            packageManager.getInstalledApplications(0)
        } catch (e: Exception) {
            Log.w(TAG, "package enumeration failed", e)
            return
        }
        for (app in apps) {
            map.putIfAbsent(app.uid, app.packageName)
        }
        uidToPackage = map
        packageMapBuiltAt = now
    }

    private fun startOfTodayMs(): Long = startOfDayMs(System.currentTimeMillis())

    companion object {
        private const val TAG = "AppUsageDataSource"
        private const val PACKAGE_MAP_TTL_MS = 60_000L
        private const val SWEEP_SLICE = 16
        private const val ROW_CAP = 100
        private const val MIN_SPLIT_MS = 60_000L
        private const val FALLBACK_INTERVAL_MS = 120_000L
        // Generous pad: bucket duration varies by OEM (2 h on AOSP, longer on
        // some Samsung builds). Padding past the open bucket is safe because
        // buckets only exist in the past and the dayStart filter drops any
        // bucket that ends before local midnight.
        private const val BUCKET_PAD_MS = 43_200_000L
        private const val BASELINE_GRACE_MS = 120_000L

        /** Local midnight for the day containing [nowMs]. */
        internal fun startOfDayMs(nowMs: Long): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return calendar.timeInMillis
        }

        /** True while a midnight-crossing baseline is still worth capturing. */
        internal fun withinBaselineWindow(nowMs: Long, dayStartMs: Long): Boolean =
            nowMs - dayStartMs <= BASELINE_GRACE_MS

        /**
         * A bucket counts toward today when it ends after local midnight.
         *
         * A bucket that straddles midnight is deliberately kept: [NetworkStats.Bucket]
         * exposes whole-bucket totals only, so the portion before midnight cannot
         * be attributed separately. Such a bucket is counted in full, which can
         * overstate today's first bucket by up to one bucketDuration of yesterday.
         */
        internal fun countsTowardDay(bucketEndMs: Long, dayStartMs: Long): Boolean =
            bucketEndMs > dayStartMs

        /**
         * Bytes accrued since [baseline] was taken, or null when [all] fell
         * below it.
         *
         * A drop means netstats trimmed the buckets the baseline was taken
         * against (history rotation, or a reboot that reset the counters). The
         * stored baseline is then meaningless and the caller must re-baseline;
         * subtracting it anyway and clamping at zero would freeze the app at
         * 0 B until the next midnight.
         */
        internal fun sinceBaseline(all: Long, baseline: Long): Long? =
            if (all < baseline) null else all - baseline

        /**
         * Records [bytes] for [key], dropping the entry when the figure is 0.
         *
         * A zero is not always "no traffic": the re-baseline poll returns 0
         * while it re-anchors a UID whose history was trimmed. Keeping the old
         * entry then would display a total computed against a baseline that no
         * longer exists, so the row is cleared instead and reappears once real
         * bytes are measured again.
         */
        internal fun recordTotal(
            totals: MutableMap<String, Long>,
            key: String,
            bytes: Long
        ) {
            if (bytes > 0L) totals[key] = bytes else totals.remove(key)
        }
    }
}

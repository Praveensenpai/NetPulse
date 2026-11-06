package com.netpulse.app.data.source

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

class TetheringStatsProvider(
    private val context: Context,
    private val networkStatsManager: NetworkStatsManager? =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
) {
    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun queryTodayTetheringBytes(zoneId: ZoneId = ZoneId.systemDefault()): Long {
        if (!hasPermission() || networkStatsManager == null) return 0L
        val todayStartMs = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        if (nowMs <= todayStartMs) return 0L

        var totalBytes = 0L
        for (transport in listOf(ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_WIFI)) {
            try {
                val stats = networkStatsManager.queryDetailsForUid(
                    transport,
                    null,
                    todayStartMs,
                    nowMs,
                    NetworkStats.Bucket.UID_TETHERING
                )
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    totalBytes += bucket.rxBytes + bucket.txBytes
                }
                stats.close()
            } catch (_: Exception) {
            }
        }
        return totalBytes
    }
}

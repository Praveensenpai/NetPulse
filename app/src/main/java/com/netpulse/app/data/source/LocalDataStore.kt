package com.netpulse.app.data.source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.netpulse.app.core.Constants
import com.netpulse.app.domain.model.DailyUsage
import com.netpulse.app.domain.model.MeterPreferences
import com.netpulse.app.domain.model.UsageLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFS_DATASTORE_NAME)
val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.USAGE_DATASTORE_NAME)

/** Point-in-time per-package totals persisted across service restarts. */
data class AppUsageSnapshot(
    val atMs: Long,
    val usesBaseline: Boolean,
    val totals: Map<String, Long>
)

class LocalDataStore(private val context: Context) {

    private object PrefKeys {
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        val SHOW_STATUS_BAR_ICON = booleanPreferencesKey("show_status_bar_icon")
        val SHOW_UPLOAD_SPEED = booleanPreferencesKey("show_upload_speed")
        val SHOW_FLOATING_OVERLAY = booleanPreferencesKey("show_floating_overlay")
        val UNIT_IN_BITS = booleanPreferencesKey("unit_in_bits")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val PAUSE_WHEN_SCREEN_OFF = booleanPreferencesKey("pause_when_screen_off")
        val OVERLAY_ALPHA = floatPreferencesKey("overlay_alpha")
        val APP_USAGE_LOG = stringPreferencesKey("app_usage_log")
        val APP_USAGE_SNAPSHOT = stringPreferencesKey("app_usage_snapshot")
    }

    val meterPreferencesFlow: Flow<MeterPreferences> = context.preferencesDataStore.data.map { prefs ->
        MeterPreferences(
            isServiceEnabled = prefs[PrefKeys.SERVICE_ENABLED] ?: true,
            showNotification = prefs[PrefKeys.SHOW_NOTIFICATION] ?: true,
            showStatusBarIcon = prefs[PrefKeys.SHOW_STATUS_BAR_ICON] ?: true,
            showUploadSpeed = prefs[PrefKeys.SHOW_UPLOAD_SPEED] ?: true,
            showFloatingOverlay = prefs[PrefKeys.SHOW_FLOATING_OVERLAY] ?: false,
            unitInBits = prefs[PrefKeys.UNIT_IN_BITS] ?: false,
            startOnBoot = prefs[PrefKeys.START_ON_BOOT] ?: true,
            pauseWhenScreenOff = prefs[PrefKeys.PAUSE_WHEN_SCREEN_OFF] ?: true,
            overlayAlpha = prefs[PrefKeys.OVERLAY_ALPHA] ?: 0.85f
        )
    }

    suspend fun getMeterPreferences(): MeterPreferences = meterPreferencesFlow.first()

    suspend fun updateMeterPreferences(transform: (MeterPreferences) -> MeterPreferences) {
        val current = getMeterPreferences()
        val updated = transform(current)
        context.preferencesDataStore.edit { prefs ->
            prefs[PrefKeys.SERVICE_ENABLED] = updated.isServiceEnabled
            prefs[PrefKeys.SHOW_NOTIFICATION] = updated.showNotification
            prefs[PrefKeys.SHOW_STATUS_BAR_ICON] = updated.showStatusBarIcon
            prefs[PrefKeys.SHOW_UPLOAD_SPEED] = updated.showUploadSpeed
            prefs[PrefKeys.SHOW_FLOATING_OVERLAY] = updated.showFloatingOverlay
            prefs[PrefKeys.UNIT_IN_BITS] = updated.unitInBits
            prefs[PrefKeys.START_ON_BOOT] = updated.startOnBoot
            prefs[PrefKeys.PAUSE_WHEN_SCREEN_OFF] = updated.pauseWhenScreenOff
            prefs[PrefKeys.OVERLAY_ALPHA] = updated.overlayAlpha
        }
    }

    fun getTodayKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun observeDailyUsage(dateKey: String = getTodayKey()): Flow<DailyUsage> {
        val mobileKey = longPreferencesKey("usage_${dateKey}_mobile")
        val wifiKey = longPreferencesKey("usage_${dateKey}_wifi")
        return context.usageDataStore.data.map { prefs ->
            DailyUsage(
                dateKey = dateKey,
                mobileBytes = prefs[mobileKey] ?: 0L,
                wifiBytes = prefs[wifiKey] ?: 0L
            )
        }
    }

    suspend fun addUsage(dateKey: String = getTodayKey(), mobileDelta: Long, wifiDelta: Long) {
        if (mobileDelta <= 0L && wifiDelta <= 0L) return
        val mobileKey = longPreferencesKey("usage_${dateKey}_mobile")
        val wifiKey = longPreferencesKey("usage_${dateKey}_wifi")
        context.usageDataStore.edit { prefs ->
            val curMobile = prefs[mobileKey] ?: 0L
            val curWifi = prefs[wifiKey] ?: 0L
            prefs[mobileKey] = curMobile + mobileDelta
            prefs[wifiKey] = curWifi + wifiDelta
        }
    }

    suspend fun resetUsage(dateKey: String = getTodayKey()) {
        val mobileKey = longPreferencesKey("usage_${dateKey}_mobile")
        val wifiKey = longPreferencesKey("usage_${dateKey}_wifi")
        context.usageDataStore.edit { prefs ->
            prefs[mobileKey] = 0L
            prefs[wifiKey] = 0L
        }
    }

    fun observeAppUsageLog(): Flow<List<UsageLogEntry>> =
        context.usageDataStore.data.map { prefs ->
            decodeUsageLog(prefs[PrefKeys.APP_USAGE_LOG] ?: "")
        }

    suspend fun saveAppUsageLog(entries: List<UsageLogEntry>) {
        val encoded = encodeUsageLog(entries)
        context.usageDataStore.edit { prefs ->
            prefs[PrefKeys.APP_USAGE_LOG] = encoded
        }
    }

    /**
     * Persists the log and the totals snapshot in a single DataStore edit.
     *
     * The two must land together: the snapshot is the baseline the next process
     * subtracts from, so a snapshot older than the log would make the recovery
     * delta include bytes already recorded in the log. One edit is atomic, so a
     * crash either keeps both or neither.
     */
    suspend fun saveAppUsageState(entries: List<UsageLogEntry>, snapshot: AppUsageSnapshot) {
        val encodedLog = encodeUsageLog(entries)
        val encodedSnapshot = encodeSnapshot(snapshot)
        context.usageDataStore.edit { prefs ->
            prefs[PrefKeys.APP_USAGE_LOG] = encodedLog
            prefs[PrefKeys.APP_USAGE_SNAPSHOT] = encodedSnapshot
        }
    }

    /** Totals seen by a previous process, plus the mode that produced them. */
    suspend fun saveAppUsageSnapshot(snapshot: AppUsageSnapshot) {
        context.usageDataStore.edit { prefs ->
            prefs[PrefKeys.APP_USAGE_SNAPSHOT] = encodeSnapshot(snapshot)
        }
    }

    private fun encodeSnapshot(snapshot: AppUsageSnapshot): String = buildString {
        append(snapshot.atMs).append('|').append(if (snapshot.usesBaseline) 1 else 0)
        for ((pkg, bytes) in snapshot.totals) append('\n').append(pkg).append('|').append(bytes)
    }

    suspend fun loadAppUsageSnapshot(): AppUsageSnapshot? {
        val raw = context.usageDataStore.data.map { it[PrefKeys.APP_USAGE_SNAPSHOT] ?: "" }.first()
        if (raw.isEmpty()) return null
        val lines = raw.lineSequence().toList()
        val header = lines.firstOrNull()?.split('|') ?: return null
        if (header.size != 2) return null
        val atMs = header[0].toLongOrNull() ?: return null
        val usesBaseline = header[1] == "1"
        val totals = HashMap<String, Long>()
        for (line in lines.drop(1)) {
            val sep = line.lastIndexOf('|')
            if (sep <= 0) continue
            val bytes = line.substring(sep + 1).toLongOrNull() ?: continue
            totals[line.substring(0, sep)] = bytes
        }
        return AppUsageSnapshot(atMs, usesBaseline, totals)
    }

    private fun encodeUsageLog(entries: List<UsageLogEntry>): String =
        entries.joinToString(separator = "\n") { entry ->
            listOf(
                entry.minuteMs,
                entry.packageName,
                entry.label.replace('|', ' '),
                entry.bytes
            ).joinToString(separator = "|")
        }

    private fun decodeUsageLog(raw: String): List<UsageLogEntry> {
        if (raw.isEmpty()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 4) return@mapNotNull null
            val minuteMs = parts[0].toLongOrNull() ?: return@mapNotNull null
            val bytes = parts[3].toLongOrNull() ?: return@mapNotNull null
            UsageLogEntry(
                minuteMs = minuteMs,
                packageName = parts[1],
                label = parts[2],
                bytes = bytes
            )
        }.toList()
    }
}

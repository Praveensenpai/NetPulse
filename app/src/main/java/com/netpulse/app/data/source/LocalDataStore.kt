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
import com.netpulse.app.domain.model.UsageHistory
import com.netpulse.app.domain.model.UsageRange

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFS_DATASTORE_NAME)
val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.USAGE_DATASTORE_NAME)

class LocalDataStore(private val context: Context) {

    /** Day key whose retention sweep already ran, so the sweep runs once per day. */
    private var lastPrunedDayKey: String? = null

    private object PrefKeys {
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        val SHOW_STATUS_BAR_ICON = booleanPreferencesKey("show_status_bar_icon")
        val SHOW_UPLOAD_SPEED = booleanPreferencesKey("show_upload_speed")
        val SHOW_FLOATING_OVERLAY = booleanPreferencesKey("show_floating_overlay")
        val UNIT_IN_BITS = booleanPreferencesKey("unit_in_bits")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val PAUSE_WHEN_SCREEN_OFF = booleanPreferencesKey("pause_when_screen_off")
        val MONITOR_WHEN_TETHERING = booleanPreferencesKey("monitor_when_tethering")
        val OVERLAY_ALPHA = floatPreferencesKey("overlay_alpha")

        /** Monotonic all-time totals; pruning never removes these. */
        val LIFETIME_MOBILE = longPreferencesKey("lifetime_mobile")
        val LIFETIME_WIFI = longPreferencesKey("lifetime_wifi")
        val LIFETIME_HOTSPOT = longPreferencesKey("lifetime_hotspot")
    }

    val meterPreferencesFlow: Flow<MeterPreferences> = context.preferencesDataStore.data.map { prefs ->
        meterPreferencesOf(prefs)
    }

    suspend fun getMeterPreferences(): MeterPreferences = meterPreferencesFlow.first()

    /**
     * Applies [transform] to the stored preferences inside one transaction.
     *
     * Reading the current value first and writing it back afterwards would let
     * two concurrent setters both start from the same snapshot, so the second
     * write would silently drop the first one's change. Building the snapshot
     * inside [edit] keeps the read and the write atomic.
     */
    suspend fun updateMeterPreferences(transform: (MeterPreferences) -> MeterPreferences) {
        context.preferencesDataStore.edit { prefs ->
            val updated = transform(meterPreferencesOf(prefs))
            prefs[PrefKeys.SERVICE_ENABLED] = updated.isServiceEnabled
            prefs[PrefKeys.SHOW_NOTIFICATION] = updated.showNotification
            prefs[PrefKeys.SHOW_STATUS_BAR_ICON] = updated.showStatusBarIcon
            prefs[PrefKeys.SHOW_UPLOAD_SPEED] = updated.showUploadSpeed
            prefs[PrefKeys.SHOW_FLOATING_OVERLAY] = updated.showFloatingOverlay
            prefs[PrefKeys.UNIT_IN_BITS] = updated.unitInBits
            prefs[PrefKeys.START_ON_BOOT] = updated.startOnBoot
            prefs[PrefKeys.PAUSE_WHEN_SCREEN_OFF] = updated.pauseWhenScreenOff
            prefs[PrefKeys.MONITOR_WHEN_TETHERING] = updated.monitorWhenTethering
            prefs[PrefKeys.OVERLAY_ALPHA] = updated.overlayAlpha
        }
    }

    private fun meterPreferencesOf(prefs: Preferences): MeterPreferences = MeterPreferences(
        isServiceEnabled = prefs[PrefKeys.SERVICE_ENABLED] ?: true,
        showNotification = prefs[PrefKeys.SHOW_NOTIFICATION] ?: true,
        showStatusBarIcon = prefs[PrefKeys.SHOW_STATUS_BAR_ICON] ?: true,
        showUploadSpeed = prefs[PrefKeys.SHOW_UPLOAD_SPEED] ?: true,
        showFloatingOverlay = prefs[PrefKeys.SHOW_FLOATING_OVERLAY] ?: false,
        unitInBits = prefs[PrefKeys.UNIT_IN_BITS] ?: false,
        startOnBoot = prefs[PrefKeys.START_ON_BOOT] ?: true,
        pauseWhenScreenOff = prefs[PrefKeys.PAUSE_WHEN_SCREEN_OFF] ?: true,
        monitorWhenTethering = prefs[PrefKeys.MONITOR_WHEN_TETHERING] ?: true,
        overlayAlpha = prefs[PrefKeys.OVERLAY_ALPHA] ?: 0.85f
    )

    fun getTodayKey(): String = DayKeys.keyFor(System.currentTimeMillis(), ZoneId.systemDefault())

    /**
     * Today's usage, following the calendar as it advances.
     *
     * The day key is re-read on a ticker rather than resolved once, because a
     * key captured at subscription time keeps the collector on the day the
     * process started: writes move to the new day's key while the UI shows
     * yesterday's total until the app restarts.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDailyUsage(): Flow<DailyUsage> =
        DayKeys.dayKeyTicker(
            clock = System::currentTimeMillis,
            zoneProvider = ZoneId::systemDefault
        ).flatMapLatest { dateKey -> usageFor(dateKey) }

    private fun usageFor(dateKey: String): Flow<DailyUsage> {
        val mobileKey = longPreferencesKey("usage_${dateKey}_mobile")
        val wifiKey = longPreferencesKey("usage_${dateKey}_wifi")
        val hotspotKey = longPreferencesKey("usage_${dateKey}_hotspot")
        return context.usageDataStore.data.map { prefs ->
            DailyUsage(
                dateKey = dateKey,
                mobileBytes = prefs[mobileKey] ?: 0L,
                wifiBytes = prefs[wifiKey] ?: 0L,
                hotspotBytes = prefs[hotspotKey] ?: 0L
            )
        }
    }

    /**
     * Usage over today, the trailing week, the trailing month, and all time.
     *
     * Re-emits whenever any retained bucket or lifetime counter changes, and
     * re-keys itself when the calendar rolls over so "today" cannot go stale.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUsageHistory(): Flow<UsageHistory> =
        DayKeys.dayKeyTicker(
            clock = System::currentTimeMillis,
            zoneProvider = ZoneId::systemDefault
        ).flatMapLatest { dateKey ->
            context.usageDataStore.data.map { prefs ->
                UsageAggregator.aggregate(
                    days = readDays(prefs),
                    todayKey = dateKey,
                    lifetime = lifetimeOf(prefs)
                )
            }
        }

    private fun readDays(prefs: Preferences): List<DailyUsage> =
        prefs.asMap().keys
            .mapNotNull { key ->
                val date = DayKeys.dateOfUsageKey(key.name) ?: return@mapNotNull null
                date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            }
            .distinct()
            .map { dateKey ->
                DailyUsage(
                    dateKey = dateKey,
                    mobileBytes = prefs[longPreferencesKey("usage_${dateKey}_mobile")] ?: 0L,
                    wifiBytes = prefs[longPreferencesKey("usage_${dateKey}_wifi")] ?: 0L,
                    hotspotBytes = prefs[longPreferencesKey("usage_${dateKey}_hotspot")] ?: 0L
                )
            }

    private fun lifetimeOf(prefs: Preferences): UsageRange = UsageRange(
        mobileBytes = prefs[PrefKeys.LIFETIME_MOBILE] ?: 0L,
        wifiBytes = prefs[PrefKeys.LIFETIME_WIFI] ?: 0L,
        hotspotBytes = prefs[PrefKeys.LIFETIME_HOTSPOT] ?: 0L
    )

    suspend fun addUsage(
        dateKey: String = getTodayKey(),
        mobileDelta: Long,
        wifiDelta: Long,
        hotspotDelta: Long = 0L
    ) {
        if (mobileDelta <= 0L && wifiDelta <= 0L && hotspotDelta <= 0L) return
        val mobileKey = longPreferencesKey("usage_${dateKey}_mobile")
        val wifiKey = longPreferencesKey("usage_${dateKey}_wifi")
        val hotspotKey = longPreferencesKey("usage_${dateKey}_hotspot")
        context.usageDataStore.edit { prefs ->
            val curMobile = prefs[mobileKey] ?: 0L
            val curWifi = prefs[wifiKey] ?: 0L
            val curHotspot = prefs[hotspotKey] ?: 0L
            prefs[mobileKey] = curMobile + mobileDelta
            prefs[wifiKey] = curWifi + wifiDelta
            prefs[hotspotKey] = curHotspot + hotspotDelta
            // Lifetime counters are separate from the day buckets because
            // pruning deletes old days; all-time has to survive that.
            prefs[PrefKeys.LIFETIME_MOBILE] = (prefs[PrefKeys.LIFETIME_MOBILE] ?: 0L) + mobileDelta
            prefs[PrefKeys.LIFETIME_WIFI] = (prefs[PrefKeys.LIFETIME_WIFI] ?: 0L) + wifiDelta
            prefs[PrefKeys.LIFETIME_HOTSPOT] = (prefs[PrefKeys.LIFETIME_HOTSPOT] ?: 0L) + hotspotDelta
        }
        pruneOldUsage(dateKey)
    }

    /**
     * Drops usage buckets older than [DayKeys.RETENTION_DAYS].
     *
     * A pair of keys is written per day and nothing reads a past day, so
     * without this the preferences file grows forever. That file is rewritten
     * in full on every [addUsage], which runs on the 1 Hz service loop, so the
     * cost of dead days is paid continuously rather than once.
     *
     * Runs at most once per day: the retention window only changes at
     * midnight, so re-scanning on every tick would be pure overhead.
     */
    private suspend fun pruneOldUsage(todayKey: String) {
        if (lastPrunedDayKey == todayKey) return
        context.usageDataStore.edit { prefs ->
            val stale = DayKeys.keysToPrune(prefs.asMap().keys.map { it.name }, todayKey)
            // Usage values are always Long, so removing through the same key
            // factory keeps this type-safe without casting Preferences.Key<*>.
            stale.forEach { prefs.remove(longPreferencesKey(it)) }
        }
        lastPrunedDayKey = todayKey
    }

    /**
     * Zeroes one day's bucket and removes the same bytes from the lifetime
     * counters.
     *
     * Without the counter adjustment a reset would leave Today at 0 while All
     * Time kept the discarded bytes, so the same screen would report a total
     * that includes usage it just claimed to delete. The arithmetic lives in
     * [UsageAggregator.reducedLifetime] so it is unit-testable without an
     * Android context.
     */
    suspend fun resetUsage(dateKey: String = getTodayKey()) {
        val mobileKey = longPreferencesKey("usage_${dateKey}_mobile")
        val wifiKey = longPreferencesKey("usage_${dateKey}_wifi")
        val hotspotKey = longPreferencesKey("usage_${dateKey}_hotspot")
        context.usageDataStore.edit { prefs ->
            val removed = UsageRange(
                mobileBytes = prefs[mobileKey] ?: 0L,
                wifiBytes = prefs[wifiKey] ?: 0L,
                hotspotBytes = prefs[hotspotKey] ?: 0L
            )
            prefs[mobileKey] = 0L
            prefs[wifiKey] = 0L
            prefs[hotspotKey] = 0L
            val reduced = UsageAggregator.reducedLifetime(lifetimeOf(prefs), removed)
            prefs[PrefKeys.LIFETIME_MOBILE] = reduced.mobileBytes
            prefs[PrefKeys.LIFETIME_WIFI] = reduced.wifiBytes
            prefs[PrefKeys.LIFETIME_HOTSPOT] = reduced.hotspotBytes
        }
    }

    suspend fun syncHotspotUsage(
        accurateHotspotBytes: Long,
        dateKey: String = getTodayKey()
    ) {
        if (accurateHotspotBytes <= 0L) return
        val mobileKey = longPreferencesKey("usage_${dateKey}_mobile")
        val hotspotKey = longPreferencesKey("usage_${dateKey}_hotspot")
        context.usageDataStore.edit { prefs ->
            val curHotspot = prefs[hotspotKey] ?: 0L
            if (accurateHotspotBytes <= curHotspot) return@edit
            val diff = accurateHotspotBytes - curHotspot
            val curMobile = prefs[mobileKey] ?: 0L
            // Reconcile offloaded tethering bytes: transfer diff from mobile to hotspot
            prefs[mobileKey] = (curMobile - diff).coerceAtLeast(0L)
            prefs[hotspotKey] = accurateHotspotBytes

            val curLifetimeHotspot = prefs[PrefKeys.LIFETIME_HOTSPOT] ?: 0L
            val curLifetimeMobile = prefs[PrefKeys.LIFETIME_MOBILE] ?: 0L
            prefs[PrefKeys.LIFETIME_HOTSPOT] = curLifetimeHotspot + diff
            prefs[PrefKeys.LIFETIME_MOBILE] = (curLifetimeMobile - diff).coerceAtLeast(0L)
        }
    }
}

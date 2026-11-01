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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.ZoneId

val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFS_DATASTORE_NAME)
val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.USAGE_DATASTORE_NAME)

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
            zone = ZoneId.systemDefault()
        ).flatMapLatest { dateKey -> usageFor(dateKey) }

    private fun usageFor(dateKey: String): Flow<DailyUsage> {
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

}

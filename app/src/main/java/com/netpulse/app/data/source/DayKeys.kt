package com.netpulse.app.data.source

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Day-bucket key arithmetic, kept free of Android types so it is unit-testable.
 *
 * The usage ledger is keyed by local calendar date, so the key has to be
 * recomputed as the clock advances. Capturing it once at subscription time
 * freezes the read side on the day the app started: writes move to the new
 * day's key while the UI keeps observing the old one until a restart.
 */
internal object DayKeys {

    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * How long the ticker sleeps before re-reading the clock.
     *
     * Sleeping exactly until midnight would be wrong if the wall clock is
     * changed or the device sleeps through the alarm, so the ticker re-checks
     * on a bounded interval and only emits when the key actually changes.
     */
    const val RECHECK_MS = 30_000L

    /** Stable `yyyy-MM-dd` key for [nowMs] in [zone]. */
    fun keyFor(nowMs: Long, zone: ZoneId): String =
        localDateOf(nowMs, zone).format(FORMATTER)

    /**
     * Epoch millis of the first instant of the next local day after [nowMs].
     *
     * Uses the zone's own rules, so a DST transition or a month/year boundary
     * lands on the real start of the day rather than a fixed 24-hour offset.
     */
    fun nextMidnightMs(nowMs: Long, zone: ZoneId): Long =
        localDateOf(nowMs, zone)
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    /**
     * Emits the current day key immediately, then again whenever the key
     * changes. Duplicate keys are dropped so downstream collection is not
     * restarted on every tick.
     */
    fun dayKeyTicker(
        clock: () -> Long,
        zone: ZoneId,
        recheckMs: Long = RECHECK_MS
    ): Flow<String> = flow {
        while (true) {
            emit(keyFor(clock(), zone))
            delay(recheckMs)
        }
    }.distinctUntilChanged()

    /**
     * Local date for an epoch instant.
     *
     * `Instant.atZone` rather than `LocalDate.ofInstant`: the latter is API 34
     * and this app supports API 33.
     */
    private fun localDateOf(nowMs: Long, zone: ZoneId) =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
}

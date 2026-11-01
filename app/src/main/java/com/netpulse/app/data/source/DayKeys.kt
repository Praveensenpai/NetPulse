package com.netpulse.app.data.source

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
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
     * How many day buckets to keep, counting today.
     *
     * The UI shows today, the trailing week, and the trailing month, so the
     * ledger has to reach back at least 30 days. A few extra days leave room for
     * a device that was offline across a boundary. All-time totals do not depend
     * on this window: they come from lifetime counters that pruning never
     * touches.
     *
     * This stays a bound rather than unbounded storage because the preferences
     * file is rewritten in full on every write, and writes happen on the 1 Hz
     * service loop.
     */
    const val RETENTION_DAYS = 35

    private val USAGE_KEY = Regex("""^usage_(\d{4}-\d{2}-\d{2})_(?:mobile|wifi)$""")

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
     * Usage keys that fall outside the [retentionDays] window ending at
     * [todayKey].
     *
     * Keys that are not usage keys, or whose date cannot be parsed, are never
     * returned: pruning must not be able to delete a preference it does not
     * understand. A key dated in the future (the clock was moved backwards) is
     * also kept, since discarding it would destroy data on a transient skew.
     */
    fun keysToPrune(
        keys: Collection<String>,
        todayKey: String,
        retentionDays: Int = RETENTION_DAYS
    ): Set<String> {
        if (retentionDays < 1) return emptySet()
        val today = parseKeyDate(todayKey) ?: return emptySet()
        val oldestKept = today.minusDays(retentionDays.toLong() - 1)

        return keys.filter { key ->
            val day = usageKeyDate(key) ?: return@filter false
            day.isBefore(oldestKept)
        }.toSet()
    }

    private fun usageKeyDate(key: String): LocalDate? =
        USAGE_KEY.find(key)?.groupValues?.get(1)?.let(::parseKeyDate)

    /** Calendar date for a `yyyy-MM-dd` bucket key, or null when malformed. */
    fun parseKey(key: String): LocalDate? = parseKeyDate(key)

    /** Calendar date encoded in a `usage_<date>_<transport>` preference name. */
    fun dateOfUsageKey(key: String): LocalDate? = usageKeyDate(key)

    private fun parseKeyDate(key: String): LocalDate? =
        runCatching { LocalDate.parse(key, FORMATTER) }.getOrNull()

    /**
     * Local date for an epoch instant.
     *
     * `Instant.atZone` rather than `LocalDate.ofInstant`: the latter is API 34
     * and this app supports API 33.
     */
    private fun localDateOf(nowMs: Long, zone: ZoneId) =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
}

package com.netpulse.app.data.source

import com.netpulse.app.domain.model.DailyUsage
import com.netpulse.app.domain.model.UsageHistory
import com.netpulse.app.domain.model.UsageRange
import java.time.temporal.ChronoUnit

/**
 * Rolls the retained per-day ledger up into the windows the UI shows.
 *
 * Kept pure and free of Android types so the boundary arithmetic is testable:
 * "this week" means the trailing seven calendar days including today, not the
 * ISO week, which would drop days the user still considers current.
 */
internal object UsageAggregator {

    const val WEEK_DAYS = 7
    const val MONTH_DAYS = 30

    /**
     * @param days retained buckets, any order
     * @param todayKey local `yyyy-MM-dd` for right now
     * @param lifetime monotonic totals that outlive pruning
     */
    fun aggregate(
        days: Collection<DailyUsage>,
        todayKey: String,
        lifetime: UsageRange
    ): UsageHistory {
        val sorted = days.sortedBy { it.dateKey }
        val today = DayKeys.parseKey(todayKey)
            ?: return UsageHistory(allTime = lifetime, days = sorted)

        var todayRange = UsageRange.ZERO
        var withinWeek = UsageRange.ZERO
        var withinMonth = UsageRange.ZERO

        for (day in days) {
            val date = DayKeys.parseKey(day.dateKey) ?: continue
            // Future-dated buckets come from a clock that was moved backwards;
            // counting them would inflate every window.
            if (date.isAfter(today)) continue

            val range = UsageRange(day.mobileBytes, day.wifiBytes)
            when (ChronoUnit.DAYS.between(date, today)) {
                0L -> todayRange += range
                in 1L until WEEK_DAYS.toLong() -> withinWeek += range
                in WEEK_DAYS.toLong() until MONTH_DAYS.toLong() -> withinMonth += range
            }
        }

        val weekTotal = withinWeek + todayRange
        return UsageHistory(
            today = todayRange,
            last7Days = weekTotal,
            last30Days = withinMonth + weekTotal,
            // Counters are the truth once they have been running; before that,
            // the retained days are the only record of earlier usage. Taking the
            // larger keeps all-time from stepping backwards on upgrade, without
            // writing a migration row into the preferences file.
            allTime = lifetime.atLeast(seedLifetime(days)),
            days = sorted
        )
    }

    /** Sum of every retained bucket. */
    fun seedLifetime(days: Collection<DailyUsage>): UsageRange =
        days.fold(UsageRange.ZERO) { acc, day ->
            acc + UsageRange(day.mobileBytes, day.wifiBytes)
        }

    /**
     * Lifetime totals after [removed] bytes are discarded from the ledger.
     *
     * A reset has to subtract from the counters as well as the day bucket, or
     * All Time keeps reporting usage the same screen just deleted. Clamped at
     * zero because a bucket written before the counters existed can be larger
     * than the counter value.
     */
    fun reducedLifetime(lifetime: UsageRange, removed: UsageRange): UsageRange = UsageRange(
        mobileBytes = (lifetime.mobileBytes - removed.mobileBytes).coerceAtLeast(0L),
        wifiBytes = (lifetime.wifiBytes - removed.wifiBytes).coerceAtLeast(0L)
    )
}

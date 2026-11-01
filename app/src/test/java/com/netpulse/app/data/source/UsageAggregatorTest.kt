package com.netpulse.app.data.source

import com.google.common.truth.Truth.assertThat
import com.netpulse.app.domain.model.DailyUsage
import com.netpulse.app.domain.model.UsageRange
import org.junit.Test

class UsageAggregatorTest {

    private fun day(key: String, mobile: Long = 0L, wifi: Long = 0L) =
        DailyUsage(dateKey = key, mobileBytes = mobile, wifiBytes = wifi)

    private val today = "2026-11-01"

    @Test
    fun `today counts only the current day`() {
        val result = UsageAggregator.aggregate(
            days = listOf(day(today, 10, 20), day("2026-10-31", 100, 200)),
            todayKey = today,
            lifetime = UsageRange(110, 220)
        )
        assertThat(result.today).isEqualTo(UsageRange(10, 20))
    }

    @Test
    fun `week is the trailing seven days including today`() {
        val days = listOf(
            day(today, 1, 1),
            day("2026-10-31", 1, 1),
            day("2026-10-26", 1, 1), // 6 days back, inside
            day("2026-10-25", 5, 5) // 7 days back, outside
        )
        val result = UsageAggregator.aggregate(days, today, UsageRange.ZERO)
        assertThat(result.last7Days).isEqualTo(UsageRange(3, 3))
    }

    @Test
    fun `month is the trailing thirty days including today`() {
        val days = listOf(
            day(today, 1, 0),
            day("2026-10-20", 1, 0),
            day("2026-10-03", 1, 0), // 29 days back, inside
            day("2026-10-02", 9, 9) // 30 days back, outside
        )
        val result = UsageAggregator.aggregate(days, today, UsageRange.ZERO)
        assertThat(result.last30Days).isEqualTo(UsageRange(3, 0))
    }

    @Test
    fun `windows nest so each larger one contains the smaller`() {
        val days = listOf(
            day(today, 2, 0),
            day("2026-10-29", 3, 0),
            day("2026-10-10", 4, 0)
        )
        val result = UsageAggregator.aggregate(days, today, UsageRange(9, 0))
        assertThat(result.today.totalBytes).isEqualTo(2)
        assertThat(result.last7Days.totalBytes).isEqualTo(5)
        assertThat(result.last30Days.totalBytes).isEqualTo(9)
    }

    @Test
    fun `future dated buckets are ignored`() {
        val result = UsageAggregator.aggregate(
            days = listOf(day(today, 1, 1), day("2026-12-01", 99, 99)),
            todayKey = today,
            lifetime = UsageRange.ZERO
        )
        assertThat(result.today).isEqualTo(UsageRange(1, 1))
        assertThat(result.last30Days).isEqualTo(UsageRange(1, 1))
    }

    @Test
    fun `all time takes the larger of counters and retained days`() {
        val days = listOf(day(today, 10, 10))

        // Fresh counters: retained days are the only evidence of older usage.
        val upgraded = UsageAggregator.aggregate(days, today, UsageRange(0, 0))
        assertThat(upgraded.allTime).isEqualTo(UsageRange(10, 10))

        // Counters have run long enough to exceed the retained window.
        val running = UsageAggregator.aggregate(days, today, UsageRange(500, 600))
        assertThat(running.allTime).isEqualTo(UsageRange(500, 600))
    }

    @Test
    fun `malformed keys are skipped rather than counted`() {
        val result = UsageAggregator.aggregate(
            days = listOf(day(today, 1, 0), day("not-a-date", 50, 50)),
            todayKey = today,
            lifetime = UsageRange(1, 0)
        )
        assertThat(result.today).isEqualTo(UsageRange(1, 0))
    }

    @Test
    fun `days are returned oldest first`() {
        val result = UsageAggregator.aggregate(
            days = listOf(day("2026-10-31"), day(today), day("2026-10-15")),
            todayKey = today,
            lifetime = UsageRange.ZERO
        )
        assertThat(result.days.map { it.dateKey })
            .containsExactly("2026-10-15", "2026-10-31", today)
            .inOrder()
    }

    @Test
    fun `unparseable today key falls back to lifetime only`() {
        val result = UsageAggregator.aggregate(
            days = listOf(day(today, 7, 7)),
            todayKey = "garbage",
            lifetime = UsageRange(3, 4)
        )
        assertThat(result.today).isEqualTo(UsageRange.ZERO)
        assertThat(result.allTime).isEqualTo(UsageRange(3, 4))
    }
}

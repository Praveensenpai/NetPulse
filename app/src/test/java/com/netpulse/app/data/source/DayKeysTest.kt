package com.netpulse.app.data.source

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DayKeysTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `keyFor formats the local calendar date`() {
        val at = millisOf(2026, 11, 1, 23, 55)
        assertThat(DayKeys.keyFor(at, zone)).isEqualTo("2026-11-01")
    }

    @Test
    fun `keyFor rolls over at local midnight, not at UTC midnight`() {
        val before = millisOf(2026, 11, 1, 23, 59)
        val after = millisOf(2026, 11, 2, 0, 0)
        assertThat(DayKeys.keyFor(before, zone)).isEqualTo("2026-11-01")
        assertThat(DayKeys.keyFor(after, zone)).isEqualTo("2026-11-02")
    }

    @Test
    fun `keyFor handles month and year boundaries`() {
        assertThat(DayKeys.keyFor(millisOf(2026, 12, 31, 23, 59), zone))
            .isEqualTo("2026-12-31")
        assertThat(DayKeys.keyFor(millisOf(2027, 1, 1, 0, 1), zone))
            .isEqualTo("2027-01-01")
    }

    @Test
    fun `nextMidnightMs points at the first instant of the next day`() {
        val now = millisOf(2026, 11, 1, 23, 55)
        val expected = millisOf(2026, 11, 2, 0, 0)
        assertThat(DayKeys.nextMidnightMs(now, zone)).isEqualTo(expected)
    }

    @Test
    fun `nextMidnightMs is correct mid-day`() {
        val now = millisOf(2026, 11, 1, 12, 0)
        assertThat(DayKeys.nextMidnightMs(now, zone))
            .isEqualTo(millisOf(2026, 11, 2, 0, 0))
    }

    @Test
    fun `ticker emits the current key first`() = runTest {
        val fixed = millisOf(2026, 11, 1, 12, 0)
        val seen = withTimeout(1_000) {
            DayKeys.dayKeyTicker(clock = { fixed }, zone = zone, recheckMs = 10_000)
                .take(1)
                .toList()
        }
        assertThat(seen).containsExactly("2026-11-01")
    }

    @Test
    fun `ticker emits the new key once the clock crosses midnight`() = runTest {
        val before = millisOf(2026, 11, 1, 23, 59)
        val after = millisOf(2026, 11, 2, 0, 0)

        // The ticker reads the clock once per tick, so the first read is the old
        // day and every later read is the new one.
        val reads = java.util.concurrent.atomic.AtomicInteger(0)
        val clock = { if (reads.getAndIncrement() == 0) before else after }

        val seen = withTimeout(5_000) {
            DayKeys.dayKeyTicker(clock = clock, zone = zone, recheckMs = 1)
                .take(2)
                .toList()
        }

        assertThat(seen).containsExactly("2026-11-01", "2026-11-02").inOrder()
    }

    @Test
    fun `ticker does not re-emit the same key`() = runTest {
        val fixed = millisOf(2026, 11, 1, 12, 0)
        val seen = mutableListOf<String>()

        val job = launch {
            DayKeys.dayKeyTicker(clock = { fixed }, zone = zone, recheckMs = 1)
                .collect { seen += it }
        }
        withTimeout(2_000) { while (seen.isEmpty()) kotlinx.coroutines.delay(1) }
        job.cancel()

        assertThat(seen).containsExactly("2026-11-01")
    }

    @Test
    fun `keysToPrune keeps the whole 35 day window`() {
        // 2026-11-01 minus 34 days is 2026-09-28, the oldest retained day.
        val keys = setOf(
            "usage_2026-11-01_mobile",
            "usage_2026-10-15_wifi",
            "usage_2026-09-28_mobile",
            "usage_2026-09-27_wifi"
        )
        assertThat(DayKeys.keysToPrune(keys, "2026-11-01"))
            .containsExactly("usage_2026-09-27_wifi")
    }

    @Test
    fun `keysToPrune drops buckets older than the window`() {
        val keys = setOf(
            "usage_2026-11-01_wifi",
            "usage_2026-10-30_wifi",
            "usage_2026-10-29_mobile",
            "usage_2026-10-29_wifi",
            "usage_2026-10-25_mobile"
        )
        assertThat(DayKeys.keysToPrune(keys, "2026-11-01", retentionDays = 3))
            .containsExactly(
                "usage_2026-10-29_mobile",
                "usage_2026-10-29_wifi",
                "usage_2026-10-25_mobile"
            )
    }

    @Test
    fun `keysToPrune never touches non-usage preferences`() {
        val keys = setOf(
            "service_enabled",
            "show_notification",
            "overlay_alpha",
            "lifetime_mobile",
            "usage_2026-08-01_wifi"
        )
        assertThat(DayKeys.keysToPrune(keys, "2026-11-01"))
            .containsExactly("usage_2026-08-01_wifi")
    }

    @Test
    fun `keysToPrune keeps future-dated keys`() {
        val keys = setOf(
            "usage_2026-11-05_wifi",
            "usage_2026-08-01_wifi"
        )
        assertThat(DayKeys.keysToPrune(keys, "2026-11-01"))
            .containsExactly("usage_2026-08-01_wifi")
    }

    @Test
    fun `keysToPrune ignores unparseable dates and malformed names`() {
        val keys = setOf(
            "usage_2026-13-45_wifi",
            "usage_notadate_mobile",
            "usage_2026-10-01_cellular"
        )
        assertThat(DayKeys.keysToPrune(keys, "2026-11-01")).isEmpty()
    }

    @Test
    fun `keysToPrune crosses a month boundary correctly`() {
        val keys = setOf(
            "usage_2026-11-01_wifi",
            "usage_2026-10-31_wifi",
            "usage_2026-10-30_wifi"
        )
        assertThat(DayKeys.keysToPrune(keys, "2026-11-01", retentionDays = 2))
            .containsExactly("usage_2026-10-30_wifi")
    }
}

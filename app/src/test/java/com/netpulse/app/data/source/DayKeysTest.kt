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
}

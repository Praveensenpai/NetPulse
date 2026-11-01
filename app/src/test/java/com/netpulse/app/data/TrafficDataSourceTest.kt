package com.netpulse.app.data

import com.google.common.truth.Truth.assertThat
import com.netpulse.app.data.source.TrafficDataSource
import com.netpulse.app.data.source.TrafficStatsProvider
import org.junit.Test

class FakeTrafficStatsProvider(
    var rx: Long = 0L,
    var tx: Long = 0L,
    var timeMs: Long = 1000L
) : TrafficStatsProvider {
    override fun getTotalRxBytes(): Long = rx
    override fun getTotalTxBytes(): Long = tx
    override fun getElapsedRealtimeMs(): Long = timeMs
}

class TrafficDataSourceTest {

    @Test
    fun sample_firstCall_returnsZeroSpeed() {
        val fake = FakeTrafficStatsProvider(rx = 1000L, tx = 500L, timeMs = 1000L)
        val dataSource = TrafficDataSource(fake)

        val first = dataSource.sample()
        assertThat(first.downloadBytesPerSec).isEqualTo(0L)
        assertThat(first.uploadBytesPerSec).isEqualTo(0L)
    }

    @Test
    fun sample_subsequentCall_computesAccurateRate() {
        val fake = FakeTrafficStatsProvider(rx = 1000L, tx = 500L, timeMs = 1000L)
        val dataSource = TrafficDataSource(fake)
        dataSource.sample()

        // Advance 1 second, with 1024 bytes Rx and 512 bytes Tx
        fake.rx = 2024L
        fake.tx = 1012L
        fake.timeMs = 2000L

        val second = dataSource.sample()
        assertThat(second.downloadBytesPerSec).isEqualTo(1024L)
        assertThat(second.uploadBytesPerSec).isEqualTo(512L)
    }

    @Test
    fun sample_afterDeviceReboot_neverProducesNegativeRate() {
        val fake = FakeTrafficStatsProvider(rx = 50000L, tx = 20000L, timeMs = 1000L)
        val dataSource = TrafficDataSource(fake)
        dataSource.sample()

        // Counters reset after reboot
        fake.rx = 100L
        fake.tx = 50L
        fake.timeMs = 2000L

        val afterReboot = dataSource.sample()
        assertThat(afterReboot.downloadBytesPerSec).isEqualTo(0L)
        assertThat(afterReboot.uploadBytesPerSec).isEqualTo(0L)
    }

    @Test
    fun consumeDelta_reportsExactBytesAndResets() {
        val fake = FakeTrafficStatsProvider(rx = 0L, tx = 0L, timeMs = 1000L)
        val dataSource = TrafficDataSource(fake)
        dataSource.sample()

        fake.rx = 4096L
        fake.tx = 1024L
        fake.timeMs = 2000L
        dataSource.sample()

        val delta = dataSource.consumeDelta()
        assertThat(delta.rxBytes).isEqualTo(4096L)
        assertThat(delta.txBytes).isEqualTo(1024L)

        val second = dataSource.consumeDelta()
        assertThat(second.totalBytes).isEqualTo(0L)
    }

    @Test
    fun peekDelta_reportsBytesWithoutClearingThem() {
        val fake = FakeTrafficStatsProvider(rx = 0L, tx = 0L, timeMs = 1000L)
        val dataSource = TrafficDataSource(fake)
        dataSource.sample()

        fake.rx = 8192L
        fake.tx = 4096L
        fake.timeMs = 2000L
        dataSource.sample()

        assertThat(dataSource.peekDelta().totalBytes).isEqualTo(12_288L)
        // A second peek must observe the same bytes: nothing was consumed.
        assertThat(dataSource.peekDelta().totalBytes).isEqualTo(12_288L)

        assertThat(dataSource.consumeDelta().totalBytes).isEqualTo(12_288L)
        assertThat(dataSource.peekDelta().totalBytes).isEqualTo(0L)
    }

    @Test
    fun sample_whilePeekingKeepsAccumulatingIntoOneDelta() {
        val fake = FakeTrafficStatsProvider(rx = 0L, tx = 0L, timeMs = 1000L)
        val dataSource = TrafficDataSource(fake)
        dataSource.sample()

        fake.rx = 1000L
        fake.timeMs = 2000L
        dataSource.sample()
        dataSource.peekDelta()

        fake.rx = 3000L
        fake.timeMs = 3000L
        dataSource.sample()

        // The first 1000 bytes were held, not lost, so the committed total is 3000.
        assertThat(dataSource.consumeDelta().rxBytes).isEqualTo(3000L)
    }

    @Test
    fun consumeDelta_afterSlowTick_reflectsRealBytesNotRate() {
        val fake = FakeTrafficStatsProvider(rx = 0L, tx = 0L, timeMs = 1000L)
        val dataSource = TrafficDataSource(fake)
        dataSource.sample()

        // 10 seconds elapsed, 100_000 bytes transferred: rate is 10 KB/s,
        // but the ledger must record the full 100_000 bytes.
        fake.rx = 100_000L
        fake.timeMs = 11_000L
        val snapshot = dataSource.sample()

        assertThat(snapshot.downloadBytesPerSec).isEqualTo(10_000L)
        assertThat(dataSource.consumeDelta().rxBytes).isEqualTo(100_000L)
    }

    @Test
    fun catchUp_keepsBytesTransferredWhilePollingWasStopped() {
        val fake = FakeTrafficStatsProvider(rx = 1000L, tx = 500L, timeMs = 1000L)
        val dataSource = TrafficDataSource(fake)
        dataSource.sample()

        // Polling paused here. 20 MB moves before the loop resumes.
        fake.rx = 21_000_000L
        fake.tx = 500L
        fake.timeMs = 60_000L

        dataSource.catchUp()

        // The gap must be attributed, not discarded like reset() did.
        assertThat(dataSource.peekDelta().rxBytes).isEqualTo(20_999_000L)
    }

    @Test
    fun catchUp_doesNotReportTheGapAsASpeedSpike() {
        val fake = FakeTrafficStatsProvider(rx = 0L, tx = 0L, timeMs = 1000L)
        val dataSource = TrafficDataSource(fake)
        dataSource.sample()

        fake.rx = 20_000_000L
        fake.timeMs = 60_000L
        dataSource.catchUp()

        // The next tick sees only its own interval, so the overlay does not
        // flash 20 MB/s of catch-up traffic that already finished.
        fake.rx = 20_000_100L
        fake.timeMs = 61_000L
        val snapshot = dataSource.sample()
        assertThat(snapshot.downloadBytesPerSec).isEqualTo(100L)
    }

    @Test
    fun catchUp_afterDeviceReboot_neverProducesNegativeDelta() {
        val fake = FakeTrafficStatsProvider(rx = 50_000L, tx = 20_000L, timeMs = 1000L)
        val dataSource = TrafficDataSource(fake)
        dataSource.sample()

        fake.rx = 100L
        fake.tx = 50L
        fake.timeMs = 2000L
        dataSource.catchUp()

        assertThat(dataSource.peekDelta().totalBytes).isEqualTo(0L)
    }

    @Test
    fun catchUp_beforeFirstSample_isANoOp() {
        val fake = FakeTrafficStatsProvider(rx = 1234L, tx = 567L, timeMs = 1000L)
        val dataSource = TrafficDataSource(fake)

        dataSource.catchUp()

        assertThat(dataSource.peekDelta().totalBytes).isEqualTo(0L)
    }
}
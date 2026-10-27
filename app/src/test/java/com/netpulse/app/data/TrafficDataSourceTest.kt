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
}
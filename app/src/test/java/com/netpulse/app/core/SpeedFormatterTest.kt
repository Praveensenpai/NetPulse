package com.netpulse.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeedFormatterTest {

    @Test
    fun formatSpeed_zeroBytes_returnsZero() {
        val result = SpeedFormatter.formatSpeed(0L, inBits = false)
        assertThat(result.value).isEqualTo("0")
        assertThat(result.unit).isEqualTo("B/s")
    }

    @Test
    fun formatSpeed_negativeBytes_returnsZero() {
        val result = SpeedFormatter.formatSpeed(-50L, inBits = false)
        assertThat(result.value).isEqualTo("0")
        assertThat(result.unit).isEqualTo("B/s")
    }

    @Test
    fun formatSpeed_kilobytesRange_formatsCorrectly() {
        val bytes = 150L * 1024L
        val result = SpeedFormatter.formatSpeed(bytes, inBits = false)
        assertThat(result.unit).isEqualTo("KB/s")
        assertThat(result.value).isEqualTo("150")
    }

    @Test
    fun formatSpeed_megabytesRange_formatsCorrectly() {
        val bytes = (12.5 * 1024.0 * 1024.0).toLong()
        val result = SpeedFormatter.formatSpeed(bytes, inBits = false)
        assertThat(result.unit).isEqualTo("MB/s")
        assertThat(result.value).isEqualTo("12.5")
    }

    @Test
    fun formatSpeed_inBits_formatsMbpsCorrectly() {
        val bytes = (5 * 1000 * 1000 / 8).toLong()
        val result = SpeedFormatter.formatSpeed(bytes, inBits = true)
        assertThat(result.unit).isEqualTo("Mbps")
        assertThat(result.value).isEqualTo("5.0")
    }

    @Test
    fun formatDataUsage_megabytesAndGigabytes() {
        val mb = 450L * 1024L * 1024L
        assertThat(SpeedFormatter.formatDataUsage(mb)).isEqualTo("450.0 MB")

        val gb = (2.5 * 1024.0 * 1024.0 * 1024.0).toLong()
        assertThat(SpeedFormatter.formatDataUsage(gb)).isEqualTo("2.50 GB")
    }

    @Test
    fun formatDataUsage_zeroBytes_rendersZeroBytes() {
        assertThat(SpeedFormatter.formatDataUsage(0L)).isEqualTo("0 B")
    }

    @Test
    fun formatDataUsage_negativeBytes_rendersZeroBytes() {
        assertThat(SpeedFormatter.formatDataUsage(-1024L)).isEqualTo("0 B")
    }

    @Test
    fun formatDataUsage_subKilobyte_rendersExactBytes() {
        assertThat(SpeedFormatter.formatDataUsage(512L)).isEqualTo("512 B")
    }

    @Test
    fun formatDataUsage_subMegabyte_rendersKilobytes() {
        val bytes = 300L * 1024L
        assertThat(SpeedFormatter.formatDataUsage(bytes)).isEqualTo("300 KB")
    }
}

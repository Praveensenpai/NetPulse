package com.netpulse.app.service

import com.google.common.truth.Truth.assertThat
import com.netpulse.app.core.ActiveNetworkType
import org.junit.Test

class UsageAttributionTest {

    @Test
    fun `wifi bytes go to the wifi bucket`() {
        val result = attributeUsage(ActiveNetworkType.WIFI, 4096L)

        assertThat(result).isEqualTo(UsageAttribution.Record(mobileBytes = 0L, wifiBytes = 4096L))
    }

    @Test
    fun `mobile bytes go to the mobile bucket`() {
        val result = attributeUsage(ActiveNetworkType.MOBILE, 2048L)

        assertThat(result).isEqualTo(UsageAttribution.Record(mobileBytes = 2048L, wifiBytes = 0L))
    }

    @Test
    fun `mobile bytes with hotspot attributes hotspot separately`() {
        val result = attributeUsage(ActiveNetworkType.MOBILE, deltaBytes = 5000L, hotspotBytes = 3000L)

        assertThat(result).isEqualTo(
            UsageAttribution.Record(
                mobileBytes = 2000L,
                wifiBytes = 0L,
                hotspotBytes = 3000L
            )
        )
    }

    @Test
    fun `wifi bytes with hotspot attributes hotspot separately`() {
        val result = attributeUsage(ActiveNetworkType.WIFI, deltaBytes = 6000L, hotspotBytes = 4000L)

        assertThat(result).isEqualTo(
            UsageAttribution.Record(
                mobileBytes = 0L,
                wifiBytes = 2000L,
                hotspotBytes = 4000L
            )
        )
    }

    @Test
    fun `unknown transport holds bytes for the next tick`() {
        assertThat(attributeUsage(ActiveNetworkType.NONE, 1024L))
            .isEqualTo(UsageAttribution.Hold)
    }

    @Test
    fun `transport without a bucket is dropped rather than misattributed`() {
        assertThat(attributeUsage(ActiveNetworkType.ETHERNET, 1024L))
            .isEqualTo(UsageAttribution.Drop)
        assertThat(attributeUsage(ActiveNetworkType.OTHER, 1024L))
            .isEqualTo(UsageAttribution.Drop)
    }

    @Test
    fun `no measured bytes is a no-op`() {
        assertThat(attributeUsage(ActiveNetworkType.WIFI, 0L)).isEqualTo(UsageAttribution.Drop)
        assertThat(attributeUsage(ActiveNetworkType.NONE, -5L)).isEqualTo(UsageAttribution.Drop)
    }

    @Test
    fun `held bytes stop accumulating at the ceiling`() {
        // The pending delta is the accumulator: a Hold leaves the bytes in
        // place, so the next tick re-peeks the grown total. These are the exact
        // values the polling loop reaches after a long no-transport stretch.
        val atLimit = attributeUsage(ActiveNetworkType.NONE, MAX_HELD_USAGE_BYTES)
        assertThat(atLimit).isEqualTo(UsageAttribution.Hold)

        val overLimit = attributeUsage(ActiveNetworkType.NONE, MAX_HELD_USAGE_BYTES + 1L)
        assertThat(overLimit).isEqualTo(UsageAttribution.Drop)
    }

    @Test
    fun `tethering with screen off attributes all bytes to hotspot`() {
        val result = attributeUsage(
            type = ActiveNetworkType.MOBILE,
            deltaBytes = 50_000_000L,
            hotspotBytes = 100L,
            isTethering = true,
            isScreenOn = false
        )

        assertThat(result).isEqualTo(
            UsageAttribution.Record(
                mobileBytes = 0L,
                wifiBytes = 0L,
                hotspotBytes = 50_000_000L
            )
        )
    }
}
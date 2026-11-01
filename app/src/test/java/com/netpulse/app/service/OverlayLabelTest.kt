package com.netpulse.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OverlayLabelTest {

    @Test
    fun `both directions render when upload is shown`() {
        val text = OverlayLabel.format(
            downloadBytesPerSec = 1_048_576L,
            uploadBytesPerSec = 1024L,
            showUpload = true
        )
        assertThat(text).isEqualTo("\u2193 1.0 MB/s  \u2191 1.0 KB/s")
    }

    @Test
    fun `upload is omitted when the setting is off`() {
        val text = OverlayLabel.format(
            downloadBytesPerSec = 1_048_576L,
            uploadBytesPerSec = 1024L,
            showUpload = false
        )
        assertThat(text).isEqualTo("\u2193 1.0 MB/s")
        assertThat(text).doesNotContain("\u2191")
    }

    @Test
    fun `unit preference applies to both directions`() {
        // 125_000 B/s = 1,000,000 bps = 1.0 Mbps
        val text = OverlayLabel.format(
            downloadBytesPerSec = 125_000L,
            uploadBytesPerSec = 125_000L,
            unitInBits = true,
            showUpload = true
        )
        assertThat(text).isEqualTo("\u2193 1.0 Mbps  \u2191 1.0 Mbps")
    }

    @Test
    fun `idle renders zero rather than an empty pill`() {
        val text = OverlayLabel.format(
            downloadBytesPerSec = 0L,
            uploadBytesPerSec = 0L,
            showUpload = false
        )
        assertThat(text).isEqualTo("\u2193 0 B/s")
    }
}
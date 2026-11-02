package com.netpulse.app.domain

import com.google.common.truth.Truth.assertThat
import com.netpulse.app.domain.model.SpeedSnapshot
import org.junit.Test

class SpeedSnapshotTest {

    @Test
    fun totalBytesPerSec_computesSum() {
        val snapshot = SpeedSnapshot(
            downloadBytesPerSec = 1000L,
            uploadBytesPerSec = 500L
        )
        assertThat(snapshot.totalBytesPerSec).isEqualTo(1500L)
    }

    @Test
    fun isIdle_whenZeroRates_returnsTrue() {
        val idleSnapshot = SpeedSnapshot(0L, 0L)
        assertThat(idleSnapshot.isIdle).isTrue()

        val activeSnapshot = SpeedSnapshot(100L, 0L)
        assertThat(activeSnapshot.isIdle).isFalse()
    }

    @Test
    fun displayBytesPerSec_includesUploadByDefault() {
        val snapshot = SpeedSnapshot(downloadBytesPerSec = 1000L, uploadBytesPerSec = 500L)
        assertThat(snapshot.displayBytesPerSec(includeUpload = true)).isEqualTo(1500L)
    }

    @Test
    fun displayBytesPerSec_excludesUploadWhenHidden() {
        val snapshot = SpeedSnapshot(downloadBytesPerSec = 1000L, uploadBytesPerSec = 500L)
        assertThat(snapshot.displayBytesPerSec(includeUpload = false)).isEqualTo(1000L)
    }

    @Test
    fun displayBytesPerSec_uploadOnlyTrafficReadsZeroWhenHidden() {
        // The visible regression: a cloud backup with the setting off used to
        // show the upload rate in the glyph while the title read `0 B/s`.
        val snapshot = SpeedSnapshot(downloadBytesPerSec = 0L, uploadBytesPerSec = 900_000L)
        assertThat(snapshot.displayBytesPerSec(includeUpload = false)).isEqualTo(0L)
        assertThat(snapshot.displayBytesPerSec(includeUpload = true)).isEqualTo(900_000L)
    }
}

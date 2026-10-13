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
}

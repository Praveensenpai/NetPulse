package com.netpulse.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeedIconLabelTest {

    @Test
    fun idleReadingShowsZeroWithUnit() {
        assertThat(SpeedIconLabel.compact("0", "B/s")).isEqualTo("0B")
    }

    @Test
    fun decimalIsDroppedAndRoundedUp() {
        assertThat(SpeedIconLabel.compact("1.8", "KB/s")).isEqualTo("2K")
    }

    @Test
    fun decimalIsDroppedAndRoundedDown() {
        assertThat(SpeedIconLabel.compact("5.6", "MB/s")).isEqualTo("6M")
    }

    @Test
    fun twoDigitReadingKeepsItsUnit() {
        assertThat(SpeedIconLabel.compact("41.0", "KB/s")).isEqualTo("41K")
    }

    @Test
    fun threeDigitReadingIsPromotedNotTruncated() {
        assertThat(SpeedIconLabel.compact("988", "KB/s")).isEqualTo("1M")
    }

    @Test
    fun largeMegabyteReadingPromotesToGigabytes() {
        assertThat(SpeedIconLabel.compact("1500", "MB/s")).isEqualTo("1G")
    }

    @Test
    fun tinyNonZeroValueReportsLowerBound() {
        assertThat(SpeedIconLabel.compact("0.3", "KB/s")).isEqualTo("<1K")
    }

    @Test
    fun labelNeverExceedsThreeCharacters() {
        val readings = listOf(
            "0" to "B/s", "999" to "B/s", "1.8" to "KB/s", "988" to "KB/s",
            "1023" to "KB/s", "1.8" to "MB/s", "1023" to "MB/s", "9999" to "MB/s",
            "1.8" to "GB/s", "9999" to "GB/s"
        )
        for ((value, unit) in readings) {
            val label = SpeedIconLabel.compact(value, unit)
            assertThat(label.length).isAtMost(SpeedIconLabel.MAX_LENGTH)
        }
    }

    @Test
    fun bitUnitsMapToSameLetter() {
        assertThat(SpeedIconLabel.compact("2.4", "Mbps")).isEqualTo("2M")
    }
}

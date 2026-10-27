package com.netpulse.app.data.source

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUsageDataSourceTest {

    private val dayStart = 1_793_125_800_000L
    private val bucketMs = 7_200_000L

    @Test
    fun bucketEndingExactlyAtMidnight_isExcluded() {
        assertThat(AppUsageDataSource.countsTowardDay(dayStart, dayStart)).isFalse()
    }

    @Test
    fun bucketEndingOneMsAfterMidnight_isIncluded() {
        assertThat(AppUsageDataSource.countsTowardDay(dayStart + 1, dayStart)).isTrue()
    }

    @Test
    fun bucketEndingLaterToday_isIncluded() {
        assertThat(AppUsageDataSource.countsTowardDay(dayStart + bucketMs, dayStart)).isTrue()
    }

    @Test
    fun bucketEntirelyBeforeMidnight_isExcluded() {
        assertThat(AppUsageDataSource.countsTowardDay(dayStart - bucketMs, dayStart)).isFalse()
    }

    /**
     * Documents the known overcount: a bucket spanning midnight is kept whole,
     * because the framework gives no way to split it.
     */
    @Test
    fun bucketStraddlingMidnight_isIncludedWhole() {
        val straddlingEnd = dayStart + bucketMs
        assertThat(AppUsageDataSource.countsTowardDay(straddlingEnd, dayStart)).isTrue()
    }
}

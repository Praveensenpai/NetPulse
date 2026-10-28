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

    @Test
    fun baselineWindow_openJustAfterMidnight() {
        assertThat(AppUsageDataSource.withinBaselineWindow(dayStart + 1_000L, dayStart)).isTrue()
    }

    @Test
    fun baselineWindow_stillOpenAtGraceBoundary() {
        assertThat(AppUsageDataSource.withinBaselineWindow(dayStart + 120_000L, dayStart)).isTrue()
    }

    @Test
    fun baselineWindow_closedOneMsAfterGrace() {
        assertThat(AppUsageDataSource.withinBaselineWindow(dayStart + 120_001L, dayStart)).isFalse()
    }

    @Test
    fun baselineWindow_closedLateInTheDay() {
        assertThat(AppUsageDataSource.withinBaselineWindow(dayStart + 16 * 3_600_000L, dayStart)).isFalse()
    }

    @Test
    fun sinceBaseline_reportsDeltaWhenHistoryGrew() {
        assertThat(AppUsageDataSource.sinceBaseline(2_082_550L, 2_080_000L)).isEqualTo(2_550L)
    }

    @Test
    fun sinceBaseline_reportsZeroWhenNothingChanged() {
        assertThat(AppUsageDataSource.sinceBaseline(2_080_000L, 2_080_000L)).isEqualTo(0L)
    }

    @Test
    fun sinceBaseline_returnsNullWhenBaselineIsStale() {
        assertThat(AppUsageDataSource.sinceBaseline(1_000L, 2_080_000L)).isNull()
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

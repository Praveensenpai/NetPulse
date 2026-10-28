package com.netpulse.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUsageTrackerTest {

    private val now = 1_793_160_000_000L
    private val dayStart = 1_793_125_800_000L

    private fun adoptable(
        atMs: Long = now - 60_000L,
        usesBaseline: Boolean = false,
        currentUsesBaseline: Boolean = false
    ) = AppUsageTracker.isAdoptable(
        snapshotAtMs = atMs,
        snapshotUsesBaseline = usesBaseline,
        nowMs = now,
        currentUsesBaseline = currentUsesBaseline,
        dayStartMs = dayStart
    )

    @Test
    fun adoptable_recentSameModeSameDay() {
        assertThat(adoptable()).isTrue()
    }

    @Test
    fun adoptable_rejectedWhenAccountingModeDiffers() {
        assertThat(adoptable(usesBaseline = true)).isFalse()
    }

    @Test
    fun adoptable_rejectedWhenSnapshotIsFromTheFuture() {
        assertThat(adoptable(atMs = now + 1_000L)).isFalse()
    }

    @Test
    fun adoptable_rejectedAtRecoveryWindowBoundary() {
        assertThat(adoptable(atMs = now - 300_000L)).isTrue()
        assertThat(adoptable(atMs = now - 300_001L)).isFalse()
    }

    @Test
    fun adoptable_rejectedWhenSnapshotPredatesMidnight() {
        assertThat(adoptable(atMs = dayStart - 1L)).isFalse()
    }

    @Test
    fun mergedTotals_withoutCarry_usesFreshOnly() {
        val previous = mapOf("YouTube" to 10_000L, "Chrome" to 500L)
        val fresh = mapOf("Chrome" to 900L)
        val merged = AppUsageTracker.mergedTotals(previous, fresh, carry = false)
        assertThat(merged).containsExactly("Chrome", 900L)
    }

    @Test
    fun mergedTotals_withCarry_keepsUnreachedPackages() {
        val previous = mapOf("YouTube" to 10_000L, "Chrome" to 500L)
        val fresh = mapOf("Chrome" to 900L)
        val merged = AppUsageTracker.mergedTotals(previous, fresh, carry = true)
        assertThat(merged).containsExactly("YouTube", 10_000L, "Chrome", 900L)
    }

    @Test
    fun mergedTotals_zeroFromSweepSupersedesCarried() {
        // A package swept and measured at 0 B must not keep its carried
        // baseline; that is what left a stale 223 MB total in the snapshot.
        val previous = mapOf("YouTube" to 223_837_537L)
        val fresh = mapOf("YouTube" to 2_137_098L)
        val merged = AppUsageTracker.mergedTotals(previous, fresh, carry = true)
        assertThat(merged).containsExactly("YouTube", 2_137_098L)
    }

    @Test
    fun mergedTotals_zeroValueIsStillAFreshMeasurement() {
        val previous = mapOf("YouTube" to 5_000L)
        val fresh = mapOf("YouTube" to 0L)
        val merged = AppUsageTracker.mergedTotals(previous, fresh, carry = true)
        assertThat(merged).containsExactly("YouTube", 0L)
    }

    @Test
    fun mergedTotals_freshValueSupersedesCarried() {
        val previous = mapOf("YouTube" to 10_000L)
        val fresh = mapOf("YouTube" to 11_500L)
        val merged = AppUsageTracker.mergedTotals(previous, fresh, carry = true)
        assertThat(merged).containsExactly("YouTube", 11_500L)
    }
}

package com.netpulse.app.service

import com.netpulse.app.core.ActiveNetworkType

/**
 * Upper bound on bytes parked while the active transport is unknown.
 *
 * Bytes are only held when the platform reports no active network, which in
 * practice is a handover window of a few seconds. The ceiling exists so a long
 * stretch without a usable transport cannot park an arbitrarily large figure and
 * then dump it into a later day's bucket.
 */
internal const val MAX_HELD_USAGE_BYTES = 64L * 1024 * 1024

/** What the sampling loop should do with the bytes measured since the last tick. */
internal sealed interface UsageAttribution {
    /** Account for these bytes against one bucket. */
    data class Record(val mobileBytes: Long, val wifiBytes: Long) : UsageAttribution

    /** Keep the bytes pending and re-evaluate on the next tick. */
    data object Hold : UsageAttribution

    /** Discard the bytes; they can never be attributed correctly. */
    data object Drop : UsageAttribution
}

/**
 * Decides how measured bytes are attributed.
 *
 * Kept pure and outside [NetPulseService] so the rule is unit-testable without an
 * Android runtime. The previous inline `when` in the service cleared the pending
 * counters before deciding, so any tick whose transport was neither Wi-Fi nor
 * mobile lost those bytes outright. That is reachable: [ActiveNetworkType.NONE]
 * is what the platform reports during a handover and before the first network
 * attaches, and an always-on VPN surfaces as [ActiveNetworkType.OTHER] because
 * its capabilities carry the VPN transport instead of the physical one.
 */
internal fun attributeUsage(
    type: ActiveNetworkType,
    deltaBytes: Long,
    heldBytes: Long = 0L
): UsageAttribution = when {
    deltaBytes <= 0L -> UsageAttribution.Drop
    type == ActiveNetworkType.WIFI -> UsageAttribution.Record(0L, deltaBytes)
    type == ActiveNetworkType.MOBILE -> UsageAttribution.Record(deltaBytes, 0L)
    type == ActiveNetworkType.NONE && heldBytes + deltaBytes <= MAX_HELD_USAGE_BYTES ->
        UsageAttribution.Hold
    else -> UsageAttribution.Drop
}

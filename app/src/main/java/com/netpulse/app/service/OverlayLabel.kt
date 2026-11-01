package com.netpulse.app.service

import com.netpulse.app.core.SpeedFormatter

/**
 * Builds the floating overlay's text.
 *
 * Kept pure and outside [FloatingOverlayManager] so the "Show Upload Speed"
 * rule is unit-testable without an Android runtime. The overlay previously
 * rendered both directions unconditionally, so the setting silently applied to
 * the notification and the Home gauge but not to the pill the user is looking
 * at.
 */
internal object OverlayLabel {

    fun format(
        downloadBytesPerSec: Long,
        uploadBytesPerSec: Long,
        unitInBits: Boolean = false,
        showUpload: Boolean = true
    ): String {
        val dl = SpeedFormatter.formatSpeed(downloadBytesPerSec, unitInBits)
        if (!showUpload) return "\u2193 ${dl.displayString}"

        val ul = SpeedFormatter.formatSpeed(uploadBytesPerSec, unitInBits)
        return "\u2193 ${dl.displayString}  \u2191 ${ul.displayString}"
    }
}

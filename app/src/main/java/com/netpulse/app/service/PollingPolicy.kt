package com.netpulse.app.service

/**
 * Decides whether the sampling loop should run.
 *
 * Kept pure and outside [NetPulseService] so the screen-off rule is
 * unit-testable without an Android runtime.
 *
 * Screen-off suspension saves battery when the device is idle, but a device
 * acting as a Wi-Fi hotspot is actively routing network packets for other
 * clients even while its own display sleeps. If [monitorWhenTethering] is true
 * and tethering is active, the polling loop remains active across screen transitions.
 */
internal fun shouldPoll(
    pauseWhenScreenOff: Boolean,
    screenOn: Boolean,
    isTetheringActive: Boolean = false,
    monitorWhenTethering: Boolean = true
): Boolean = when {
    isTetheringActive && monitorWhenTethering -> true
    !pauseWhenScreenOff || screenOn -> true
    else -> false
}
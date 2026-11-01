package com.netpulse.app.service

/**
 * Decides whether the sampling loop should run.
 *
 * Kept pure and outside [NetPulseService] so the screen-off rule is
 * unit-testable without an Android runtime.
 *
 * This governs only the *decision*. The caller still has to read the actual
 * display state before asking, because registering [ScreenStateReceiver]
 * delivers future transitions only and never reports the state the display is
 * already in.
 */
internal fun shouldPoll(pauseWhenScreenOff: Boolean, screenOn: Boolean): Boolean =
    !pauseWhenScreenOff || screenOn
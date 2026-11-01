package com.netpulse.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PollingPolicyTest {

    @Test
    fun `polls when pause is disabled regardless of screen`() {
        assertThat(shouldPoll(pauseWhenScreenOff = false, screenOn = false)).isTrue()
        assertThat(shouldPoll(pauseWhenScreenOff = false, screenOn = true)).isTrue()
    }

    @Test
    fun `polls only while screen is on when pause is enabled`() {
        assertThat(shouldPoll(pauseWhenScreenOff = true, screenOn = true)).isTrue()
        assertThat(shouldPoll(pauseWhenScreenOff = true, screenOn = false)).isFalse()
    }
}

package com.netpulse.app.domain.model

data class SpeedSnapshot(
    val downloadBytesPerSec: Long = 0L,
    val uploadBytesPerSec: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalBytesPerSec: Long get() = downloadBytesPerSec + uploadBytesPerSec
    val isIdle: Boolean get() = downloadBytesPerSec == 0L && uploadBytesPerSec == 0L
}

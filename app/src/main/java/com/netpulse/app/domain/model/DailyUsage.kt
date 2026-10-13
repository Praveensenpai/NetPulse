package com.netpulse.app.domain.model

data class DailyUsage(
    val dateKey: String, // "YYYY-MM-DD"
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L
) {
    val totalBytes: Long get() = mobileBytes + wifiBytes
}

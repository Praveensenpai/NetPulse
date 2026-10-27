package com.netpulse.app.domain.model

/** Cumulative traffic attributed to a single application package. */
data class AppUsage(
    val packageName: String,
    val label: String,
    val totalBytes: Long,
    val bytesPerSec: Long = 0L
)
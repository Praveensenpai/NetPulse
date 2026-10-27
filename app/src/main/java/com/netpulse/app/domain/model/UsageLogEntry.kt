package com.netpulse.app.domain.model

/** One minute-resolution record of bytes consumed by a package. */
data class UsageLogEntry(
    val minuteMs: Long,
    val packageName: String,
    val label: String,
    val bytes: Long
)
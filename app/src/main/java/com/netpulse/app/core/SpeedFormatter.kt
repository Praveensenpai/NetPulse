package com.netpulse.app.core

import java.util.Locale

object SpeedFormatter {

    fun formatSpeed(bytesPerSec: Long, inBits: Boolean = false): FormattedSpeed {
        if (bytesPerSec <= 0L) {
            val unit = if (inBits) "bps" else "B/s"
            return FormattedSpeed(value = "0", unit = unit)
        }

        return if (inBits) {
            formatBits(bytesPerSec * 8.0)
        } else {
            formatBytes(bytesPerSec.toDouble())
        }
    }

    private fun formatBytes(bytes: Double): FormattedSpeed {
        return when {
            bytes < 1024.0 -> {
                FormattedSpeed(value = String.format(Locale.US, "%.0f", bytes), unit = "B/s")
            }
            bytes < 1024.0 * 1024.0 -> {
                val kb = bytes / 1024.0
                val formatted = if (kb < 100.0) {
                    String.format(Locale.US, "%.1f", kb)
                } else {
                    String.format(Locale.US, "%.0f", kb)
                }
                FormattedSpeed(value = formatted, unit = "KB/s")
            }
            bytes < 1024.0 * 1024.0 * 1024.0 -> {
                val mb = bytes / (1024.0 * 1024.0)
                FormattedSpeed(value = String.format(Locale.US, "%.1f", mb), unit = "MB/s")
            }
            else -> {
                val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                FormattedSpeed(value = String.format(Locale.US, "%.2f", gb), unit = "GB/s")
            }
        }
    }

    private fun formatBits(bits: Double): FormattedSpeed {
        return when {
            bits < 1000.0 -> {
                FormattedSpeed(value = String.format(Locale.US, "%.0f", bits), unit = "bps")
            }
            bits < 1000.0 * 1000.0 -> {
                val kbps = bits / 1000.0
                val formatted = if (kbps < 100.0) {
                    String.format(Locale.US, "%.1f", kbps)
                } else {
                    String.format(Locale.US, "%.0f", kbps)
                }
                FormattedSpeed(value = formatted, unit = "Kbps")
            }
            bits < 1000.0 * 1000.0 * 1000.0 -> {
                val mbps = bits / (1000.0 * 1000.0)
                FormattedSpeed(value = String.format(Locale.US, "%.1f", mbps), unit = "Mbps")
            }
            else -> {
                val gbps = bits / (1000.0 * 1000.0 * 1000.0)
                FormattedSpeed(value = String.format(Locale.US, "%.2f", gbps), unit = "Gbps")
            }
        }
    }

    fun formatDataUsage(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            kb < 1.0 -> "0 MB"
            mb < 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            mb < 1024.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.2f GB", mb / 1024.0)
        }
    }
}

data class FormattedSpeed(
    val value: String,
    val unit: String
) {
    val displayString: String get() = "$value $unit"
}

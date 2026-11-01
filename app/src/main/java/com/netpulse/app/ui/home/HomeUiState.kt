package com.netpulse.app.ui.home

import com.netpulse.app.core.ActiveNetworkType
import com.netpulse.app.core.FormattedSpeed
import com.netpulse.app.domain.model.UsageHistory

enum class UsageWindow(val label: String) {
    TODAY("Today"),
    WEEK("This Week"),
    MONTH("This Month"),
    ALL_TIME("All Time")
}

data class HomeUiState(
    val isServiceEnabled: Boolean = false,
    val downloadSpeed: FormattedSpeed = FormattedSpeed("0", "B/s"),
    val uploadSpeed: FormattedSpeed = FormattedSpeed("0", "B/s"),
    val downloadBytesPerSec: Long = 0L,
    val uploadBytesPerSec: Long = 0L,
    val usageHistory: UsageHistory = UsageHistory.EMPTY,
    val selectedWindow: UsageWindow = UsageWindow.TODAY,
    val networkType: ActiveNetworkType = ActiveNetworkType.NONE,
    val isBatteryOptimized: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val showUploadSpeed: Boolean = true
)

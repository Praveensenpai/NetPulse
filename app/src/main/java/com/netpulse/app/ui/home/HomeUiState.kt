package com.netpulse.app.ui.home

import com.netpulse.app.core.ActiveNetworkType
import com.netpulse.app.core.FormattedSpeed
import com.netpulse.app.domain.model.DailyUsage

data class HomeUiState(
    val isServiceEnabled: Boolean = false,
    val downloadSpeed: FormattedSpeed = FormattedSpeed("0", "B/s"),
    val uploadSpeed: FormattedSpeed = FormattedSpeed("0", "B/s"),
    val downloadBytesPerSec: Long = 0L,
    val uploadBytesPerSec: Long = 0L,
    val dailyUsage: DailyUsage = DailyUsage(""),
    val networkType: ActiveNetworkType = ActiveNetworkType.NONE,
    val isBatteryOptimized: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val showUploadSpeed: Boolean = true
)
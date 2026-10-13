package com.netpulse.app.ui.home

import com.netpulse.app.core.ActiveNetworkType
import com.netpulse.app.core.FormattedSpeed
import com.netpulse.app.domain.model.DailyUsage

data class HomeUiState(
    val isServiceEnabled: Boolean = true,
    val downloadSpeed: FormattedSpeed = FormattedSpeed("0", "B/s"),
    val uploadSpeed: FormattedSpeed = FormattedSpeed("0", "B/s"),
    val dailyUsage: DailyUsage = DailyUsage(""),
    val networkType: ActiveNetworkType = ActiveNetworkType.NONE,
    val isBatteryOptimized: Boolean = false
)

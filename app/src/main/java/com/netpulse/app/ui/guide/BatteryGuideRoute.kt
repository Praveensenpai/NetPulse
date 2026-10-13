package com.netpulse.app.ui.guide

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.netpulse.app.core.BatteryOptimizer

@Composable
fun BatteryGuideRoute(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BatteryGuideScreen(
        onOpenBatterySettings = { BatteryOptimizer.requestIgnoreBatteryOptimizations(context) },
        onOpenDeviceCare = { BatteryOptimizer.openSamsungDeviceCare(context) },
        modifier = modifier
    )
}

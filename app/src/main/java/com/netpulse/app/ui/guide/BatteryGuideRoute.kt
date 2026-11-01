package com.netpulse.app.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleEventObserver
import com.netpulse.app.core.BatteryOptimizer

@Composable
fun BatteryGuideRoute(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Exemption is granted in another app, so it can only change while this
    // screen is stopped. Re-reading on every resume keeps the button honest.
    var isExempt by remember { mutableStateOf(BatteryOptimizer.isIgnoringBatteryOptimizations(context)) }
    val isSamsung = remember { BatteryOptimizer.isSamsungDevice() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isExempt = BatteryOptimizer.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BatteryGuideScreen(
        isBatteryExempt = isExempt,
        isSamsungDevice = isSamsung,
        onOpenBatterySettings = {
            BatteryOptimizer.requestIgnoreBatteryOptimizations(context)
            isExempt = BatteryOptimizer.isIgnoringBatteryOptimizations(context)
        },
        onOpenDeviceCare = {
            if (!BatteryOptimizer.openSamsungDeviceCare(context)) {
                BatteryOptimizer.openAppDetailsSettings(context)
            }
        },
        modifier = modifier
    )
}

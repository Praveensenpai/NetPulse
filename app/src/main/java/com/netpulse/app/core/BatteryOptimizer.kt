package com.netpulse.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.netpulse.app.core.Constants.SAMSUNG_DEVICE_CARE_PACKAGE
import com.netpulse.app.core.Constants.SAMSUNG_SMART_MANAGER_PACKAGE

/** Outcome of asking the platform for a battery optimisation exemption. */
enum class BatteryExemptionResult {
    /** The app already holds the exemption; nothing was launched. */
    ALREADY_EXEMPT,

    /** The system exemption dialog was launched. */
    REQUESTED,

    /** No exemption activity on this device; app settings were opened instead. */
    UNAVAILABLE
}

object BatteryOptimizer {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * Asks the platform to exempt this app from battery optimisation.
     *
     * Returns [BatteryExemptionResult.ALREADY_EXEMPT] without launching anything
     * when the app is already exempt. Android's
     * `RequestIgnoreBatteryOptimizations` activity has nothing to ask in that
     * case, so it opens and finishes immediately — the screen flashes and the
     * user is left on the previous page, which reads as a dead button.
     */
    fun requestIgnoreBatteryOptimizations(context: Context): BatteryExemptionResult {
        if (isIgnoringBatteryOptimizations(context)) {
            return BatteryExemptionResult.ALREADY_EXEMPT
        }
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            BatteryExemptionResult.REQUESTED
        } catch (_: Exception) {
            openAppDetailsSettings(context)
            BatteryExemptionResult.UNAVAILABLE
        }
    }

    /**
     * Opens Samsung's battery screen, or app info when no Samsung package exists.
     *
     * Callers should gate the label on [isSamsungDevice]; this fallback keeps the
     * button useful on hardware where Device Care is absent instead of failing.
     */
    fun openSamsungDeviceCare(context: Context): Boolean {
        val pm = context.packageManager
        val candidates = listOf(
            SAMSUNG_DEVICE_CARE_PACKAGE,
            SAMSUNG_SMART_MANAGER_PACKAGE
        )
        for (pkg in candidates) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(launchIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        return false
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun isSamsungDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("samsung") || brand.contains("samsung")
    }
}

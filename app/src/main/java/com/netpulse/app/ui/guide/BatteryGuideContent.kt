package com.netpulse.app.ui.guide

/** One actionable step in the battery guide. */
data class BatteryGuideStep(
    val title: String,
    val description: String
)

/**
 * Device-appropriate copy for the battery guide screen.
 *
 * Kept free of Compose types so the wording can be unit-tested: the guide used
 * to describe Samsung One UI on every device, including hardware that has no
 * Device Care or Deep Sleeping Apps at all.
 */
data class BatteryGuideContent(
    val isSamsungDevice: Boolean,
    val introTitle: String,
    val introBody: String,
    val steps: List<BatteryGuideStep>
)

object BatteryGuideTexts {

    fun forDevice(isSamsungDevice: Boolean): BatteryGuideContent =
        if (isSamsungDevice) SAMSUNG else GENERIC

    private val SAMSUNG = BatteryGuideContent(
        isSamsungDevice = true,
        introTitle = "Samsung One UI Optimization",
        introBody = "Samsung devices employ strict background limits. Follow these three " +
            "steps to ensure NetPulse runs accurately with minimal battery overhead.",
        steps = listOf(
            BatteryGuideStep(
                title = "Set Battery to Unrestricted",
                description = "Navigate to App Info > Battery and select 'Unrestricted' so " +
                    "Samsung One UI will not pause telemetry when backgrounded."
            ),
            BatteryGuideStep(
                title = "Exclude from Deep Sleeping Apps",
                description = "In Samsung Device Care > Battery > Background usage limits, " +
                    "verify NetPulse is not listed in Sleeping or Deep sleeping apps."
            ),
            BatteryGuideStep(
                title = "Auto-Start & Lock in Recents",
                description = "On Samsung One UI, tap the app icon in the Recents overview " +
                    "and select 'Lock this app' for continuous foreground resilience."
            )
        )
    )

    private val GENERIC = BatteryGuideContent(
        isSamsungDevice = false,
        introTitle = "Battery Optimization",
        introBody = "Android applies background limits that can pause NetPulse while the " +
            "screen is off. Follow these three steps to keep monitoring accurate.",
        steps = listOf(
            BatteryGuideStep(
                title = "Set Battery Usage to Unrestricted",
                description = "Open App Info > Battery and choose 'Unrestricted' so the " +
                    "system will not pause telemetry when the app is backgrounded."
            ),
            BatteryGuideStep(
                title = "Allow Background Data",
                description = "In App Info > Mobile data, enable background data so usage " +
                    "keeps being recorded while NetPulse is not in the foreground."
            ),
            BatteryGuideStep(
                title = "Keep the App in Recents",
                description = "Do not swipe NetPulse away from the Recents overview; " +
                    "clearing it lets the launcher stop the monitoring service."
            )
        )
    )
}

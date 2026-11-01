package com.netpulse.app.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.netpulse.app.ui.theme.Dimens

private val STEP_ICONS: List<ImageVector> = listOf(
    Icons.Rounded.BatteryChargingFull,
    Icons.Rounded.Shield,
    Icons.Rounded.Check
)

@Composable
fun BatteryGuideScreen(
    isBatteryExempt: Boolean,
    isSamsungDevice: Boolean,
    onOpenBatterySettings: () -> Unit,
    onOpenDeviceCare: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Wording comes from BatteryGuideTexts so a non-Samsung device is never told
    // about One UI, Device Care, or Deep Sleeping Apps it does not have.
    val content = BatteryGuideTexts.forDevice(isSamsungDevice)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.md)
    ) {
        item(key = "guide_intro") {
            GuideIntroCard(content = content)
        }

        itemsIndexed(
            items = content.steps,
            key = { index, _ -> "step_$index" }
        ) { index, step ->
            GuideStepCard(
                stepNumber = (index + 1).toString(),
                step = step,
                icon = STEP_ICONS.getOrElse(index) { Icons.Rounded.Check }
            )
        }

        item(key = "actions") {
            GuideActionButtons(
                isBatteryExempt = isBatteryExempt,
                isSamsungDevice = isSamsungDevice,
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenDeviceCare = onOpenDeviceCare
            )
        }
    }
}

@Composable
private fun GuideIntroCard(
    content: BatteryGuideContent,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cornerMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(Dimens.md)) {
            Text(
                text = content.introTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.xs))
            Text(
                text = content.introBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GuideStepCard(
    stepNumber: String,
    step: BatteryGuideStep,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cornerMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconSizeMd)
            )
            Spacer(modifier = Modifier.width(Dimens.md))
            Column {
                Text(
                    text = "Step $stepNumber: ${step.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Dimens.xs))
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GuideActionButtons(
    isBatteryExempt: Boolean,
    isSamsungDevice: Boolean,
    onOpenBatterySettings: () -> Unit,
    onOpenDeviceCare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        // Launching the exemption intent while the app is already exempt opens a
        // settings screen that finishes instantly, so the button would flash and
        // return with no visible effect. Show the granted state instead.
        Button(
            onClick = onOpenBatterySettings,
            enabled = !isBatteryExempt,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.cornerMd)
        ) {
            Icon(
                imageVector = if (isBatteryExempt) {
                    Icons.Rounded.Check
                } else {
                    Icons.Rounded.BatteryChargingFull
                },
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeSm)
            )
            Spacer(modifier = Modifier.width(Dimens.sm))
            Text(
                text = if (isBatteryExempt) {
                    "Battery Exemption Granted"
                } else {
                    "Open Battery Exemption Settings"
                }
            )
        }
        OutlinedButton(
            onClick = onOpenDeviceCare,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.cornerMd)
        ) {
            Icon(
                imageVector = if (isSamsungDevice) {
                    Icons.Rounded.SettingsApplications
                } else {
                    Icons.Rounded.Info
                },
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeSm)
            )
            Spacer(modifier = Modifier.width(Dimens.sm))
            Text(
                text = if (isSamsungDevice) {
                    "Open Samsung Device Care"
                } else {
                    "Open App Info"
                }
            )
        }
    }
}

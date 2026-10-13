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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

@Composable
fun BatteryGuideScreen(
    onOpenBatterySettings: () -> Unit,
    onOpenDeviceCare: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.md)
    ) {
        item(key = "guide_intro") {
            GuideIntroCard()
        }

        item(key = "step_1") {
            GuideStepCard(
                stepNumber = "1",
                title = "Set Battery to Unrestricted",
                description = "Navigate to App Info > Battery and select 'Unrestricted' so Samsung One UI will not pause telemetry when backgrounded.",
                icon = Icons.Rounded.BatteryChargingFull
            )
        }

        item(key = "step_2") {
            GuideStepCard(
                stepNumber = "2",
                title = "Exclude from Deep Sleeping Apps",
                description = "In Samsung Device Care > Battery > Background usage limits, verify NetPulse is not listed in Sleeping or Deep sleeping apps.",
                icon = Icons.Rounded.Shield
            )
        }

        item(key = "step_3") {
            GuideStepCard(
                stepNumber = "3",
                title = "Auto-Start & Lock in Recents",
                description = "On Samsung One UI, tap the app icon in the Recents overview and select 'Lock this app' for continuous foreground resilience.",
                icon = Icons.Rounded.Check
            )
        }

        item(key = "actions") {
            GuideActionButtons(
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenDeviceCare = onOpenDeviceCare
            )
        }
    }
}

@Composable
private fun GuideIntroCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cornerMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(Dimens.md)) {
            Text(
                text = "Samsung One UI Optimization",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.xs))
            Text(
                text = "Samsung devices employ strict background limits. Follow these three steps to ensure NetPulse runs accurately with minimal battery overhead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GuideStepCard(
    stepNumber: String,
    title: String,
    description: String,
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
                    text = "Step $stepNumber: $title",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Dimens.xs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GuideActionButtons(
    onOpenBatterySettings: () -> Unit,
    onOpenDeviceCare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        Button(
            onClick = onOpenBatterySettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.cornerMd)
        ) {
            Icon(
                imageVector = Icons.Rounded.BatteryChargingFull,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeSm)
            )
            Spacer(modifier = Modifier.width(Dimens.sm))
            Text(text = "Open Battery Exemption Settings")
        }
        OutlinedButton(
            onClick = onOpenDeviceCare,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.cornerMd)
        ) {
            Icon(
                imageVector = Icons.Rounded.SettingsApplications,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeSm)
            )
            Spacer(modifier = Modifier.width(Dimens.sm))
            Text(text = "Open Samsung Device Care")
        }
    }
}

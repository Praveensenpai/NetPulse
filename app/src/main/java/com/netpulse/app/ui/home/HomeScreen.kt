package com.netpulse.app.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.netpulse.app.ui.components.SpeedGaugeCard
import com.netpulse.app.ui.components.StatusHeader
import com.netpulse.app.ui.components.UsageSummaryCard
import com.netpulse.app.ui.theme.BatteryWarningOrange
import com.netpulse.app.ui.theme.Dimens

@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.md)
    ) {
        item(key = "status_header") {
            StatusHeader(
                isServiceActive = state.isServiceEnabled,
                networkType = state.networkType
            )
        }

        item(key = "speed_gauge") {
            SpeedGaugeCard(
                downloadSpeed = state.downloadSpeed,
                uploadSpeed = state.uploadSpeed
            )
        }

        item(key = "usage_summary") {
            UsageSummaryCard(dailyUsage = state.dailyUsage)
        }

        if (!state.hasNotificationPermission) {
            item(key = "notif_warning") {
                PermissionNoticeCard(
                    title = "Notification Permission Required",
                    description = "Allow notifications to display real-time network speeds in your status bar.",
                    actionLabel = "Enable Notifications",
                    icon = Icons.Rounded.NotificationsActive,
                    onAction = { onEvent(HomeEvent.RequestNotificationPermission) }
                )
            }
        }

        if (state.isBatteryOptimized) {
            item(key = "battery_warning") {
                BatteryNoticeCard(
                    onFixClick = { onEvent(HomeEvent.RequestBatteryExemption) }
                )
            }
        }

        item(key = "toggle_action") {
            ServiceToggleCard(
                isServiceEnabled = state.isServiceEnabled,
                onToggle = { onEvent(HomeEvent.ToggleService(!state.isServiceEnabled)) }
            )
        }
    }
}

@Composable
private fun PermissionNoticeCard(
    title: String,
    description: String,
    actionLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cornerMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(Dimens.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.iconSizeMd)
                )
                Spacer(modifier = Modifier.size(Dimens.sm))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(Dimens.xs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.sm))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(Dimens.cornerPill)
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun BatteryNoticeCard(
    onFixClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cornerMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(Dimens.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.BatteryAlert,
                    contentDescription = null,
                    tint = BatteryWarningOrange,
                    modifier = Modifier.size(Dimens.iconSizeMd)
                )
                Spacer(modifier = Modifier.size(Dimens.sm))
                Text(
                    text = "Samsung Battery Restriction",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(Dimens.xs))
            Text(
                text = "One UI may stop NetPulse when locked. Set battery usage to Unrestricted for uninterrupted speeds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.sm))
            Button(
                onClick = onFixClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(Dimens.cornerPill)
            ) {
                Text(text = "Allow Unrestricted", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ServiceToggleCard(
    isServiceEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cornerMd),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isServiceEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.PowerSettingsNew,
            contentDescription = null,
            tint = if (isServiceEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(Dimens.iconSizeMd)
        )
        Spacer(modifier = Modifier.size(Dimens.sm))
        Text(
            text = if (isServiceEnabled) "Stop Monitoring" else "Start Monitoring",
            color = if (isServiceEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
        )
    }
}

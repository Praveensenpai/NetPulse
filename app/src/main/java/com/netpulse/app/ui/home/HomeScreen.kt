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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.netpulse.app.ui.components.AppUsageCard
import com.netpulse.app.ui.components.SpeedGaugeCard
import com.netpulse.app.ui.components.StatusHeader
import com.netpulse.app.ui.components.UsageSummaryCard
import com.netpulse.app.ui.components.UsageLogCard
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
                uploadSpeed = state.uploadSpeed,
                showUpload = state.showUploadSpeed
            )
        }

        item(key = "usage_summary") {
            UsageSummaryCard(dailyUsage = state.dailyUsage)
        }

        if (!state.hasNotificationPermission) {
            item(key = "notif_warning") {
                PermissionNoticeCard(
                    title = "Notifications Are Off",
                    description = "Allow notifications so NetPulse can show live speeds in your status bar.",
                    actionLabel = "Open Notification Settings",
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

        item(key = "app_usage") {
            AppUsageCard(
                apps = state.appUsage,
                hasUsageAccess = state.hasUsageAccess,
                onRequestAccess = { onEvent(HomeEvent.RequestUsageAccess) }
            )
        }

        item(key = "usage_log") {
            UsageLogCard(entries = state.usageLog)
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
    icon: ImageVector,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    NoticeCard(
        title = title,
        description = description,
        icon = icon,
        iconTint = MaterialTheme.colorScheme.primary,
        onAction = onAction,
        actionLabel = actionLabel,
        modifier = modifier
    )
}

@Composable
private fun BatteryNoticeCard(
    onFixClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NoticeCard(
        title = "Battery Restriction Detected",
        description = "The system may pause NetPulse when the screen is off. Set battery usage to Unrestricted for continuous monitoring.",
        icon = Icons.Rounded.BatteryAlert,
        iconTint = BatteryWarningOrange,
        onAction = onFixClick,
        actionLabel = "Allow Unrestricted",
        modifier = modifier
    )
}

@Composable
private fun NoticeCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit,
    actionLabel: String,
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
                    tint = iconTint,
                    modifier = Modifier.size(Dimens.iconSizeMd)
                )
                Spacer(modifier = Modifier.width(Dimens.sm))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(Dimens.sm))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.md))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimens.cornerPill),
                contentPadding = PaddingValues(vertical = Dimens.sm)
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
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
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.xxl),
        shape = RoundedCornerShape(Dimens.cornerMd),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isServiceEnabled) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = if (isServiceEnabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onPrimary
            }
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.PowerSettingsNew,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeMd)
        )
        Spacer(modifier = Modifier.width(Dimens.sm))
        Text(
            text = if (isServiceEnabled) "Stop Monitoring" else "Start Monitoring",
            style = MaterialTheme.typography.labelLarge
        )
    }
}
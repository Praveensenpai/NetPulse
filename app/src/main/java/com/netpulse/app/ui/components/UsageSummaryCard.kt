package com.netpulse.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.netpulse.app.core.SpeedFormatter
import com.netpulse.app.domain.model.UsageHistory
import com.netpulse.app.domain.model.UsageRange
import com.netpulse.app.ui.home.UsageWindow
import com.netpulse.app.ui.theme.Dimens

@Composable
fun UsageSummaryCard(
    history: UsageHistory,
    selectedWindow: UsageWindow,
    onSelectWindow: (UsageWindow) -> Unit,
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
                    imageVector = Icons.Rounded.DataUsage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.iconSizeSm)
                )
                Spacer(modifier = Modifier.width(Dimens.sm))
                Text(
                    text = "Data Consumption",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(Dimens.sm))
            UsageWindowChips(
                selected = selectedWindow,
                onSelect = onSelectWindow
            )
            Spacer(modifier = Modifier.height(Dimens.md))
            val range = history.rangeFor(selectedWindow)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                UsageItem(
                    label = "Mobile Data",
                    usage = SpeedFormatter.formatDataUsage(range.mobileBytes),
                    icon = Icons.Rounded.SignalCellularAlt,
                    modifier = Modifier.weight(1f)
                )
                UsageItem(
                    label = "Wi-Fi",
                    usage = SpeedFormatter.formatDataUsage(range.wifiBytes),
                    icon = Icons.Rounded.Wifi,
                    modifier = Modifier.weight(1f)
                )
                if (range.hotspotBytes > 0L) {
                    UsageItem(
                        label = "Hotspot",
                        usage = SpeedFormatter.formatDataUsage(range.hotspotBytes),
                        icon = Icons.Rounded.WifiTethering,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(Dimens.sm))
            Text(
                text = "Total ${SpeedFormatter.formatDataUsage(range.totalBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Total for the selected window. */
private fun UsageHistory.rangeFor(window: UsageWindow): UsageRange = when (window) {
    UsageWindow.TODAY -> today
    UsageWindow.WEEK -> last7Days
    UsageWindow.MONTH -> last30Days
    UsageWindow.ALL_TIME -> allTime
}

@Composable
private fun UsageWindowChips(
    selected: UsageWindow,
    onSelect: (UsageWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
    ) {
        UsageWindow.entries.forEach { window ->
            FilterChip(
                selected = window == selected,
                onClick = { onSelect(window) },
                label = {
                    Text(
                        text = window.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

@Composable
private fun UsageItem(
    label: String,
    usage: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconSizeSm)
            )
            Spacer(modifier = Modifier.width(Dimens.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(Dimens.xs))
        Text(
            text = usage,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

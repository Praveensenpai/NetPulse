package com.netpulse.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.netpulse.app.core.SpeedFormatter
import com.netpulse.app.domain.model.AppUsage
import com.netpulse.app.ui.theme.Dimens
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun AppUsageCard(
    apps: ImmutableList<AppUsage>,
    hasUsageAccess: Boolean,
    onRequestAccess: () -> Unit,
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
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.iconSizeSm)
                )
                Spacer(modifier = Modifier.width(Dimens.sm))
                Text(
                    text = "Live App Usage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(Dimens.md))
            if (!hasUsageAccess) {
                Text(
                    text = "Grant usage access to rank apps by live data consumption.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Dimens.md))
                Button(
                    onClick = onRequestAccess,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.cornerPill),
                    contentPadding = PaddingValues(vertical = Dimens.sm)
                ) {
                    Text(text = "Grant Usage Access", style = MaterialTheme.typography.labelLarge)
                }
            } else if (apps.isEmpty()) {
                Text(
                    text = "Waiting for the first sample…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val topTotal = apps.firstOrNull()?.bytesPerSec?.coerceAtLeast(1L) ?: 1L
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    apps.take(MAX_ROWS).forEach { app ->
                        AppUsageRow(app = app, maxRate = topTotal)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(
    app: AppUsage,
    maxRate: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(Dimens.sm))
            Text(
                text = SpeedFormatter.formatSpeed(app.bytesPerSec).let { "${it.value} ${it.unit}" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(Dimens.xs))
        LinearProgressIndicator(
            progress = { (app.bytesPerSec.toFloat() / maxRate.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun UsageLogCard(
    entries: ImmutableList<com.netpulse.app.domain.model.UsageLogEntry> = persistentListOf(),
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
                    imageVector = Icons.Rounded.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.iconSizeSm)
                )
                Spacer(modifier = Modifier.width(Dimens.sm))
                Text(
                    text = "Usage Log",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(Dimens.sm))
            if (entries.isEmpty()) {
                Text(
                    text = "No per-app activity recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    entries.take(MAX_LOG_ROWS).forEach { entry ->
                        LogRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(
    entry: com.netpulse.app.domain.model.UsageLogEntry,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatMinute(entry.minuteMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(Dimens.sm))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(Dimens.sm))
        Text(
            text = SpeedFormatter.formatDataUsage(entry.bytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMinute(minuteMs: Long): String {
    // Buckets are epoch millis; dividing directly yields UTC and showed times
    // hours off the device clock for non-UTC locales.
    val time = java.time.Instant.ofEpochMilli(minuteMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}

private const val MAX_ROWS = 8
private const val MAX_LOG_ROWS = 12

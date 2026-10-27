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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.netpulse.app.core.FormattedSpeed
import com.netpulse.app.ui.theme.Dimens
import com.netpulse.app.ui.theme.SpeedDownloadGreen
import com.netpulse.app.ui.theme.SpeedUploadBlue

@Composable
fun SpeedGaugeCard(
    downloadSpeed: FormattedSpeed,
    uploadSpeed: FormattedSpeed,
    modifier: Modifier = Modifier,
    showUpload: Boolean = true
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cornerLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.sm, vertical = Dimens.lg),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            SpeedMetricColumn(
                label = "Download",
                speed = downloadSpeed,
                icon = Icons.Rounded.ArrowDownward,
                accentColor = SpeedDownloadGreen,
                modifier = if (showUpload) Modifier.weight(1f) else Modifier.fillMaxWidth()
            )
            if (showUpload) {
                SpeedMetricColumn(
                    label = "Upload",
                    speed = uploadSpeed,
                    icon = Icons.Rounded.ArrowUpward,
                    accentColor = SpeedUploadBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SpeedMetricColumn(
    label: String,
    speed: FormattedSpeed,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(Dimens.iconSizeSm)
            )
            Spacer(modifier = Modifier.width(Dimens.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(Dimens.sm))
        Text(
            text = speed.value,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = speed.unit,
            style = MaterialTheme.typography.labelLarge,
            color = accentColor
        )
    }
}
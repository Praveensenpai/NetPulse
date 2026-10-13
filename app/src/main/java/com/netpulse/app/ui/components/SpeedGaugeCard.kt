package com.netpulse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.netpulse.app.core.FormattedSpeed
import com.netpulse.app.ui.theme.Dimens
import com.netpulse.app.ui.theme.SpeedDownloadGreen
import com.netpulse.app.ui.theme.SpeedUploadBlue

@Composable
fun SpeedGaugeCard(
    downloadSpeed: FormattedSpeed,
    uploadSpeed: FormattedSpeed,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cornerLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.lg),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SpeedMetricColumn(
                label = "Download",
                speed = downloadSpeed,
                icon = Icons.Rounded.ArrowDownward,
                accentColor = SpeedDownloadGreen
            )
            SpeedMetricColumn(
                label = "Upload",
                speed = uploadSpeed,
                icon = Icons.Rounded.ArrowUpward,
                accentColor = SpeedUploadBlue
            )
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
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(Dimens.xs))
        Text(
            text = speed.value,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = speed.unit,
            style = MaterialTheme.typography.labelMedium,
            color = accentColor
        )
    }
}

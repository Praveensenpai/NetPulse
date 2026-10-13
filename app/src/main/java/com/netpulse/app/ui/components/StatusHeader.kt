package com.netpulse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.netpulse.app.core.ActiveNetworkType
import com.netpulse.app.ui.theme.Dimens

@Composable
fun StatusHeader(
    isServiceActive: Boolean,
    networkType: ActiveNetworkType,
    modifier: Modifier = Modifier
) {
    val statusBg = if (isServiceActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val statusColor = if (isServiceActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val netIcon: ImageVector = when (networkType) {
        ActiveNetworkType.WIFI -> Icons.Rounded.Wifi
        ActiveNetworkType.MOBILE -> Icons.Rounded.SignalCellularAlt
        else -> Icons.Rounded.SignalCellularOff
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(statusBg, RoundedCornerShape(Dimens.cornerPill))
                .padding(horizontal = Dimens.md, vertical = Dimens.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isServiceActive) Icons.Rounded.CheckCircle else Icons.Rounded.PauseCircle,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(Dimens.iconSizeSm)
                )
                Spacer(modifier = Modifier.width(Dimens.xs))
                Text(
                    text = if (isServiceActive) "Monitoring Active" else "Monitoring Paused",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
        }

        Icon(
            imageVector = netIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconSizeMd)
        )
    }
}

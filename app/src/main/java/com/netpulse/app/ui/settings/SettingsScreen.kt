package com.netpulse.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.netpulse.app.ui.components.SettingToggleRow
import com.netpulse.app.ui.theme.Dimens

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = state.preferences

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        item(key = "status_bar_icon") {
            SettingToggleRow(
                title = "Status Bar Dynamic Icon",
                description = "Display real-time numeric speed in status bar",
                checked = prefs.showStatusBarIcon,
                onCheckedChange = { onEvent(SettingsEvent.ToggleStatusBarIcon(it)) },
                icon = Icons.Rounded.Notifications
            )
        }

        item(key = "upload_speed") {
            SettingToggleRow(
                title = "Show Upload Speed",
                description = "Display both upload and download metrics",
                checked = prefs.showUploadSpeed,
                onCheckedChange = { onEvent(SettingsEvent.ToggleUploadSpeed(it)) },
                icon = Icons.Rounded.SwapVert
            )
        }

        item(key = "bits_unit") {
            SettingToggleRow(
                title = "Unit in Bits (bps)",
                description = "Show speed in Mbps/Kbps instead of MB/s/KB/s",
                checked = prefs.unitInBits,
                onCheckedChange = { onEvent(SettingsEvent.ToggleUnitInBits(it)) },
                icon = Icons.Rounded.Speed
            )
        }

        item(key = "screen_off_pause") {
            SettingToggleRow(
                title = "Pause When Screen Off",
                description = "Halt telemetry while display is locked to conserve battery",
                checked = prefs.pauseWhenScreenOff,
                onCheckedChange = { onEvent(SettingsEvent.TogglePauseScreenOff(it)) },
                icon = Icons.Rounded.BatterySaver
            )
        }

        item(key = "floating_overlay") {
            SettingToggleRow(
                title = "Floating Mini Overlay",
                description = "Draggable on-screen telemetry pill badge",
                checked = prefs.showFloatingOverlay,
                onCheckedChange = { onEvent(SettingsEvent.ToggleFloatingOverlay(it)) },
                icon = Icons.Rounded.Layers
            )
        }

        item(key = "start_on_boot") {
            SettingToggleRow(
                title = "Start on Boot",
                description = "Automatically resume monitoring on device reboot",
                checked = prefs.startOnBoot,
                onCheckedChange = { onEvent(SettingsEvent.ToggleStartOnBoot(it)) },
                icon = Icons.Rounded.Power
            )
        }
    }
}

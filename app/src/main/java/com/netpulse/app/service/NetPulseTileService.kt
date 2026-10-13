package com.netpulse.app.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.netpulse.app.NetPulseApp
import com.netpulse.app.core.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NetPulseTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
            val prefs = NetPulseApp.instance.preferencesRepository.getPreferences()
            updateTileState(prefs.isServiceEnabled)
        }
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val repo = NetPulseApp.instance.preferencesRepository
            val current = repo.getPreferences()
            val newEnabled = !current.isServiceEnabled
            repo.setServiceEnabled(newEnabled)
            updateTileState(newEnabled)

            val serviceIntent = Intent(this@NetPulseTileService, NetPulseService::class.java).apply {
                action = if (newEnabled) Constants.ACTION_START_SERVICE else Constants.ACTION_STOP_SERVICE
            }
            if (newEnabled) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private fun updateTileState(isEnabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (isEnabled) "Active" else "Disabled"
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

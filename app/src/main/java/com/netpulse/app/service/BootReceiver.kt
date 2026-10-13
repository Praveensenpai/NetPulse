package com.netpulse.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netpulse.app.NetPulseApp
import com.netpulse.app.core.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            CoroutineScope(Dispatchers.Default).launch {
                val prefs = NetPulseApp.instance.preferencesRepository.getPreferences()
                if (prefs.isServiceEnabled && prefs.startOnBoot) {
                    val serviceIntent = Intent(context, NetPulseService::class.java).apply {
                        this.action = Constants.ACTION_START_SERVICE
                    }
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }
}

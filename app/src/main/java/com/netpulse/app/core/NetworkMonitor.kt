package com.netpulse.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.NetworkInterface

enum class ActiveNetworkType {
    WIFI,
    MOBILE,
    ETHERNET,
    OTHER,
    NONE
}

data class NetworkConnection(
    val isConnected: Boolean,
    val type: ActiveNetworkType,
    val isTethering: Boolean = false
)

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun observeConnection(): Flow<NetworkConnection> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentConnection())
            }

            override fun onLost(network: Network) {
                trySend(getCurrentConnection())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(getCurrentConnection())
            }
        }

        val tetherReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(getCurrentConnection())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)
        val filter = IntentFilter(ACTION_TETHER_STATE_CHANGED)
        context.registerReceiver(tetherReceiver, filter)
        trySend(getCurrentConnection())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
            try {
                context.unregisterReceiver(tetherReceiver)
            } catch (_: Exception) {
            }
        }
    }

    fun isTetheringActive(): Boolean = getActiveTetheredInterfaces().isNotEmpty()

    fun getActiveTetheredInterfaces(): List<String> {
        val tethered = mutableListOf<String>()
        try {
            val sticky = context.registerReceiver(null, IntentFilter(ACTION_TETHER_STATE_CHANGED))
            val list = sticky?.getStringArrayListExtra(EXTRA_ACTIVE_TETHER)
                ?: sticky?.getStringArrayListExtra(EXTRA_ACTIVE_LOCAL_ONLY)
            if (list != null) {
                tethered.addAll(list)
            }
        } catch (_: Exception) {
        }
        if (tethered.isEmpty()) {
            try {
                val ifaces = NetworkInterface.getNetworkInterfaces()
                if (ifaces != null) {
                    for (iface in ifaces) {
                        val name = iface.name
                        if (iface.isUp && isTetherInterfaceName(name)) {
                            tethered.add(name)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return tethered
    }

    private fun isTetherInterfaceName(name: String): Boolean =
        name.startsWith("swlan") ||
            name.startsWith("ap") ||
            name.startsWith("softap") ||
            name.startsWith("rndis")

    fun getCurrentConnection(): NetworkConnection {
        val isTether = isTetheringActive()
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkConnection(
            isConnected = false,
            type = ActiveNetworkType.NONE,
            isTethering = isTether
        )
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkConnection(isConnected = false, type = ActiveNetworkType.NONE, isTethering = isTether)

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ActiveNetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ActiveNetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ActiveNetworkType.ETHERNET
            else -> ActiveNetworkType.OTHER
        }

        return NetworkConnection(isConnected = hasInternet, type = type, isTethering = isTether)
    }

    companion object {
        private const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
        private const val EXTRA_ACTIVE_TETHER = "tetherArray"
        private const val EXTRA_ACTIVE_LOCAL_ONLY = "activeArray"
    }
}

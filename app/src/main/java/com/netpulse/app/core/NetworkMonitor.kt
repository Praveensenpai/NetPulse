package com.netpulse.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class ActiveNetworkType {
    WIFI,
    MOBILE,
    ETHERNET,
    OTHER,
    NONE
}

data class NetworkConnection(
    val isConnected: Boolean,
    val type: ActiveNetworkType
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

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)
        trySend(getCurrentConnection())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    fun getCurrentConnection(): NetworkConnection {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkConnection(
            isConnected = false,
            type = ActiveNetworkType.NONE
        )
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkConnection(isConnected = false, type = ActiveNetworkType.NONE)

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ActiveNetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ActiveNetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ActiveNetworkType.ETHERNET
            else -> ActiveNetworkType.OTHER
        }

        return NetworkConnection(isConnected = hasInternet, type = type)
    }
}

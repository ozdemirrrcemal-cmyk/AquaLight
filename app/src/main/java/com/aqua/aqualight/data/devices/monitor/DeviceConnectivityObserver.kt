package com.aqua.aqualight.data.devices.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Observes whether the phone currently has a usable local network path for LAN devices.
 */
class DeviceConnectivityObserver(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun observeLocalNetworkAvailable(): Flow<Boolean> = callbackFlow {
        val localNetworks = ConcurrentHashMap.newKeySet<Network>()

        fun publishAvailability() {
            trySend(localNetworks.isNotEmpty())
        }

        fun updateNetwork(
            network: Network,
            capabilities: NetworkCapabilities?
        ) {
            if (DeviceLocalTransportPolicy.hasLocalTransport(capabilities)) {
                localNetworks.add(network)
            } else {
                localNetworks.remove(network)
            }
            publishAvailability()
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // The request itself only matches Wi-Fi or Ethernet. Avoid a synchronous
                // capabilities lookup here: Android documents that it can return stale data
                // from inside a NetworkCallback.
                localNetworks.add(network)
                publishAvailability()
            }

            override fun onLost(network: Network) {
                localNetworks.remove(network)
                publishAvailability()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                updateNetwork(
                    network = network,
                    capabilities = networkCapabilities
                )
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        connectivityManager.allNetworks.forEach { network ->
            if (
                DeviceLocalTransportPolicy.hasLocalTransport(
                    connectivityManager.getNetworkCapabilities(network)
                )
            ) {
                localNetworks.add(network)
            }
        }
        publishAvailability()

        awaitClose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }

    fun isLocalNetworkAvailable(): Boolean {
        return connectivityManager.allNetworks.any { network ->
            DeviceLocalTransportPolicy.hasLocalTransport(
                connectivityManager.getNetworkCapabilities(network)
            )
        }
    }
}

internal object DeviceLocalTransportPolicy {

    fun hasLocalTransport(capabilities: NetworkCapabilities?): Boolean {
        return capabilities != null && isLocalTransport(
            hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        )
    }

    fun isLocalTransport(
        hasWifi: Boolean,
        hasEthernet: Boolean
    ): Boolean = hasWifi || hasEthernet
}

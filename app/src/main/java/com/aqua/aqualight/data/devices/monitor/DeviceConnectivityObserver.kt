package com.aqua.aqualight.data.devices.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Observes the concrete Android Network used for local AquaLight traffic.
 *
 * A Boolean Wi-Fi flag is not enough when VPN, cellular, Wi-Fi and Ethernet coexist. Discovery and
 * runtime sockets query [currentLocalNetwork] so each connection is bound to the same canonical
 * local path that drives Online/Offline decisions.
 */
class DeviceConnectivityObserver(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val generation = AtomicLong(0L)
    private val currentPath = AtomicReference<DeviceLocalNetworkPath?>(initialActivePath())

    fun observeLocalNetworkPath(): Flow<DeviceLocalNetworkPath?> = callbackFlow {
        val localNetworks = ConcurrentHashMap.newKeySet<Network>()
        val capabilitiesByNetwork = ConcurrentHashMap<Network, NetworkCapabilities>()
        val linkPropertiesByNetwork = ConcurrentHashMap<Network, LinkProperties>()

        fun publishPath() {
            val previous = currentPath.get()
            val selected = selectPreferredNetwork(
                networks = localNetworks,
                capabilitiesByNetwork = capabilitiesByNetwork,
                previousNetwork = previous?.network
            )
            val next = selected?.let { network ->
                val capabilities = capabilitiesByNetwork[network]
                val linkProperties = linkPropertiesByNetwork[network]
                val pathChanged = previous == null ||
                    previous.network != network ||
                    previous.linkProperties != linkProperties
                val nextGeneration = if (pathChanged) {
                    generation.incrementAndGet()
                } else {
                    requireNotNull(previous).generation
                }
                DeviceLocalNetworkPath(
                    network = network,
                    capabilities = capabilities,
                    linkProperties = linkProperties,
                    generation = nextGeneration
                )
            }
            currentPath.set(next)
            trySend(next)
        }

        fun updateNetwork(
            network: Network,
            capabilities: NetworkCapabilities?
        ) {
            if (DeviceLocalTransportPolicy.hasLocalTransport(capabilities)) {
                localNetworks.add(network)
                if (capabilities != null) capabilitiesByNetwork[network] = capabilities
            } else {
                localNetworks.remove(network)
                capabilitiesByNetwork.remove(network)
                linkPropertiesByNetwork.remove(network)
            }
            publishPath()
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // The request itself matches only Wi-Fi or Ethernet. Capabilities arrive through
                // onCapabilitiesChanged; accepting the network here avoids a false Offline pulse.
                localNetworks.add(network)
                publishPath()
            }

            override fun onLost(network: Network) {
                localNetworks.remove(network)
                capabilitiesByNetwork.remove(network)
                linkPropertiesByNetwork.remove(network)
                publishPath()
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

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) {
                if (network in localNetworks) {
                    linkPropertiesByNetwork[network] = linkProperties
                    publishPath()
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        connectivityManager.activeNetwork?.let { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (DeviceLocalTransportPolicy.hasLocalTransport(capabilities)) {
                localNetworks.add(network)
                if (capabilities != null) capabilitiesByNetwork[network] = capabilities
                connectivityManager.getLinkProperties(network)?.let { linkProperties ->
                    linkPropertiesByNetwork[network] = linkProperties
                }
            }
        }
        publishPath()

        awaitClose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            currentPath.set(null)
        }
    }

    fun observeLocalNetworkAvailable(): Flow<Boolean> {
        return observeLocalNetworkPath()
            .map { path -> path != null }
            .distinctUntilChanged()
    }

    fun currentLocalNetwork(): Network? {
        currentPath.get()?.network?.let { return it }

        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return activeNetwork.takeIf {
            DeviceLocalTransportPolicy.hasLocalTransport(capabilities)
        }
    }

    fun currentLocalNetworkGeneration(): Long = currentPath.get()?.generation ?: 0L

    fun isLocalNetworkAvailable(): Boolean = currentLocalNetwork() != null

    private fun initialActivePath(): DeviceLocalNetworkPath? {
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (!DeviceLocalTransportPolicy.hasLocalTransport(capabilities)) return null
        return DeviceLocalNetworkPath(
            network = network,
            capabilities = capabilities,
            linkProperties = connectivityManager.getLinkProperties(network),
            generation = generation.incrementAndGet()
        )
    }

    private fun selectPreferredNetwork(
        networks: Set<Network>,
        capabilitiesByNetwork: Map<Network, NetworkCapabilities>,
        previousNetwork: Network?
    ): Network? {
        if (previousNetwork != null && previousNetwork in networks) {
            return previousNetwork
        }

        return networks.firstOrNull { network ->
            capabilitiesByNetwork[network]
                ?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        } ?: networks.firstOrNull { network ->
            capabilitiesByNetwork[network]
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: networks.firstOrNull()
    }
}

data class DeviceLocalNetworkPath(
    val network: Network,
    val capabilities: NetworkCapabilities?,
    val linkProperties: LinkProperties?,
    val generation: Long
)

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

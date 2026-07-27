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
import kotlinx.coroutines.flow.distinctUntilChangedBy
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
        val tracker = LocalNetworkPathTracker(
            connectivityManager = connectivityManager,
            generation = generation,
            currentPath = currentPath,
            publish = { path -> trySend(path) }
        )
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                tracker.onAvailable(network)
            }

            override fun onLost(network: Network) {
                tracker.onLost(network)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                tracker.onCapabilitiesChanged(network, networkCapabilities)
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) {
                tracker.onLinkPropertiesChanged(network, linkProperties)
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)
        tracker.seedActiveNetwork()

        awaitClose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            currentPath.set(null)
        }
    }.distinctUntilChangedBy { path -> path?.generation }

    fun observeLocalNetworkAvailable(): Flow<Boolean> {
        return observeLocalNetworkPath()
            .map { path -> path != null }
            .distinctUntilChanged()
    }

    fun currentLocalNetwork(): Network? {
        val cachedPath = currentPath.get()
        val cachedNetwork = cachedPath?.network?.takeIf(::hasLocalTransport)
        if (cachedPath != null && cachedNetwork == null) {
            currentPath.compareAndSet(cachedPath, null)
        }
        val activeNetwork = connectivityManager.activeNetwork?.takeIf(::hasLocalTransport)
        return cachedNetwork ?: activeNetwork
    }

    fun currentLocalNetworkGeneration(): Long = currentPath.get()?.generation ?: 0L

    fun isLocalNetworkAvailable(): Boolean = currentLocalNetwork() != null

    private fun hasLocalTransport(network: Network): Boolean {
        return DeviceLocalTransportPolicy.hasLocalTransport(
            connectivityManager.getNetworkCapabilities(network)
        )
    }

    private fun initialActivePath(): DeviceLocalNetworkPath? {
        return connectivityManager.activeNetwork
            ?.takeIf(::hasLocalTransport)
            ?.let { network ->
                DeviceLocalNetworkPath(
                    network = network,
                    capabilities = connectivityManager.getNetworkCapabilities(network),
                    linkProperties = connectivityManager.getLinkProperties(network),
                    generation = generation.incrementAndGet()
                )
            }
    }
}

private class LocalNetworkPathTracker(
    private val connectivityManager: ConnectivityManager,
    private val generation: AtomicLong,
    private val currentPath: AtomicReference<DeviceLocalNetworkPath?>,
    private val publish: (DeviceLocalNetworkPath?) -> Unit
) {
    private val localNetworks = ConcurrentHashMap.newKeySet<Network>()
    private val capabilitiesByNetwork = ConcurrentHashMap<Network, NetworkCapabilities>()
    private val linkPropertiesByNetwork = ConcurrentHashMap<Network, LinkProperties>()

    fun onAvailable(network: Network) {
        // The request itself matches only Wi-Fi or Ethernet. Capabilities arrive through
        // onCapabilitiesChanged; accepting the network here avoids a false Offline pulse.
        localNetworks.add(network)
        publishPath()
    }

    fun onLost(network: Network) {
        localNetworks.remove(network)
        capabilitiesByNetwork.remove(network)
        linkPropertiesByNetwork.remove(network)
        publishPath()
    }

    fun onCapabilitiesChanged(
        network: Network,
        capabilities: NetworkCapabilities
    ) {
        updateNetwork(network, capabilities)
    }

    fun onLinkPropertiesChanged(
        network: Network,
        linkProperties: LinkProperties
    ) {
        if (network in localNetworks) {
            linkPropertiesByNetwork[network] = linkProperties
            publishPath()
        }
    }

    fun seedActiveNetwork() {
        connectivityManager.activeNetwork?.let { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (DeviceLocalTransportPolicy.hasLocalTransport(capabilities)) {
                localNetworks.add(network)
                capabilities?.let { capabilitiesByNetwork[network] = it }
                connectivityManager.getLinkProperties(network)?.let { linkProperties ->
                    linkPropertiesByNetwork[network] = linkProperties
                }
            }
        }
        publishPath()
    }

    private fun updateNetwork(
        network: Network,
        capabilities: NetworkCapabilities?
    ) {
        if (DeviceLocalTransportPolicy.hasLocalTransport(capabilities)) {
            localNetworks.add(network)
            capabilities?.let { capabilitiesByNetwork[network] = it }
        } else {
            localNetworks.remove(network)
            capabilitiesByNetwork.remove(network)
            linkPropertiesByNetwork.remove(network)
        }
        publishPath()
    }

    private fun publishPath() {
        val previous = currentPath.get()
        val selected = selectPreferredNetwork(previous?.network)
        val next = selected?.let { network -> buildPath(previous, network) }
        currentPath.set(next)
        publish(next)
    }

    private fun buildPath(
        previous: DeviceLocalNetworkPath?,
        network: Network
    ): DeviceLocalNetworkPath {
        val capabilities = capabilitiesByNetwork[network]
        val linkProperties = linkPropertiesByNetwork[network]
        val pathChanged = previous == null ||
            previous.network != network ||
            hasRouteChanged(previous, network, linkProperties)
        val nextGeneration = if (pathChanged) {
            generation.incrementAndGet()
        } else {
            requireNotNull(previous).generation
        }

        return DeviceLocalNetworkPath(
            network = network,
            capabilities = capabilities,
            linkProperties = linkProperties
                ?: previous?.takeIf { path -> path.network == network }?.linkProperties,
            generation = nextGeneration
        )
    }

    private fun hasRouteChanged(
        previous: DeviceLocalNetworkPath,
        network: Network,
        linkProperties: LinkProperties?
    ): Boolean {
        val previousLinkProperties = previous.linkProperties
        return previous.network == network &&
            previousLinkProperties != null &&
            linkProperties != null &&
            previousLinkProperties != linkProperties
    }

    private fun selectPreferredNetwork(previousNetwork: Network?): Network? {
        return when {
            previousNetwork != null && previousNetwork in localNetworks -> previousNetwork
            else -> ethernetNetwork() ?: wifiNetwork() ?: localNetworks.firstOrNull()
        }
    }

    private fun ethernetNetwork(): Network? {
        return localNetworks.firstOrNull { network ->
            capabilitiesByNetwork[network]
                ?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }
    }

    private fun wifiNetwork(): Network? {
        return localNetworks.firstOrNull { network ->
            capabilitiesByNetwork[network]
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
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

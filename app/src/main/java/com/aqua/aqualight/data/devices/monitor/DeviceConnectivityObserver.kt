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
 * A Boolean Wi-Fi flag is not enough when VPN, blocked policy, cellular, Wi-Fi and Ethernet
 * coexist. Discovery and runtime sockets query [currentLocalNetwork] so each connection is bound to
 * the same canonical local path that drives Online/Offline decisions.
 */
class DeviceConnectivityObserver(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val generation = AtomicLong(0L)
    private val blockedNetworks = ConcurrentHashMap.newKeySet<Network>()
    private val currentPath = AtomicReference<DeviceLocalNetworkPath?>(initialActivePath())

    fun observeLocalNetworkPath(): Flow<DeviceLocalNetworkPath?> = callbackFlow {
        val tracker = LocalNetworkPathTracker(
            connectivityManager = connectivityManager,
            generation = generation,
            blockedNetworks = blockedNetworks,
            currentPath = currentPath,
            publish = { path -> trySend(path) }
        )
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                tracker.updateAvailability(network, available = true)
            }

            override fun onLost(network: Network) {
                tracker.updateAvailability(network, available = false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                tracker.updateCapabilities(network, networkCapabilities)
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) {
                tracker.updateLinkProperties(network, linkProperties)
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                tracker.updateBlockedStatus(network, blocked)
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)
        tracker.seedActiveNetwork()

        awaitClose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            blockedNetworks.clear()
            currentPath.set(null)
        }
    }.distinctUntilChangedBy { path -> path?.generation }

    fun observeLocalNetworkAvailable(): Flow<Boolean> {
        return observeLocalNetworkPath()
            .map { path -> path != null }
            .distinctUntilChanged()
    }

    fun currentLocalNetwork(): Network? {
        // NetworkCallback owns the canonical Wi-Fi/Ethernet set. Trusting its selected path avoids
        // a false unavailable result while Android is still delivering the first capabilities
        // callback after onAvailable. Loss or a blocked-status callback removes the cached path
        // synchronously.
        val callbackNetwork = currentPath.get()
            ?.network
            ?.takeUnless { network -> blockedNetworks.contains(network) }
        val activeNetwork = connectivityManager.activeNetwork
            ?.takeUnless { network -> blockedNetworks.contains(network) }
            ?.takeIf(::hasLocalTransport)
        return callbackNetwork ?: activeNetwork
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
    private val blockedNetworks: MutableSet<Network>,
    private val currentPath: AtomicReference<DeviceLocalNetworkPath?>,
    private val publish: (DeviceLocalNetworkPath?) -> Unit
) {
    private val localNetworks = ConcurrentHashMap.newKeySet<Network>()
    private val capabilitiesByNetwork = ConcurrentHashMap<Network, NetworkCapabilities>()
    private val linkPropertiesByNetwork = ConcurrentHashMap<Network, LinkProperties>()

    fun updateAvailability(network: Network, available: Boolean) {
        if (available) {
            // The request itself matches only Wi-Fi or Ethernet. Capabilities arrive through
            // onCapabilitiesChanged; accepting the network here avoids a false Offline pulse.
            localNetworks.add(network)
        } else {
            localNetworks.remove(network)
            blockedNetworks.remove(network)
            capabilitiesByNetwork.remove(network)
            linkPropertiesByNetwork.remove(network)
        }
        publishPath()
    }

    fun updateCapabilities(
        network: Network,
        capabilities: NetworkCapabilities
    ) {
        if (DeviceLocalTransportPolicy.hasLocalTransport(capabilities)) {
            localNetworks.add(network)
            capabilitiesByNetwork[network] = capabilities
        } else {
            localNetworks.remove(network)
            blockedNetworks.remove(network)
            capabilitiesByNetwork.remove(network)
            linkPropertiesByNetwork.remove(network)
        }
        publishPath()
    }

    fun updateLinkProperties(
        network: Network,
        linkProperties: LinkProperties
    ) {
        if (network in localNetworks) {
            linkPropertiesByNetwork[network] = linkProperties
            publishPath()
        }
    }

    fun updateBlockedStatus(network: Network, blocked: Boolean) {
        if (blocked) {
            blockedNetworks.add(network)
        } else {
            blockedNetworks.remove(network)
        }
        publishPath()
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
        val routeChanged = previous
            ?.takeIf { path -> path.network == network }
            ?.linkProperties
            ?.let { previousLinkProperties ->
                linkProperties != null && previousLinkProperties != linkProperties
            } == true
        val pathChanged = previous == null ||
            previous.network != network ||
            routeChanged
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

    private fun selectPreferredNetwork(previousNetwork: Network?): Network? {
        return when {
            previousNetwork != null && isUsable(previousNetwork) -> previousNetwork
            else -> networkWithTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                ?: networkWithTransport(NetworkCapabilities.TRANSPORT_WIFI)
                ?: localNetworks.firstOrNull(::isUsable)
        }
    }

    private fun networkWithTransport(transport: Int): Network? {
        return localNetworks.firstOrNull { network ->
            isUsable(network) &&
                capabilitiesByNetwork[network]?.hasTransport(transport) == true
        }
    }

    private fun isUsable(network: Network): Boolean {
        return DeviceLocalTransportPolicy.isUsableLocalPath(
            hasLocalTransport = network in localNetworks,
            isBlocked = network in blockedNetworks
        )
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
            hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            isNotVpn = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        )
    }

    fun isLocalTransport(
        hasWifi: Boolean,
        hasEthernet: Boolean,
        isNotVpn: Boolean
    ): Boolean = isNotVpn && (hasWifi || hasEthernet)

    fun isUsableLocalPath(
        hasLocalTransport: Boolean,
        isBlocked: Boolean
    ): Boolean = hasLocalTransport && !isBlocked
}

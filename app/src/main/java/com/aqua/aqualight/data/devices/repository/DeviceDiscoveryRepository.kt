package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoveryRefreshSender
import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoverySupervisor
import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoveryUdpScanner
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
import com.aqua.aqualight.data.devices.monitor.DevicePresenceSupervisor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/** Repository boundary for UDP discovery. */
class DeviceDiscoveryRepository(
    private val discoverySupervisor: AqlDiscoverySupervisor = AqlDiscoverySupervisor(),
    private val presenceSupervisor: DevicePresenceSupervisor = DevicePresenceSupervisor()
) {
    val devices: Flow<List<DeviceSnapshot>> = presenceSupervisor.devices

    fun start(scope: CoroutineScope): Job =
        discoverySupervisor.start(scope) { discovered ->
            presenceSupervisor.onDiscoveredDevice(discovered)
        }

    fun restartScanner(localNetworkAvailable: Boolean) {
        discoverySupervisor.restartScanner(localNetworkAvailable)
    }

    suspend fun refreshNow(): AqlDiscoveryRefreshSender.SendResult =
        discoverySupervisor.sendRefreshNow()

    suspend fun refreshForegroundBurst(): List<AqlDiscoveryRefreshSender.SendResult> =
        discoverySupervisor.sendForegroundRefreshBurst()

    fun reevaluatePresence(localNetworkAvailable: Boolean = true) {
        presenceSupervisor.reevaluate(localNetworkAvailable = localNetworkAvailable)
    }

    companion object {
        fun withConnectivityObserver(
            connectivityObserver: DeviceConnectivityObserver
        ): DeviceDiscoveryRepository {
            val networkProvider = connectivityObserver::currentLocalNetwork
            return DeviceDiscoveryRepository(
                discoverySupervisor = AqlDiscoverySupervisor(
                    scanner = AqlDiscoveryUdpScanner(
                        networkProvider = networkProvider,
                        requireLocalNetwork = true
                    ),
                    refreshSender = AqlDiscoveryRefreshSender(
                        networkProvider = networkProvider,
                        requireLocalNetwork = true
                    ),
                    initialScanningActive = connectivityObserver.isLocalNetworkAvailable()
                )
            )
        }
    }
}

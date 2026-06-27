package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoveryRefreshSender
import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoverySupervisor
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.monitor.DevicePresenceSupervisor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/**
 * Repository boundary for UDP discovery.
 *
 * UI code should depend on this repository instead of constructing UDP sockets directly.
 */
class DeviceDiscoveryRepository(
    private val discoverySupervisor: AqlDiscoverySupervisor = AqlDiscoverySupervisor(),
    private val presenceSupervisor: DevicePresenceSupervisor = DevicePresenceSupervisor()
) {

    val devices: Flow<List<DeviceSnapshot>> = presenceSupervisor.devices

    fun start(scope: CoroutineScope): Job =
        discoverySupervisor.start(scope) { discovered ->
            presenceSupervisor.onDiscoveredDevice(discovered)
        }

    suspend fun refreshNow(): AqlDiscoveryRefreshSender.SendResult =
        discoverySupervisor.sendRefreshNow()

    suspend fun refreshForegroundBurst(): List<AqlDiscoveryRefreshSender.SendResult> =
        discoverySupervisor.sendForegroundRefreshBurst()

    fun reevaluatePresence(localNetworkAvailable: Boolean = true) {
        presenceSupervisor.reevaluate(localNetworkAvailable = localNetworkAvailable)
    }
}

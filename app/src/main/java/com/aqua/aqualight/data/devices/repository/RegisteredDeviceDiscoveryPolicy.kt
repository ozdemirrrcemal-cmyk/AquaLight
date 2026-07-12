package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid

/**
 * Limits LAN discovery to live updates for devices that are already present in
 * the owner-scoped registry.
 *
 * UDP discovery is not a registration or ownership source. Unknown devices are
 * intentionally ignored here and remain available only through the explicit
 * Add Device provisioning flows.
 */
internal object RegisteredDeviceDiscoveryPolicy {

    fun filterRegisteredUpdates(
        registeredDeviceUids: Set<DeviceUid>,
        discoveredDevices: Iterable<DeviceSnapshot>
    ): List<DeviceSnapshot> {
        if (registeredDeviceUids.isEmpty()) return emptyList()

        return discoveredDevices.filter { discovered ->
            discovered.deviceUid in registeredDeviceUids
        }
    }
}

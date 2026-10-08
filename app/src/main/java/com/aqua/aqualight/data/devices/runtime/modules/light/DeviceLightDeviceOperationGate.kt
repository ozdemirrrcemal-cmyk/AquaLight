package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-device serialization boundary shared by Light refresh and mutations.
 * Cancel optional post-ACK work before a newer user command waits for the gate.
 */
internal class DeviceLightDeviceOperationGate(
    private val cancelOptionalReconciliation: (DeviceUid) -> Unit = {}
) {
    private val locks = ConcurrentHashMap<DeviceUid, Mutex>()

    suspend fun <T> withDevice(deviceUid: DeviceUid, block: suspend () -> T): T =
        locks.computeIfAbsent(deviceUid) { Mutex() }.withLock { block() }

    suspend fun <T> withMutation(deviceUid: DeviceUid, block: suspend () -> T): T {
        cancelOptionalReconciliation(deviceUid)
        return withDevice(deviceUid, block)
    }
}

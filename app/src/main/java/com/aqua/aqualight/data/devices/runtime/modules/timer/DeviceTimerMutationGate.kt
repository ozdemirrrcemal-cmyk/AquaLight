package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes Timer mutations per device because firmware owns one device-wide revision. */
internal class DeviceTimerMutationGate {
    private val locks = ConcurrentHashMap<DeviceUid, Mutex>()

    suspend fun <T> withDevice(deviceUid: DeviceUid, block: suspend () -> T): T =
        lock(deviceUid).withLock { block() }

    private fun lock(deviceUid: DeviceUid): Mutex =
        locks.computeIfAbsent(deviceUid) { Mutex() }
}

package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid

const val DEVICE_RUNTIME_DEFAULT_TIMEOUT_MILLIS = 8_000L
const val DEVICE_RUNTIME_MIN_TIMEOUT_MILLIS = 1_000L
const val DEVICE_RUNTIME_MAX_TIMEOUT_MILLIS = 30_000L

/** Single command-execution boundary shared by every typed runtime module. */
interface DeviceRuntimeCommandGateway {
    suspend fun <T> execute(
        deviceUid: DeviceUid,
        command: DeviceRuntimeCommand<T>,
        timeoutMillis: Long = DEVICE_RUNTIME_DEFAULT_TIMEOUT_MILLIS
    ): DeviceRuntimeCommandOutcome<T>
}

package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid

/** Single command-execution boundary shared by every typed runtime module. */
interface DeviceRuntimeCommandGateway {
    suspend fun <T> execute(
        deviceUid: DeviceUid,
        command: DeviceRuntimeCommand<T>,
        timeoutMillis: Long = DeviceRuntimeCommandExecutor.DEFAULT_TIMEOUT_MILLIS
    ): DeviceRuntimeCommandOutcome<T>
}

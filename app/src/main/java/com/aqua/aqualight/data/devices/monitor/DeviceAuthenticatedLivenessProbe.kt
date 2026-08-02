package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

/** A device is live only after the correlated broker parses a successful firmware response. */
internal class DeviceAuthenticatedLivenessProbe(
    private val requestStatus: suspend (DeviceUid) ->
        DeviceRuntimeCommandOutcome<*>,
    private val recordProof: (DeviceUid, DeviceRuntimeConnectionGeneration) -> Boolean
) {
    suspend fun execute(deviceUid: DeviceUid): Boolean {
        val success = requestStatus(deviceUid) as?
            DeviceRuntimeCommandOutcome.Success<*> ?: return false
        return recordProof(deviceUid, success.generation)
    }
}

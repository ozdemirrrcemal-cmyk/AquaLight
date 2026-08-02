package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/** A device is live only after the correlated broker parses a successful firmware response. */
internal class DeviceAuthenticatedLivenessProbe(
    private val requestStatus: suspend (DeviceUid) ->
        DeviceRuntimeCommandOutcome<*>,
    private val recordProof: (DeviceUid) -> Unit
) {
    suspend fun execute(deviceUid: DeviceUid): Boolean {
        return if (requestStatus(deviceUid) is DeviceRuntimeCommandOutcome.Success) {
            recordProof(deviceUid)
            true
        } else {
            false
        }
    }
}

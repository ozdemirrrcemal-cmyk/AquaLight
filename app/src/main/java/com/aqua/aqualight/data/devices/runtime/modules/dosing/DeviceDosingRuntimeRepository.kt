package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.dosing.v1.DeviceDosingV1GlobalStatus
import com.aqua.aqualight.data.devices.dosing.v1.DeviceDosingV1Repository
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/**
 * Runtime-bootstrap facade over the pinned Dosing v1 transport contract.
 *
 * This class deliberately owns no parallel Dosing state store. The production Dosing v1 state
 * owner remains the single authoritative mutable owner for channel/progress/calibration state.
 */
class DeviceDosingRuntimeRepository(
    gateway: DeviceRuntimeCommandGateway
) {
    private val delegate = DeviceDosingV1Repository(gateway)

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1GlobalStatus> =
        delegate.requestGlobalStatus(deviceUid)
}

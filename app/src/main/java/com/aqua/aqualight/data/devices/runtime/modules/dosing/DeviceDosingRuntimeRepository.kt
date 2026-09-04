package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.dosing.v1.DeviceDosingV1GlobalStatus
import com.aqua.aqualight.data.devices.dosing.v1.DeviceDosingV1Repository
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Runtime-bootstrap facade over the pinned Dosing v1 transport contract.
 *
 * This class deliberately owns no parallel Dosing state store. The production Dosing v1 state
 * owner remains the single authoritative mutable owner for channel/progress/calibration state.
 * [runtimeReadyGenerations] contains only session-readiness generation proof, never firmware state.
 */
class DeviceDosingRuntimeRepository(
    gateway: DeviceRuntimeCommandGateway
) {
    private val delegate = DeviceDosingV1Repository(gateway)
    private val _runtimeReadyGenerations =
        MutableStateFlow<Map<DeviceUid, DeviceRuntimeConnectionGeneration>>(emptyMap())

    val runtimeReadyGenerations: StateFlow<Map<DeviceUid, DeviceRuntimeConnectionGeneration>> =
        _runtimeReadyGenerations.asStateFlow()

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1GlobalStatus> =
        delegate.requestGlobalStatus(deviceUid)

    internal fun markRuntimeReady(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ) {
        _runtimeReadyGenerations.update { current -> current + (deviceUid to generation) }
    }

    internal fun clearRuntimeState(deviceUid: DeviceUid) {
        _runtimeReadyGenerations.update { current -> current - deviceUid }
    }
}

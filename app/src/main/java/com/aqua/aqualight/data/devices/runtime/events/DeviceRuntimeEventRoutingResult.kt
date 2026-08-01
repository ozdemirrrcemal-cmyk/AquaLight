package com.aqua.aqualight.data.devices.runtime.events

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

sealed interface DeviceRuntimeEventRoutingResult {
    data class Routed(
        val event: DeviceRuntimeTypedEvent
    ) : DeviceRuntimeEventRoutingResult

    data class Unmatched(
        val module: String,
        val action: String
    ) : DeviceRuntimeEventRoutingResult

    data class Stale(
        val activeGeneration: DeviceRuntimeConnectionGeneration?,
        val receivedGeneration: DeviceRuntimeConnectionGeneration
    ) : DeviceRuntimeEventRoutingResult

    data class Malformed(
        val field: String
    ) : DeviceRuntimeEventRoutingResult
}

package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.dosing.v1.DeviceDosingV1Contract
import com.aqua.aqualight.data.devices.model.SUPPORTED_DEVICE_API_VERSION
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeContract

/**
 * Android-owned registry of firmware contracts this build can actually consume.
 *
 * Target firmware is admitted only when every required contract is implemented by this app.
 * Optional target contracts are deliberately non-blocking.
 */
internal object DeviceFirmwareContractRegistry {

    private val supportedRequiredDomains = setOf(
        DeviceLightRuntimeContract.SCHEMA,
        DeviceTimerRuntimeContract.SCHEMA,
        DeviceDosingV1Contract.SCHEMA,
        DeviceCoolingV1Contract.SCHEMA
    )

    fun requireTargetCompatible(contracts: DeviceFirmwareTargetContracts) {
        val unsupported = linkedSetOf<String>()

        if (contracts.wsSchema != AqlWsContract.SCHEMA) {
            unsupported += contracts.wsSchema
        }
        if (contracts.wsProtocolVersion != AqlWsContract.PROTOCOL_VERSION) {
            unsupported += "wsProtocolVersion=${contracts.wsProtocolVersion}"
        }
        if (contracts.deviceApiVersion != SUPPORTED_DEVICE_API_VERSION) {
            unsupported += "deviceApiVersion=${contracts.deviceApiVersion}"
        }
        unsupported += contracts.requiredDomains.filterNot(supportedRequiredDomains::contains)

        if (unsupported.isNotEmpty()) {
            throw DeviceFirmwareApplicationUpdateRequiredException(unsupported)
        }
    }
}

internal class DeviceFirmwareApplicationUpdateRequiredException(
    val unsupportedContracts: Set<String>
) : IllegalStateException(
    "Target firmware requires contracts unsupported by this app build: " +
        unsupportedContracts.sorted().joinToString()
)

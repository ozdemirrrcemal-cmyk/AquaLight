package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFragment
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataReduction
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandFactory
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage

internal enum class DeviceRuntimeMetadataBootstrapKind(
    val module: String,
    val action: String
) {
    IDENTITY(AqlWsContract.MODULE_DEVICE, AqlWsContract.ACTION_DEVICE_IDENTITY_GET),
    CAPABILITIES(AqlWsContract.MODULE_DEVICE, AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET),
    STATUS_MODULES(AqlWsContract.MODULE_DEVICE, AqlWsContract.ACTION_DEVICE_STATUS_GET);

    fun command(): AqlWsOutgoingMessage.Command = when (this) {
        IDENTITY -> AqlWsCommandFactory.deviceIdentity()
        CAPABILITIES -> AqlWsCommandFactory.deviceCapabilities()
        STATUS_MODULES -> AqlWsCommandFactory.deviceStatus()
    }

    fun accepts(fragment: DeviceRuntimeMetadataFragment): Boolean = when (this) {
        IDENTITY -> fragment is DeviceRuntimeMetadataFragment.Identity
        CAPABILITIES -> fragment is DeviceRuntimeMetadataFragment.Capabilities
        STATUS_MODULES -> fragment is DeviceRuntimeMetadataFragment.Modules
    }
}

internal data class DeviceRuntimeMetadataBootstrapTicket(
    val deviceUid: DeviceUid,
    val generation: DeviceRuntimeMetadataGeneration,
    val kind: DeviceRuntimeMetadataBootstrapKind,
    val requestId: String
)

internal sealed interface DeviceRuntimeMetadataBootstrapClaim {
    data object Unmatched : DeviceRuntimeMetadataBootstrapClaim
    data class Accepted(val ticket: DeviceRuntimeMetadataBootstrapTicket) :
        DeviceRuntimeMetadataBootstrapClaim
    data class Rejected(val state: DeviceRuntimeMetadataGenerationState.Rejected) :
        DeviceRuntimeMetadataBootstrapClaim
}

internal sealed interface DeviceRuntimeMetadataBootstrapProcessing {
    data object Unmatched : DeviceRuntimeMetadataBootstrapProcessing
    data class Reduced(val reduction: DeviceRuntimeMetadataReduction) :
        DeviceRuntimeMetadataBootstrapProcessing
}

internal val deviceRuntimeMetadataBootstrapOrder = listOf(
    DeviceRuntimeMetadataBootstrapKind.IDENTITY,
    DeviceRuntimeMetadataBootstrapKind.CAPABILITIES,
    DeviceRuntimeMetadataBootstrapKind.STATUS_MODULES
)

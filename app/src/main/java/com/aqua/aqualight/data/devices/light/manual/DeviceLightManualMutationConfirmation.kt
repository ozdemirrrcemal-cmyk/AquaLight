package com.aqua.aqualight.data.devices.light.manual

import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualFailure
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMutationResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightScene
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.requestGraph

internal suspend fun DeviceRuntimeCommandOutcome<*>.confirmManualMutation(
    uid: DeviceUid,
    runtime: DeviceLightRuntimeRepository,
    expectedScene: DeviceLightScene
): DeviceLightManualMutationResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> {
        val status = runtime.currentStatus(uid)
            ?.takeIf { current -> current.confirms(expectedScene) }
            ?: runtime.refreshStatusAndGraph(uid)
                ?.takeIf { current -> current.confirms(expectedScene) }
        status?.toManualSnapshot(uid)
            ?.let(DeviceLightManualMutationResult::Success)
            ?: manualMutationFailure(DeviceLightManualFailure.UNAVAILABLE)
    }
    else -> manualMutationFailure(toManualFailure())
}

private suspend fun DeviceLightRuntimeRepository.refreshStatusAndGraph(
    uid: DeviceUid
): DeviceLightStatus? {
    val outcome = requestStatus(uid)
    if (outcome is DeviceRuntimeCommandOutcome.Success) requestGraph(uid)
    return currentStatus(uid)
}

private fun DeviceLightStatus.confirms(expectedScene: DeviceLightScene): Boolean =
    mode == DeviceLightMode.MANUAL && manual.scene == expectedScene

internal fun DeviceRuntimeCommandOutcome<*>.toManualFailure(): DeviceLightManualFailure = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> DeviceLightManualFailure.NOT_CONNECTED
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> DeviceLightManualFailure.UNSUPPORTED
    is DeviceRuntimeCommandOutcome.FirmwareError -> DeviceLightManualFailure.REJECTED
    is DeviceRuntimeCommandOutcome.ProtocolError -> DeviceLightManualFailure.INVALID_DATA
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> DeviceLightManualFailure.UNAVAILABLE
    is DeviceRuntimeCommandOutcome.Success -> error("A successful outcome has no failure.")
}

internal fun manualMutationFailure(failure: DeviceLightManualFailure) =
    DeviceLightManualMutationResult.Failed(failure)

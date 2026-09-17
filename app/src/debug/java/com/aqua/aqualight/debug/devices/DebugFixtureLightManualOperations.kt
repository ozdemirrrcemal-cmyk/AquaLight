package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualFailure
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMutationResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualOperations
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualReadResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScene
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Installable-debug decorator backed by the single in-process Light fixture runtime. */
internal class DebugFixtureLightManualOperations(
    private val delegate: DeviceLightManualOperations,
    private val runtime: DebugLightFixtureRuntime
) : DeviceLightManualOperations {

    override fun observe(deviceUid: String): Flow<DeviceLightManualReadResult> =
        if (runtime.contains(deviceUid)) {
            runtime.revisions.map {
                runtime.currentManual(deviceUid)
                    ?.let(DeviceLightManualReadResult::Available)
                    ?: DeviceLightManualReadResult.Failed(DeviceLightManualFailure.INVALID_DATA)
            }
        } else {
            delegate.observe(deviceUid)
        }

    override suspend fun setScene(
        deviceUid: String,
        scene: DeviceLightManualScene
    ): DeviceLightManualMutationResult = if (runtime.contains(deviceUid)) {
        runtime.setManual(deviceUid, scene)
            ?.let(DeviceLightManualMutationResult::Success)
            ?: DeviceLightManualMutationResult.Failed(DeviceLightManualFailure.INVALID_DATA)
    } else {
        delegate.setScene(deviceUid, scene)
    }

    override suspend fun turnOff(deviceUid: String): DeviceLightManualMutationResult =
        if (runtime.contains(deviceUid)) {
            runtime.turnManualOff(deviceUid)
                ?.let(DeviceLightManualMutationResult::Success)
                ?: DeviceLightManualMutationResult.Failed(DeviceLightManualFailure.INVALID_DATA)
        } else {
            delegate.turnOff(deviceUid)
        }
}

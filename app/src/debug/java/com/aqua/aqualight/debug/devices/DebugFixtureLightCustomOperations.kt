package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomMutationResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomReadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Routes only debug-fixture UIDs to the in-process Light runtime. */
internal class DebugFixtureLightCustomOperations(
    private val delegate: DeviceLightCustomOperations,
    private val runtime: DebugLightFixtureRuntime
) : DeviceLightCustomOperations {

    override fun observe(deviceUid: String): Flow<DeviceLightCustomReadResult> =
        if (runtime.contains(deviceUid)) {
            runtime.revisions.map {
                runtime.current(deviceUid)
                    ?.let(DeviceLightCustomReadResult::Available)
                    ?: failedRead()
            }
        } else {
            delegate.observe(deviceUid)
        }

    override fun current(deviceUid: String): DeviceLightCustomReadResult =
        if (runtime.contains(deviceUid)) {
            runtime.current(deviceUid)
                ?.let(DeviceLightCustomReadResult::Available)
                ?: failedRead()
        } else {
            delegate.current(deviceUid)
        }

    override suspend fun read(deviceUid: String): DeviceLightCustomReadResult =
        if (runtime.contains(deviceUid)) {
            runtime.current(deviceUid)
                ?.let(DeviceLightCustomReadResult::Available)
                ?: failedRead()
        } else {
            delegate.read(deviceUid)
        }

    override suspend fun preview(
        deviceUid: String,
        virtualTimeMs: Long
    ): DeviceLightCustomMutationResult = if (runtime.contains(deviceUid)) {
        if (runtime.preview(deviceUid, virtualTimeMs)) success() else failedMutation()
    } else {
        delegate.preview(deviceUid, virtualTimeMs)
    }

    override suspend fun clearPreview(deviceUid: String): DeviceLightCustomMutationResult =
        if (runtime.contains(deviceUid)) {
            if (runtime.clearPreview(deviceUid)) success() else failedMutation()
        } else {
            delegate.clearPreview(deviceUid)
        }
}

private fun success(): DeviceLightCustomMutationResult = DeviceLightCustomMutationResult.Success

private fun failedRead(): DeviceLightCustomReadResult =
    DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.INVALID_DATA)

private fun failedMutation(): DeviceLightCustomMutationResult =
    DeviceLightCustomMutationResult.Failed(DeviceLightCustomFailure.INVALID_DATA)

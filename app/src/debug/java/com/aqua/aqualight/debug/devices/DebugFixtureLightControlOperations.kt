package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightAdaptationSummary
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Stateless debug adapter over the single Light fixture runtime; real UIDs still delegate. */
internal class DebugFixtureLightControlOperations(
    private val delegate: DeviceLightControlOperations,
    private val runtime: DebugLightFixtureRuntime,
    private val adaptationOperations: DebugFixtureLightAdaptationOperations? = null
) : DeviceLightControlOperations {

    override fun observeControl(deviceUid: String): Flow<DeviceLightControlResult> =
        if (!runtime.contains(deviceUid)) {
            delegate.observeControl(deviceUid)
        } else if (adaptationOperations?.supportsFixture(deviceUid) == true) {
            runtime.controlSnapshots(deviceUid)
                .combine(adaptationOperations.observe(deviceUid)) { snapshot, _ ->
                    snapshot.withCurrentAdaptation(deviceUid).toFixtureResult()
                }
        } else {
            runtime.controlSnapshots(deviceUid).map { snapshot -> snapshot.toFixtureResult() }
        }

    override fun currentControl(deviceUid: String): DeviceLightControlResult =
        if (runtime.contains(deviceUid)) {
            runtime.currentControl(deviceUid)
                .withCurrentAdaptation(deviceUid)
                .toFixtureResult()
        } else {
            delegate.currentControl(deviceUid)
        }

    override suspend fun refreshControl(deviceUid: String): DeviceLightControlResult =
        if (runtime.contains(deviceUid)) {
            runtime.currentControl(deviceUid)
                .withCurrentAdaptation(deviceUid)
                .toFixtureResult()
        } else {
            delegate.refreshControl(deviceUid)
        }

    private fun DeviceLightControlSnapshot?.withCurrentAdaptation(
        deviceUid: String
    ) = this?.copy(
        adaptation = adaptationOperations?.summary(deviceUid)
            ?: DeviceLightAdaptationSummary()
    )

    private fun DebugLightFixtureRuntime.controlSnapshots(
        deviceUid: String
    ): Flow<DeviceLightControlSnapshot?> = revisions
        .map { currentControl(deviceUid) }
        .distinctUntilChanged()
}

private fun DeviceLightControlSnapshot?.toFixtureResult(): DeviceLightControlResult =
    this?.let(DeviceLightControlResult::Available)
        ?: DeviceLightControlResult.Failed(DeviceLightControlFailure.UNSUPPORTED)

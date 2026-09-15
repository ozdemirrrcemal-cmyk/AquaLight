package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.control.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.control.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.control.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.control.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.control.DeviceLightAdaptationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Catalog-derived Light readiness projection for installable debug fixtures only. */
internal class DebugFixtureLightControlOperations(
    private val delegate: DeviceLightControlOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val adaptationOperations: DebugFixtureLightAdaptationOperations? = null
) : DeviceLightControlOperations {

    override fun observeControl(deviceUid: String): Flow<DeviceLightControlResult> {
        val fixture = fixtureResult(deviceUid)
        return when {
            fixture == null -> delegate.observeControl(deviceUid)
            adaptationOperations?.supportsFixture(deviceUid) == true ->
                adaptationOperations.observe(deviceUid).map { fixtureResult(deviceUid) ?: fixture }
            else -> flowOf(fixture)
        }
    }

    override fun currentControl(deviceUid: String): DeviceLightControlResult =
        fixtureResult(deviceUid) ?: delegate.currentControl(deviceUid)

    override suspend fun refreshControl(deviceUid: String): DeviceLightControlResult =
        fixtureResult(deviceUid) ?: delegate.refreshControl(deviceUid)

    private fun fixtureResult(deviceUid: String): DeviceLightControlResult? {
        val root = fixtures.rootSnapshot(deviceUid)
        return when {
            root == null -> null
            root.channelSlots.lightChannels.isEmpty() ->
                DeviceLightControlResult.Failed(DeviceLightControlFailure.UNSUPPORTED)
            else -> DeviceLightControlResult.Available(
                DeviceLightControlSnapshot(
                    deviceUid = root.deviceUid,
                    productKey = root.productKey,
                    physicalChannelCount = root.channelSlots.lightChannels.size,
                    channelKeys = root.channelSlots.lightChannels.map { slot -> slot.wireKey.value },
                    adaptation = adaptationOperations?.summary(root.deviceUid)
                        ?: root.fixtureAdaptationSummary()
                )
            )
        }
    }
}

private fun com.aqua.aqualight.application.devices.DeviceRootSnapshot.fixtureAdaptationSummary() =
    DeviceLightAdaptationSummary(
        supported = LIGHT_ACCLIMATION_FEATURE in supportedFeatures
    )

private const val LIGHT_ACCLIMATION_FEATURE = "LIGHT_ACCLIMATION"

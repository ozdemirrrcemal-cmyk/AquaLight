package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.DeviceLightControlSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Catalog-derived Light readiness projection for installable debug fixtures only. */
internal class DebugFixtureLightControlOperations(
    private val delegate: DeviceLightControlOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceLightControlOperations {

    override fun observeControl(deviceUid: String): Flow<DeviceLightControlResult> =
        fixtureResult(deviceUid)?.let(::flowOf) ?: delegate.observeControl(deviceUid)

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
                    channelKeys = root.channelSlots.lightChannels.map { slot -> slot.wireKey.value }
                )
            )
        }
    }
}

package com.aqua.aqualight.application.devices.light

import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import kotlinx.coroutines.flow.Flow

/** Firmware-independent application boundary for the shared Light V1 control surface. */
interface DeviceLightControlOperations {
    fun observeControl(deviceUid: String): Flow<DeviceLightControlResult>

    /** Returns a snapshot only when the current runtime generation is authoritative. */
    fun currentControl(deviceUid: String): DeviceLightControlResult

    /** Refreshes the complete Light V1 status document used to prepare the root surface. */
    suspend fun refreshControl(deviceUid: String): DeviceLightControlResult
}

sealed interface DeviceLightControlResult {
    data class Available(
        val snapshot: DeviceLightControlSnapshot
    ) : DeviceLightControlResult

    data class Failed(
        val failure: DeviceLightControlFailure
    ) : DeviceLightControlResult
}

enum class DeviceLightControlFailure {
    UNAVAILABLE,
    NOT_CONNECTED,
    UNSUPPORTED,
    REJECTED,
    INVALID_DATA
}

/** Minimum authoritative identity needed before rendering any Light control content. */
data class DeviceLightControlSnapshot(
    val deviceUid: String,
    val productKey: String,
    val physicalChannelCount: Int,
    val channelKeys: List<String>
)

/** Exact catalog/runtime identity check shared by preparation and destination read paths. */
fun DeviceLightControlSnapshot?.matchesLightControlSurface(
    deviceUid: String,
    root: DeviceRootSnapshot?
): Boolean {
    if (this == null || root == null || this.deviceUid != deviceUid) return false
    if (productKey != root.productKey) return false
    val expectedKeys = root.channelSlots.lightChannels.map { slot -> slot.wireKey.value }
    if (physicalChannelCount != root.lightChannelCount) return false
    return channelKeys.size == expectedKeys.size && channelKeys.toSet() == expectedKeys.toSet()
}

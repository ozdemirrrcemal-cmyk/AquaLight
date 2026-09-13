package com.aqua.aqualight.application.devices.light.control

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

/** Authoritative Light snapshot projected without leaking firmware models into presentation. */
data class DeviceLightControlSnapshot(
    val deviceUid: String,
    val productKey: String,
    val physicalChannelCount: Int,
    val channelKeys: List<String>,
    val hero: DeviceLightHeroSnapshot = DeviceLightHeroSnapshot()
)

data class DeviceLightHeroSnapshot(
    val mode: DeviceLightControlMode? = null,
    val outputActive: Boolean? = null,
    val outputCondition: DeviceLightOutputCondition? = null,
    val outputHealthy: Boolean? = null,
    val estimatedPowerWatts: Double? = null,
    val estimatedColorTemperatureKelvin: Int? = null
)

enum class DeviceLightControlMode {
    MANUAL,
    AUTOMATIC,
    CUSTOM
}

enum class DeviceLightOutputCondition {
    ACTIVE,
    SCHEDULED_OFF,
    ALL_CHANNELS_ZERO,
    CLOCK_UNAVAILABLE,
    THERMAL_PROTECTION,
    POWER_LIMITED,
    HARDWARE_FAULT
}

/** Exact catalog/runtime identity check shared by preparation and destination read paths. */
fun DeviceLightControlSnapshot?.matchesLightControlSurface(
    deviceUid: String,
    root: DeviceRootSnapshot?
): Boolean {
    val expectedKeys = root?.channelSlots?.lightChannels
        ?.map { slot -> slot.wireKey.value }
        .orEmpty()
    return when {
        this == null || root == null -> false
        this.deviceUid != deviceUid -> false
        productKey != root.productKey -> false
        physicalChannelCount != root.lightChannelCount -> false
        channelKeys.size != expectedKeys.size -> false
        else -> channelKeys.toSet() == expectedKeys.toSet()
    }
}

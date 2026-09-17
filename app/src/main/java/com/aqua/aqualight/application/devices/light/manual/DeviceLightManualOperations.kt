package com.aqua.aqualight.application.devices.light.manual

import kotlinx.coroutines.flow.Flow

/** Firmware-independent boundary for authoritative Manual Light control. */
interface DeviceLightManualOperations {
    fun observe(deviceUid: String): Flow<DeviceLightManualReadResult>

    suspend fun setScene(
        deviceUid: String,
        scene: DeviceLightManualScene
    ): DeviceLightManualMutationResult

    suspend fun turnOff(deviceUid: String): DeviceLightManualMutationResult
}

enum class DeviceLightManualChannel {
    RED,
    GREEN,
    BLUE,
    WHITE
}

data class DeviceLightManualScene(
    val channels: Map<DeviceLightManualChannel, Int>
) {
    init {
        require(channels.isNotEmpty())
        require(channels.values.all { percent -> percent in MANUAL_PERCENT_RANGE })
    }
}

data class DeviceLightManualSnapshot(
    val deviceUid: String,
    val productKey: String,
    val scene: DeviceLightManualScene,
    val estimatedPowerWatts: Int?,
    val estimatedPowerRatio: Float?,
    val protection: DeviceLightManualProtection?
) {
    init {
        require(deviceUid.isNotBlank())
        require(productKey.isNotBlank())
        require(estimatedPowerWatts == null || estimatedPowerWatts >= 0)
        require(estimatedPowerRatio == null || estimatedPowerRatio in POWER_RATIO_RANGE)
    }
}

data class DeviceLightManualProtection(
    val kind: DeviceLightManualProtectionKind,
    val effectivePercent: Int? = null
) {
    init {
        require(effectivePercent == null || effectivePercent in MANUAL_PERCENT_RANGE)
        require(kind != DeviceLightManualProtectionKind.THERMAL_SHUTDOWN || effectivePercent == null)
    }
}

enum class DeviceLightManualProtectionKind {
    POWER_LIMITED,
    THERMAL_LIMITED,
    THERMAL_SHUTDOWN
}

sealed interface DeviceLightManualReadResult {
    data class Available(val snapshot: DeviceLightManualSnapshot) : DeviceLightManualReadResult
    data class Failed(val failure: DeviceLightManualFailure) : DeviceLightManualReadResult
}

sealed interface DeviceLightManualMutationResult {
    data class Success(val snapshot: DeviceLightManualSnapshot) : DeviceLightManualMutationResult
    data class Failed(val failure: DeviceLightManualFailure) : DeviceLightManualMutationResult
}

enum class DeviceLightManualFailure {
    UNAVAILABLE,
    NOT_CONNECTED,
    UNSUPPORTED,
    REJECTED,
    INVALID_DATA
}

private val MANUAL_PERCENT_RANGE = 0..100
private val POWER_RATIO_RANGE = 0f..1f

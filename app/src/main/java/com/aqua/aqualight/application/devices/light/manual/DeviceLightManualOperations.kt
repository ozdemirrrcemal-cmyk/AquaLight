package com.aqua.aqualight.application.devices.light.manual

import kotlinx.coroutines.flow.Flow

/** Firmware-independent Manual boundary with retained presentation and authoritative writes. */
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

/** Firmware-authored channel identity and presentation metadata. */
data class DeviceLightManualChannelDescriptor(
    val channel: DeviceLightManualChannel,
    val key: String,
    val displayName: String,
    val displayColorRgb: Int,
    val order: Int
) {
    init {
        require(key.isNotBlank())
        require(displayName.isNotBlank())
        require(displayColorRgb in DISPLAY_COLOR_RANGE)
        require(order >= 0)
    }
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
    val channelDescriptors: List<DeviceLightManualChannelDescriptor>,
    val scene: DeviceLightManualScene,
    val estimatedPowerWatts: Int?,
    val estimatedPowerRatio: Float?,
    val estimatedPowerDisplayColorRgb: Int?,
    val protection: DeviceLightManualProtection?,
    /** Current-generation authority for firmware writes; presentation may be older and retained. */
    val firmwareWriteAuthoritative: Boolean
) {
    init {
        require(deviceUid.isNotBlank())
        require(productKey.isNotBlank())
        require(
            channelDescriptors.map { descriptor -> descriptor.order } ==
                channelDescriptors.indices.toList()
        )
        require(
            channelDescriptors.map { descriptor -> descriptor.channel }.let { channels ->
                channels.size == channels.distinct().size && channels.toSet() == scene.channels.keys
            }
        )
        require(
            channelDescriptors.map { descriptor -> descriptor.key }.distinct().size ==
                channelDescriptors.size
        )
        require(estimatedPowerWatts == null || estimatedPowerWatts >= 0)
        require(estimatedPowerRatio == null || estimatedPowerRatio in POWER_RATIO_RANGE)
        require(
            estimatedPowerDisplayColorRgb == null ||
                estimatedPowerDisplayColorRgb in DISPLAY_COLOR_RANGE
        )
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

private val MANUAL_PERCENT_RANGE = MANUAL_MIN_PERCENT..MANUAL_MAX_PERCENT
private val POWER_RATIO_RANGE = 0f..1f
private val DISPLAY_COLOR_RANGE = 0..DISPLAY_COLOR_RGB_MAX
private const val MANUAL_MIN_PERCENT = 0
private const val MANUAL_MAX_PERCENT = 100
private const val DISPLAY_COLOR_RGB_MAX = 0xFFFFFF

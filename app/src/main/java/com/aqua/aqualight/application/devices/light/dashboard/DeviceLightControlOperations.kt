package com.aqua.aqualight.application.devices.light.dashboard

import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemCondition
import kotlinx.coroutines.flow.Flow

/** Firmware-independent application boundary for the shared Light V1 control surface. */
interface DeviceLightControlOperations {
    /** Observes the last complete validated frame; half-refreshes never erase presentation. */
    fun observeControl(deviceUid: String): Flow<DeviceLightControlResult>

    /** Returns a snapshot only when the current runtime generation is authoritative. */
    fun currentControl(deviceUid: String): DeviceLightControlResult

    /** Refreshes the Light V1 status and graph documents used to prepare the root surface. */
    suspend fun refreshControl(deviceUid: String): DeviceLightControlResult

    /**
     * Writes the selected operating mode.
     *
     * A firmware ACK is a committed mutation even when the authoritative status/graph readback is
     * still pending. Callers must not reinterpret a delayed readback as a rejected device write.
     */
    suspend fun setMode(
        deviceUid: String,
        mode: DeviceLightControlMode
    ): DeviceLightModeMutationResult
}

sealed interface DeviceLightModeMutationResult {
    data class Reconciled(
        val snapshot: DeviceLightControlSnapshot
    ) : DeviceLightModeMutationResult

    data class Committed(
        val mode: DeviceLightControlMode
    ) : DeviceLightModeMutationResult

    data class Failed(
        val failure: DeviceLightControlFailure
    ) : DeviceLightModeMutationResult
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

/** Fully validated Light frame projected without leaking firmware models into presentation. */
data class DeviceLightControlSnapshot(
    val deviceUid: String,
    val productKey: String,
    val physicalChannelCount: Int,
    val channelKeys: List<String>,
    val channels: List<DeviceLightChannelOutputSnapshot>,
    val plan: DeviceLightPlanSnapshot,
    val automaticProgramCount: Int,
    val customCurvePointCount: Int,
    val activeAutomaticProgramId: String? = null,
    val hero: DeviceLightHeroSnapshot = DeviceLightHeroSnapshot(),
    val adaptation: DeviceLightAdaptationSummary = DeviceLightAdaptationSummary(),
    val systemSupported: Boolean = false,
    val system: DeviceLightSystemSummary? = null
)

/** Channel metadata and effective output exactly as reported by light.status.get. */
data class DeviceLightChannelOutputSnapshot(
    val key: String,
    val displayName: String,
    val displayColorRgb: Int,
    val effectivePercent: Int
)

/** Daily plan exactly as reported by light.graph.get; presentation only scales it for drawing. */
data class DeviceLightPlanSnapshot(
    val available: Boolean,
    val reason: DeviceLightPlanReason,
    val nowTimeMs: Long?,
    val channelScale: Int,
    val hasScheduleToday: Boolean,
    val points: List<DeviceLightPlanPointSnapshot>
)

data class DeviceLightPlanPointSnapshot(
    val timeMs: Long,
    val channelLevels: List<Int>
)

enum class DeviceLightPlanReason {
    OK,
    MODE_HAS_NO_SCHEDULE,
    RTC_NOT_READY,
    NO_ENABLED_AUTO_PROGRAM_TODAY,
    CUSTOM_NOT_INSTALLED,
    CUSTOM_NOT_SCHEDULED_TODAY
}

data class DeviceLightAdaptationSummary(
    val supported: Boolean = false,
    val state: DeviceLightAdaptationState? = null,
    val currentPermille: Int? = null,
    val remainingSeconds: Long? = null
)

data class DeviceLightSystemSummary(
    val temperatureCelsius: Double?,
    val fanPercents: List<Int>,
    val condition: DeviceLightSystemCondition
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
        channelKeys.toSet() != expectedKeys.toSet() -> false
        channels.map { channel -> channel.key } != channelKeys -> false
        plan.points.any { point -> point.channelLevels.size != channels.size } -> false
        else -> true
    }
}

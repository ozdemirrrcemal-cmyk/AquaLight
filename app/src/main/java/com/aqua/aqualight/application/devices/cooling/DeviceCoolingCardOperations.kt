package com.aqua.aqualight.application.devices.cooling

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlReason
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import kotlinx.coroutines.flow.Flow

/** Read-only application boundary for the tank-detail Cooling card. */
interface DeviceCoolingCardOperations {
    fun observe(deviceUid: String): Flow<DeviceCoolingCardState>
}

sealed interface DeviceCoolingCardState {
    data object Preparing : DeviceCoolingCardState

    data class Ready(val summary: DeviceCoolingCardSummary) : DeviceCoolingCardState

    data class Unavailable(val reason: DeviceCoolingCardUnavailableReason) : DeviceCoolingCardState
}

enum class DeviceCoolingCardUnavailableReason {
    INVALID_DEVICE_UID,
    DEVICE_NOT_REGISTERED,
    DEVICE_FAMILY_MISMATCH,
    RUNTIME_CONNECTION_FAILED,
    OBSERVATION_FAILED
}

data class DeviceCoolingCardSummary(
    val mode: DeviceCoolingControlMode,
    val operatingState: DeviceCoolingOperatingState,
    val controlReason: DeviceCoolingControlReason,
    val waterTemperatureC: Double?,
    val actualFanPercent: Double?,
    val targetFanPercent: Double?,
    val automaticRange: DeviceCoolingCardTemperatureRange?,
    val program: DeviceCoolingCardProgramSummary?,
    /** True only when the authoritative firmware state and applied output both indicate motion. */
    val fanMotionActive: Boolean
)

data class DeviceCoolingCardTemperatureRange(
    val startC: Double,
    val fullSpeedC: Double
)

data class DeviceCoolingCardProgramSummary(
    val activeSlotNumber: Int?,
    val slotCount: Int,
    val activeSlot: DeviceCoolingCardProgramSlot?
)

data class DeviceCoolingCardProgramSlot(
    val startMinutes: Int,
    val endMinutes: Int
)

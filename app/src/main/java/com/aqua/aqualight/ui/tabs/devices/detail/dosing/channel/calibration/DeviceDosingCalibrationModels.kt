package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import android.os.SystemClock
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationTarget

enum class DeviceDosingCalibrationStep {
    NAME,
    PRIME,
    CALIBRATION_RUN,
    MEASUREMENT,
    VERIFICATION,
    CONFIRMATION
}

enum class DeviceDosingCalibrationError {
    INVALID_NAME,
    INVALID_MEASUREMENT,
    CONNECTION,
    UNAVAILABLE
}

data class DeviceDosingCalibrationUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val step: DeviceDosingCalibrationStep = DeviceDosingCalibrationStep.NAME,
    val displayName: String = "",
    val measuredMl: String = "",
    val pumpCount: Int = 0,
    val channelNumber: Int = 0,
    val channelTitle: String = "",
    val isPumpActive: Boolean = false,
    val remainingMs: Long = 0L,
    val candidateDoseMsPerMl: Long? = null,
    val error: DeviceDosingCalibrationError? = null
)

sealed interface DeviceDosingCalibrationEvent {
    data object Exit : DeviceDosingCalibrationEvent
    data class Completed(
        val target: DeviceDosingChannelNavigationTarget
    ) : DeviceDosingCalibrationEvent
}

fun interface DeviceDosingCalibrationClock {
    fun elapsedRealtime(): Long
}

internal object SystemDeviceDosingCalibrationClock : DeviceDosingCalibrationClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}

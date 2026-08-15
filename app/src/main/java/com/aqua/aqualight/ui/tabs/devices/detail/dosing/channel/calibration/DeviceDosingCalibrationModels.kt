package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import android.os.SystemClock
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget

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

data class DosingCalibrationProgressState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val step: DeviceDosingCalibrationStep = DeviceDosingCalibrationStep.NAME,
    val isPumpActive: Boolean = false,
    val remainingMs: Long = 0L,
    val candidateDoseMsPerMl: Long? = null
)

data class DosingCalibrationChannelState(
    val pumpCount: Int = 0,
    val channelNumber: Int = 0
)

data class DosingCalibrationInputState(
    val displayName: String = "",
    val measuredMl: String = ""
)

data class DeviceDosingCalibrationUiState(
    val progress: DosingCalibrationProgressState = DosingCalibrationProgressState(),
    val channel: DosingCalibrationChannelState = DosingCalibrationChannelState(),
    val input: DosingCalibrationInputState = DosingCalibrationInputState(),
    val error: DeviceDosingCalibrationError? = null
) {
    val isLoading: Boolean get() = progress.isLoading
    val isBusy: Boolean get() = progress.isBusy
    val step: DeviceDosingCalibrationStep get() = progress.step
    val isPumpActive: Boolean get() = progress.isPumpActive
    val remainingMs: Long get() = progress.remainingMs
    val candidateDoseMsPerMl: Long? get() = progress.candidateDoseMsPerMl
    val pumpCount: Int get() = channel.pumpCount
    val channelNumber: Int get() = channel.channelNumber
    val displayName: String get() = input.displayName
    val measuredMl: String get() = input.measuredMl
}

internal inline fun DeviceDosingCalibrationUiState.updateProgress(
    transform: (DosingCalibrationProgressState) -> DosingCalibrationProgressState
): DeviceDosingCalibrationUiState = copy(progress = transform(progress))

internal inline fun DeviceDosingCalibrationUiState.updateChannel(
    transform: (DosingCalibrationChannelState) -> DosingCalibrationChannelState
): DeviceDosingCalibrationUiState = copy(channel = transform(channel))

internal inline fun DeviceDosingCalibrationUiState.updateInput(
    transform: (DosingCalibrationInputState) -> DosingCalibrationInputState
): DeviceDosingCalibrationUiState = copy(input = transform(input))

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

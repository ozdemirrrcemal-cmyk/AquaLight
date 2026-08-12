package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Public lifecycle facade; the private workflow owns the single calibration UI state source. */
class DeviceDosingChannelCalibrationViewModel(
    operations: DeviceDosingCalibrationOperations,
    clock: DeviceDosingCalibrationClock = SystemDeviceDosingCalibrationClock
) : ViewModel() {
    private val workflow = DosingCalibrationWorkflow(
        operations = operations,
        clock = clock,
        scope = viewModelScope
    )

    val uiState: StateFlow<DeviceDosingCalibrationUiState> = workflow.uiState
    val events: Flow<DeviceDosingCalibrationEvent> = workflow.events

    internal fun bind(route: DeviceDosingCalibrationRoute) = workflow.bind(route)

    internal fun onAction(action: DeviceDosingCalibrationAction) = workflow.onAction(action)

    fun requestExit() = workflow.requestExit()

    fun onHostStopped() = workflow.onHostStopped()
}

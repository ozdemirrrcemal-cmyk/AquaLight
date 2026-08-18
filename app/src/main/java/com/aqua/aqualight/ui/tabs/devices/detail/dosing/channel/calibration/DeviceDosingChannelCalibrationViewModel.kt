package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

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
    private val feedbackChannel = Channel<DeviceDosingCalibrationError>(Channel.BUFFERED)
    private var lastForwardedError: DeviceDosingCalibrationError? = null

    val uiState: StateFlow<DeviceDosingCalibrationUiState> = workflow.uiState
    val events: Flow<DeviceDosingCalibrationEvent> = workflow.events
    val feedbackEvents: Flow<DeviceDosingCalibrationError> = feedbackChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            workflow.uiState.collect {
                yield()
                val error = workflow.uiState.value.error
                when {
                    error == null -> lastForwardedError = null
                    error != lastForwardedError -> {
                        lastForwardedError = error
                        feedbackChannel.send(error)
                    }
                }
            }
        }
    }

    internal fun bind(route: DeviceDosingCalibrationRoute) = workflow.bind(route)

    internal fun onAction(action: DeviceDosingCalibrationAction) {
        workflow.onAction(action)
        val error = workflow.uiState.value.error
        if (error == null) {
            lastForwardedError = null
            return
        }
        if (action.producesSynchronousValidationFeedback()) {
            lastForwardedError = error
            feedbackChannel.trySend(error)
        }
    }

    fun requestExit() {
        lastForwardedError = null
        workflow.requestExit()
    }

    fun onHostStopped() = workflow.onHostStopped()
}

private fun DeviceDosingCalibrationAction.producesSynchronousValidationFeedback(): Boolean =
    when (this) {
        DeviceDosingCalibrationAction.SaveDisplayName,
        DeviceDosingCalibrationAction.SaveMeasurement,
        DeviceDosingCalibrationAction.AcceptVerification -> true
        else -> false
    }

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingProgramRuntimeSnapshot

@Composable
internal fun CoolingSystemOperationCard(
    snapshot: DeviceCoolingControlSnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    CoolingSystemStatusSection(
        title = stringResource(R.string.device_cooling_system_status_operation_title),
        visuals = visuals
    ) {
        CoolingSystemOperationRows(snapshot = snapshot, visuals = visuals)
        CoolingSystemProgramRows(runtime = snapshot.programRuntime, visuals = visuals)
    }
}

@Composable
private fun CoolingSystemOperationRows(
    snapshot: DeviceCoolingControlSnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_mode),
        value = stringResource(snapshot.mode.toStatusTextRes()),
        visuals = visuals
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_operating_state),
        value = snapshot.operatingState?.let { state ->
            stringResource(state.toStatusTextRes())
        } ?: stringResource(R.string.device_cooling_value_unavailable),
        visuals = visuals,
        tone = if (snapshot.operatingState == DeviceCoolingOperatingState.FAULT) {
            CoolingSystemStatusTone.DANGER
        } else {
            CoolingSystemStatusTone.NEUTRAL
        }
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_control_reason),
        value = stringResource(snapshot.controlReason.toStatusTextRes()),
        visuals = visuals
    )
}

@Composable
private fun CoolingSystemProgramRows(
    runtime: DeviceCoolingProgramRuntimeSnapshot?,
    visuals: CoolingSystemStatusVisuals
) {
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_clock),
        value = runtime?.clockReady?.let { ready ->
            stringResource(
                if (ready) R.string.device_cooling_system_status_clock_ready
                else R.string.device_cooling_system_status_clock_unsynced
            )
        } ?: stringResource(R.string.device_cooling_value_unavailable),
        visuals = visuals,
        tone = when (runtime?.clockReady) {
            true -> CoolingSystemStatusTone.SUCCESS
            false -> CoolingSystemStatusTone.DANGER
            null -> CoolingSystemStatusTone.NEUTRAL
        }
    )
    runtime?.currentMinuteOfDay?.let { minute ->
        CoolingSystemStatusDetailRow(
            label = stringResource(R.string.device_cooling_system_status_device_time),
            value = coolingMinuteOfDayText(minute),
            visuals = visuals
        )
    }
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_active_program_slot),
        value = runtime?.activeSlotIndex?.let { index ->
            stringResource(R.string.device_cooling_system_status_program_slot_format, index + 1)
        } ?: stringResource(R.string.device_cooling_system_status_no_active_program_slot),
        visuals = visuals
    )
}

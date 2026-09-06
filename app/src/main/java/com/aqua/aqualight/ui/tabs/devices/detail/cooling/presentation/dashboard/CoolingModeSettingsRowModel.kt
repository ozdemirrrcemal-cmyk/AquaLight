package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIconKind
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingControlMode
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.DeviceCoolingRootUiState

internal data class CoolingModeSettingsRowModel(
    val mode: CoolingControlMode,
    val icon: AquaCoolingDashboardIconKind,
    val value: String,
    val contentDescription: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
internal fun coolingModeSettingsRowModels(
    state: DeviceCoolingRootUiState,
    actions: DeviceCoolingDashboardActions
): List<CoolingModeSettingsRowModel> = listOf(
    CoolingModeSettingsRowModel(
        mode = CoolingControlMode.AUTOMATIC,
        icon = AquaCoolingDashboardIconKind.AUTOMATIC,
        value = coolingAutomaticRangeText(state),
        contentDescription = stringResource(R.string.device_cooling_edit_automatic_description),
        selected = state.selectedMode == CoolingControlMode.AUTOMATIC,
        onClick = actions.onAutomaticSettingsClick
    ),
    CoolingModeSettingsRowModel(
        mode = CoolingControlMode.MANUAL,
        icon = AquaCoolingDashboardIconKind.MANUAL,
        value = coolingManualTargetText(state.manualFanPercent),
        contentDescription = stringResource(R.string.device_cooling_manual_settings_description),
        selected = state.selectedMode == CoolingControlMode.MANUAL,
        onClick = actions.onManualSettingsClick
    ),
    CoolingModeSettingsRowModel(
        mode = CoolingControlMode.PROGRAM,
        icon = AquaCoolingDashboardIconKind.PROGRAM,
        value = coolingProgramSummaryText(state),
        contentDescription = stringResource(R.string.device_cooling_edit_program_description),
        selected = state.selectedMode == CoolingControlMode.PROGRAM,
        onClick = actions.onProgramSettingsClick
    )
)

@Composable
private fun coolingAutomaticRangeText(state: DeviceCoolingRootUiState): String {
    val minimum = state.autoStartTemperatureC
    val maximum = state.autoMaxTemperatureC
    return if (minimum != null && maximum != null) {
        stringResource(
            R.string.device_cooling_temperature_range_value_format,
            minimum,
            maximum
        )
    } else {
        stringResource(R.string.device_cooling_value_unavailable)
    }
}

@Composable
private fun coolingManualTargetText(percent: Int?): String = percent?.let { value ->
    stringResource(R.string.device_cooling_manual_target_value_format, value)
} ?: stringResource(R.string.device_cooling_manual_target_unavailable)

@Composable
private fun coolingProgramSummaryText(state: DeviceCoolingRootUiState): String {
    val slotCount = state.programSlotCount
    return when {
        slotCount == null -> stringResource(R.string.device_cooling_value_unavailable)
        slotCount == 0 -> stringResource(R.string.device_cooling_program_not_configured)
        else -> pluralStringResource(
            R.plurals.device_cooling_program_period_count,
            slotCount,
            slotCount
        )
    }
}

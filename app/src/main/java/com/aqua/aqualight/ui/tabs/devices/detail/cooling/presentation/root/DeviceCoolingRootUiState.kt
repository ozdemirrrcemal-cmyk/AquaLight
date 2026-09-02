package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.authoritativeValueOrNull

typealias CoolingControlMode = DeviceCoolingControlMode

data class CoolingControlPresentation(
    val selectedMode: CoolingControlMode,
    val supportedModes: Set<CoolingControlMode>,
    val modeSelectionWritable: Boolean,
    val manualFanCapabilities: DeviceCoolingManualFanCapabilities?,
    val manualFanPercent: Int?,
    val actualFanPercent: Int?,
    val tankTemperatureC: Double?
)

data class CoolingAutomaticSummaryPresentation(
    val startTemperatureC: Double,
    val maximumSpeedTemperatureC: Double
)

data class CoolingHistoryOverviewPresentation(
    val temperaturesC: List<Double>
)

enum class CoolingHealthState {
    READY,
    WARNING,
    FAULT,
    UNKNOWN
}

data class CoolingDashboardOverviewPresentation(
    val roomTemperatureC: Double?,
    val humidityPercent: Double?,
    val powerWatts: Double?,
    val estimatedKwhPerDay: Double?,
    val roomTemperatureHistoryC: List<Double>,
    val programSlotCount: Int?,
    val nextProgramStartMinutesOfDay: Int?,
    val fanHealth: CoolingHealthState,
    val sensorHealth: CoolingHealthState,
    val activeAlarmCount: Int?
)

data class DeviceCoolingRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val contentEnabled: Boolean = false,
    val controlState: CoolingDataState<CoolingControlPresentation, DeviceCoolingControlFailure> =
        CoolingDataState.Initial,
    val controlMutationState: CoolingMutationState<DeviceCoolingControlFailure> =
        CoolingMutationState.Idle,
    val automaticSummaryState: CoolingDataState<
        CoolingAutomaticSummaryPresentation,
        DeviceCoolingAutomaticFailure
        > = CoolingDataState.Initial,
    val historyState: CoolingDataState<CoolingHistoryOverviewPresentation, Nothing> =
        CoolingDataState.Initial,
    val dashboardOverviewState: CoolingDataState<
        CoolingDashboardOverviewPresentation,
        Nothing
        > = CoolingDataState.Initial,
    val fanOutputCount: Int = 0,
    val temperatureSensorCount: Int = 0
) {
    private val controlPresentation: CoolingControlPresentation?
        get() = controlState.authoritativeValueOrNull

    private val controlFreshness: CoolingDataFreshness?
        get() = when (val state = controlState) {
            is CoolingDataState.Content -> state.freshness
            is CoolingDataState.Empty -> state.freshness
            CoolingDataState.Initial,
            CoolingDataState.Loading,
            CoolingDataState.Unavailable,
            CoolingDataState.Unsupported,
            is CoolingDataState.OperationError -> null
        }

    val controlAvailable: Boolean
        get() = controlFreshness == CoolingDataFreshness.CURRENT

    val controlWriteEnabled: Boolean
        get() = controlAvailable && controlMutationState != CoolingMutationState.Saving

    val selectedMode: CoolingControlMode?
        get() = controlPresentation?.selectedMode

    val supportedModes: Set<CoolingControlMode>
        get() = controlPresentation?.supportedModes.orEmpty()

    val modeSelectionWritable: Boolean
        get() = controlWriteEnabled && controlPresentation?.modeSelectionWritable == true

    val manualFanPercent: Int?
        get() = controlPresentation?.manualFanPercent

    val fanPercentNow: Int?
        get() = controlPresentation?.actualFanPercent

    val tankTemperatureC: Double?
        get() = controlPresentation?.tankTemperatureC

    val autoStartTemperatureC: Double?
        get() = automaticSummaryState.authoritativeValueOrNull?.startTemperatureC

    val autoMaxTemperatureC: Double?
        get() = automaticSummaryState.authoritativeValueOrNull?.maximumSpeedTemperatureC

    val temperatureHistoryC: List<Double>
        get() = historyState.authoritativeValueOrNull?.temperaturesC.orEmpty()

    private val dashboardOverview: CoolingDashboardOverviewPresentation?
        get() = dashboardOverviewState.authoritativeValueOrNull

    val roomTemperatureC: Double?
        get() = dashboardOverview?.roomTemperatureC

    val humidityPercent: Double?
        get() = dashboardOverview?.humidityPercent

    val powerWatts: Double?
        get() = dashboardOverview?.powerWatts

    val estimatedKwhPerDay: Double?
        get() = dashboardOverview?.estimatedKwhPerDay

    val roomTemperatureHistoryC: List<Double>
        get() = dashboardOverview?.roomTemperatureHistoryC.orEmpty()

    val programSlotCount: Int?
        get() = dashboardOverview?.programSlotCount

    val nextProgramStartMinutesOfDay: Int?
        get() = dashboardOverview?.nextProgramStartMinutesOfDay

    val fanHealth: CoolingHealthState
        get() = dashboardOverview?.fanHealth ?: CoolingHealthState.UNKNOWN

    val sensorHealth: CoolingHealthState
        get() = dashboardOverview?.sensorHealth ?: CoolingHealthState.UNKNOWN

    val activeAlarmCount: Int?
        get() = dashboardOverview?.activeAlarmCount
}

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmCode
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSeverity
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlReason
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingProgramRuntimeSnapshot
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
    val actualFanPercent: Double?,
    val tankTemperatureC: Double?,
    val operatingState: DeviceCoolingOperatingState? = null,
    val controlReason: DeviceCoolingControlReason = DeviceCoolingControlReason.UNKNOWN,
    val targetFanPercent: Double? = null,
    val manualActive: Boolean? = null,
    val programRuntime: DeviceCoolingProgramRuntimeSnapshot? = null
)

data class CoolingAutomaticSummaryPresentation(
    val startTemperatureC: Double,
    val maximumSpeedTemperatureC: Double
)

data class CoolingHistoryOverviewPresentation(
    val generatedAtEpochMillis: Long,
    val points: List<DeviceCoolingTemperatureHistoryPoint>
)

enum class CoolingHealthState {
    READY,
    WARNING,
    FAULT,
    UNKNOWN
}

data class CoolingDashboardOverviewPresentation(
    val roomTemperatureC: Double? = null,
    val humidityPercent: Double? = null,
    val powerWatts: Double? = null,
    val estimatedKwhPerDay: Double? = null,
    val programSlotCount: Int? = null,
    val fanHealth: CoolingHealthState = CoolingHealthState.UNKNOWN,
    val sensorHealth: CoolingHealthState = CoolingHealthState.UNKNOWN,
    val activeAlarmCount: Int? = null,
    val highestAlarmSeverity: DeviceCoolingAlarmSeverity = DeviceCoolingAlarmSeverity.UNKNOWN,
    val activeAlarmCodes: List<DeviceCoolingAlarmCode> = emptyList()
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
    val temperatureTimelineState: CoolingTemperatureTimelinePresentation =
        CoolingTemperatureTimelinePresentation(),
    val dashboardOverviewState: CoolingDataState<
        CoolingDashboardOverviewPresentation,
        Nothing
        > = CoolingDataState.Initial,
    val surfacePreparationPending: Boolean = false,
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

    val showGlobalLoading: Boolean
        get() = (surfacePreparationPending && !controlAvailable) ||
            controlMutationState == CoolingMutationState.Saving

    val selectedMode: CoolingControlMode?
        get() = controlPresentation?.selectedMode

    val supportedModes: Set<CoolingControlMode>
        get() = controlPresentation?.supportedModes.orEmpty()

    val modeSelectionWritable: Boolean
        get() = controlWriteEnabled && controlPresentation?.modeSelectionWritable == true

    val manualFanPercent: Int?
        get() = controlPresentation?.manualFanPercent

    val fanPercentNow: Double?
        get() = controlPresentation?.actualFanPercent

    val tankTemperatureC: Double?
        get() = controlPresentation?.tankTemperatureC

    val operatingState: DeviceCoolingOperatingState?
        get() = controlPresentation?.operatingState

    val autoStartTemperatureC: Double?
        get() = automaticSummaryState.authoritativeValueOrNull?.startTemperatureC

    val autoMaxTemperatureC: Double?
        get() = automaticSummaryState.authoritativeValueOrNull?.maximumSpeedTemperatureC

    val temperatureHistoryPoints: List<DeviceCoolingTemperatureHistoryPoint>
        get() = historyState.authoritativeValueOrNull?.points.orEmpty()

    val temperatureHistoryGeneratedAtEpochMillis: Long?
        get() = historyState.authoritativeValueOrNull?.generatedAtEpochMillis

    val temperatureHistoryC: List<Double>
        get() = temperatureHistoryPoints.map { point -> point.temperatureC }

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

    val programSlotCount: Int?
        get() = dashboardOverview?.programSlotCount

    val fanHealth: CoolingHealthState
        get() = dashboardOverview?.fanHealth ?: CoolingHealthState.UNKNOWN

    val sensorHealth: CoolingHealthState
        get() = dashboardOverview?.sensorHealth ?: CoolingHealthState.UNKNOWN

    val activeAlarmCount: Int?
        get() = dashboardOverview?.activeAlarmCount

    val activeAlarmCodes: List<DeviceCoolingAlarmCode>
        get() = dashboardOverview?.activeAlarmCodes.orEmpty()
}

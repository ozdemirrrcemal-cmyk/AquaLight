package com.aqua.aqualight.ui.tabs.devices.detail.cooling.root

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingMutationState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.authoritativeValueOrNull

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
    val roomTemperatureC: Double? = null,
    val humidityPercent: Double? = null,
    val powerWatts: Double? = null,
    val estimatedKwhPerDay: Double? = null,
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

    val manualFanCapabilities: DeviceCoolingManualFanCapabilities?
        get() = controlPresentation?.manualFanCapabilities?.let { capabilities ->
            if (controlWriteEnabled) capabilities else capabilities.copy(writable = false)
        }

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
}

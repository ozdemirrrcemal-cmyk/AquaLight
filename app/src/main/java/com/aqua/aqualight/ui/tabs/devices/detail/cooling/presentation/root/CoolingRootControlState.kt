package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState

internal fun DeviceCoolingControlSnapshot.toRootControlPresentation(): CoolingControlPresentation =
    CoolingControlPresentation(
        selectedMode = mode,
        supportedModes = capabilities.supportedModes,
        modeSelectionWritable = capabilities.modeSelectionWritable,
        manualFanCapabilities = capabilities.manualFan,
        manualFanPercent = manualFanPercent,
        actualFanPercent = actualFanPercent,
        tankTemperatureC = tankTemperatureC
    )

internal fun DeviceCoolingControlResult.toRootControlState(
    previous: CoolingDataState<CoolingControlPresentation, DeviceCoolingControlFailure>
): CoolingDataState<CoolingControlPresentation, DeviceCoolingControlFailure> = when (this) {
    is DeviceCoolingControlResult.Available -> CoolingDataState.Content(
        value = snapshot.toRootControlPresentation()
    )
    is DeviceCoolingControlResult.Failed -> previous.afterControlReadFailure(failure)
}

internal fun DeviceCoolingControlResult.toRootDashboardOverviewState(
    previous: CoolingDataState<CoolingDashboardOverviewPresentation, Nothing>
): CoolingDataState<CoolingDashboardOverviewPresentation, Nothing> = when (this) {
    is DeviceCoolingControlResult.Available -> snapshot.telemetry
        ?.toRootDashboardOverview()
        ?.let { overview -> CoolingDataState.Content(overview) }
        ?: previous
    is DeviceCoolingControlResult.Failed -> previous
}

internal fun CoolingDataState<CoolingControlPresentation, DeviceCoolingControlFailure>
    .beginControlRefresh(): CoolingDataState<CoolingControlPresentation, DeviceCoolingControlFailure> =
    when (this) {
        is CoolingDataState.Content -> copy(
            freshness = CoolingDataFreshness.REFRESHING,
            refreshFailure = null
        )
        is CoolingDataState.Empty -> copy(
            freshness = CoolingDataFreshness.REFRESHING,
            refreshFailure = null
        )
        CoolingDataState.Initial,
        CoolingDataState.Loading,
        CoolingDataState.Unavailable,
        CoolingDataState.Unsupported,
        is CoolingDataState.OperationError -> CoolingDataState.Loading
    }

private fun DeviceCoolingTelemetrySnapshot.toRootDashboardOverview():
    CoolingDashboardOverviewPresentation {
    val active = activeAlarms
    return CoolingDashboardOverviewPresentation(
        roomTemperatureC = roomTemperatureC,
        humidityPercent = humidityPercent,
        powerWatts = powerWatts,
        estimatedKwhPerDay = estimatedKwhPerDay,
        fanHealth = when (fanHealth) {
            DeviceCoolingFanHealth.UNVERIFIED,
            DeviceCoolingFanHealth.UNKNOWN -> CoolingHealthState.UNKNOWN
            DeviceCoolingFanHealth.HARDWARE_FAULT -> CoolingHealthState.FAULT
        },
        sensorHealth = when (sensorHealth) {
            DeviceCoolingSensorHealth.OK -> CoolingHealthState.READY
            DeviceCoolingSensorHealth.WARNING -> CoolingHealthState.WARNING
            DeviceCoolingSensorHealth.CRITICAL -> CoolingHealthState.FAULT
            DeviceCoolingSensorHealth.UNKNOWN -> CoolingHealthState.UNKNOWN
        },
        activeAlarmCount = active.size,
        activeAlarmCodes = active.map { alarm -> alarm.code }
    )
}

private fun CoolingDataState<CoolingControlPresentation, DeviceCoolingControlFailure>
    .afterControlReadFailure(
    failure: DeviceCoolingControlFailure
): CoolingDataState<CoolingControlPresentation, DeviceCoolingControlFailure> = when (failure) {
    DeviceCoolingControlFailure.Unsupported -> CoolingDataState.Unsupported
    DeviceCoolingControlFailure.Unavailable,
    DeviceCoolingControlFailure.NotConnected,
    is DeviceCoolingControlFailure.Rejected,
    DeviceCoolingControlFailure.InvalidData -> preserveControlOrResolveFailure(failure)
}

private fun CoolingDataState<CoolingControlPresentation, DeviceCoolingControlFailure>
    .preserveControlOrResolveFailure(
    failure: DeviceCoolingControlFailure
): CoolingDataState<CoolingControlPresentation, DeviceCoolingControlFailure> = when (this) {
    is CoolingDataState.Content -> copy(
        freshness = CoolingDataFreshness.STALE,
        refreshFailure = failure
    )
    is CoolingDataState.Empty -> copy(
        freshness = CoolingDataFreshness.STALE,
        refreshFailure = failure
    )
    CoolingDataState.Initial,
    CoolingDataState.Loading,
    CoolingDataState.Unavailable,
    CoolingDataState.Unsupported,
    is CoolingDataState.OperationError -> failure.toControlTerminalState()
}

private fun DeviceCoolingControlFailure.toControlTerminalState(): CoolingDataState<
    CoolingControlPresentation,
    DeviceCoolingControlFailure
    > = when (this) {
    DeviceCoolingControlFailure.Unsupported -> CoolingDataState.Unsupported
    DeviceCoolingControlFailure.Unavailable,
    DeviceCoolingControlFailure.NotConnected -> CoolingDataState.Unavailable
    is DeviceCoolingControlFailure.Rejected,
    DeviceCoolingControlFailure.InvalidData -> CoolingDataState.OperationError(this)
}

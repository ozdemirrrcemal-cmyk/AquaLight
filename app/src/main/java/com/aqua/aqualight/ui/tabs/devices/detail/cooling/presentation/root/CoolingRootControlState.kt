package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

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

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState

internal fun DeviceCoolingAutomaticSettingsSnapshot.toRootAutomaticState(
    previous: CoolingDataState<CoolingAutomaticSummaryPresentation, DeviceCoolingAutomaticFailure>
): CoolingDataState<CoolingAutomaticSummaryPresentation, DeviceCoolingAutomaticFailure> {
    val summary = toRootAutomaticSummaryOrNull()
    return when {
        !loaded -> previous.keepAutomaticValueOrLoading()
        !available -> CoolingDataState.Unsupported
        summary == null -> previous.afterAutomaticReadFailure(
            DeviceCoolingAutomaticFailure.InvalidConfiguration
        )
        else -> CoolingDataState.Content(summary)
    }
}

internal fun DeviceCoolingAutomaticSettingsSnapshot.toRootAutomaticStateAfterRefresh(
    previous: CoolingDataState<CoolingAutomaticSummaryPresentation, DeviceCoolingAutomaticFailure>
): CoolingDataState<CoolingAutomaticSummaryPresentation, DeviceCoolingAutomaticFailure> =
    if (loaded) {
        toRootAutomaticState(previous)
    } else {
        previous.afterAutomaticReadFailure(DeviceCoolingAutomaticFailure.Unavailable)
    }

private fun DeviceCoolingAutomaticSettingsSnapshot.toRootAutomaticSummaryOrNull():
    CoolingAutomaticSummaryPresentation? = startTemperatureC
    ?.takeIf(Double::isFinite)
    ?.let { start ->
        maximumSpeedTemperatureC
            ?.takeIf(Double::isFinite)
            ?.let { maximum ->
                CoolingAutomaticSummaryPresentation(
                    startTemperatureC = start,
                    maximumSpeedTemperatureC = maximum
                )
            }
    }

internal fun CoolingDataState<
    CoolingAutomaticSummaryPresentation,
    DeviceCoolingAutomaticFailure
    >.beginAutomaticRefresh(): CoolingDataState<
    CoolingAutomaticSummaryPresentation,
    DeviceCoolingAutomaticFailure
    > = when (this) {
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

internal fun CoolingDataState<
    CoolingAutomaticSummaryPresentation,
    DeviceCoolingAutomaticFailure
    >.afterAutomaticReadFailure(
    failure: DeviceCoolingAutomaticFailure
): CoolingDataState<CoolingAutomaticSummaryPresentation, DeviceCoolingAutomaticFailure> = when (failure) {
    DeviceCoolingAutomaticFailure.Unsupported -> CoolingDataState.Unsupported
    DeviceCoolingAutomaticFailure.Unavailable,
    DeviceCoolingAutomaticFailure.NotConnected,
    DeviceCoolingAutomaticFailure.TemporaryFailure,
    DeviceCoolingAutomaticFailure.ReadOnly,
    DeviceCoolingAutomaticFailure.InvalidConfiguration,
    DeviceCoolingAutomaticFailure.Rejected -> preserveAutomaticOrResolveFailure(failure)
}

private fun CoolingDataState<
    CoolingAutomaticSummaryPresentation,
    DeviceCoolingAutomaticFailure
    >.preserveAutomaticOrResolveFailure(
    failure: DeviceCoolingAutomaticFailure
): CoolingDataState<CoolingAutomaticSummaryPresentation, DeviceCoolingAutomaticFailure> = when (this) {
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
    is CoolingDataState.OperationError -> failure.toAutomaticTerminalState()
}

private fun CoolingDataState<CoolingAutomaticSummaryPresentation, DeviceCoolingAutomaticFailure>
    .keepAutomaticValueOrLoading(): CoolingDataState<
    CoolingAutomaticSummaryPresentation,
    DeviceCoolingAutomaticFailure
    > = when (this) {
    is CoolingDataState.Content,
    is CoolingDataState.Empty -> this
    CoolingDataState.Initial,
    CoolingDataState.Loading,
    CoolingDataState.Unavailable,
    CoolingDataState.Unsupported,
    is CoolingDataState.OperationError -> CoolingDataState.Loading
}

private fun DeviceCoolingAutomaticFailure.toAutomaticTerminalState(): CoolingDataState<
    CoolingAutomaticSummaryPresentation,
    DeviceCoolingAutomaticFailure
    > = when (this) {
    DeviceCoolingAutomaticFailure.Unsupported -> CoolingDataState.Unsupported
    DeviceCoolingAutomaticFailure.Unavailable,
    DeviceCoolingAutomaticFailure.NotConnected,
    DeviceCoolingAutomaticFailure.TemporaryFailure -> CoolingDataState.Unavailable
    DeviceCoolingAutomaticFailure.ReadOnly,
    DeviceCoolingAutomaticFailure.InvalidConfiguration,
    DeviceCoolingAutomaticFailure.Rejected -> CoolingDataState.OperationError(this)
}

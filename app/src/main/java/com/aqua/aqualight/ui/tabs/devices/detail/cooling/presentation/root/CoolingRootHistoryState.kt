package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingHistoryOverviewPresentation

internal fun DeviceCoolingTemperatureHistoryLoadResult.toRootHistoryState(
    previous: CoolingDataState<CoolingHistoryOverviewPresentation, Nothing> =
        CoolingDataState.Initial
): CoolingDataState<CoolingHistoryOverviewPresentation, Nothing> = when (this) {
    is DeviceCoolingTemperatureHistoryLoadResult.Loaded -> {
        val presentation = CoolingHistoryOverviewPresentation(
            generatedAtEpochMillis = snapshot.generatedAtEpochMillis,
            points = snapshot.points
        )
        if (presentation.points.isEmpty()) {
            CoolingDataState.Empty(presentation)
        } else {
            CoolingDataState.Content(presentation)
        }
    }
    DeviceCoolingTemperatureHistoryLoadResult.Unsupported -> CoolingDataState.Unsupported
    DeviceCoolingTemperatureHistoryLoadResult.Unavailable,
    is DeviceCoolingTemperatureHistoryLoadResult.Rejected -> previous.preserveHistoryOrUnavailable()
}

private fun CoolingDataState<CoolingHistoryOverviewPresentation, Nothing>
    .preserveHistoryOrUnavailable():
    CoolingDataState<CoolingHistoryOverviewPresentation, Nothing> = when (this) {
    is CoolingDataState.Content -> copy(freshness = CoolingDataFreshness.STALE)
    is CoolingDataState.Empty -> copy(freshness = CoolingDataFreshness.STALE)
    CoolingDataState.Initial,
    CoolingDataState.Loading,
    CoolingDataState.Unavailable,
    CoolingDataState.Unsupported,
    is CoolingDataState.OperationError -> CoolingDataState.Unavailable
}

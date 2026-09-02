package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState

internal fun DeviceCoolingTemperatureHistoryLoadResult.toRootHistoryState(): CoolingDataState<
    CoolingHistoryOverviewPresentation,
    Nothing
    > = when (this) {
    is DeviceCoolingTemperatureHistoryLoadResult.Loaded -> {
        val presentation = CoolingHistoryOverviewPresentation(
            temperaturesC = snapshot.points.map { point -> point.temperatureC }
        )
        if (presentation.temperaturesC.isEmpty()) {
            CoolingDataState.Empty(presentation)
        } else {
            CoolingDataState.Content(presentation)
        }
    }
    DeviceCoolingTemperatureHistoryLoadResult.Unsupported -> CoolingDataState.Unsupported
    DeviceCoolingTemperatureHistoryLoadResult.Unavailable -> CoolingDataState.Unavailable
}

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common

data class CoolingLiveTemperaturePointPresentation(
    val inputSampleSequence: Long,
    val sampledAtUptimeMillis: Long,
    val evaluatedAtUptimeMillis: Long,
    val temperatureC: Double
)

data class CoolingTemperatureTimelinePresentation(
    val timeGeneration: Long? = null,
    val lastInputSampleSequence: Long? = null,
    val historyAnchorEpochMillis: Long? = null,
    val historyAnchorEvaluatedAtUptimeMillis: Long? = null,
    val committedLivePoints: List<CoolingLiveTemperaturePointPresentation> = emptyList(),
    val currentLivePoint: CoolingLiveTemperaturePointPresentation? = null
)

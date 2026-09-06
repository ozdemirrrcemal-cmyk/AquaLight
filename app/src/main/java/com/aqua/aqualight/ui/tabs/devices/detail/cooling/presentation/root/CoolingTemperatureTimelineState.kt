package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingWaterTemperatureSample

private const val LIVE_POINT_INTERVAL_MILLIS = 5L * 60L * 1_000L
private const val LIVE_WINDOW_MILLIS = 24L * 60L * 60L * 1_000L
private const val MAXIMUM_COMMITTED_LIVE_POINTS = 289

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

internal data class CoolingTemperatureTimelineUpdate(
    val state: CoolingTemperatureTimelinePresentation,
    val sourceReset: Boolean
)

internal fun CoolingTemperatureTimelinePresentation.accept(
    sample: DeviceCoolingWaterTemperatureSample
): CoolingTemperatureTimelineUpdate {
    val previous = currentLivePoint
    val sourceReset = previous != null && (
        timeGeneration != sample.timeGeneration ||
            sample.evaluatedAtUptimeMillis < previous.evaluatedAtUptimeMillis ||
            sample.sampledAtUptimeMillis < previous.sampledAtUptimeMillis
        )
    val base = if (sourceReset) CoolingTemperatureTimelinePresentation() else this
    if (
        !sourceReset &&
        base.lastInputSampleSequence == sample.inputSampleSequence &&
        base.currentLivePoint?.sampledAtUptimeMillis == sample.sampledAtUptimeMillis
    ) {
        return CoolingTemperatureTimelineUpdate(base, sourceReset = false)
    }

    val point = CoolingLiveTemperaturePointPresentation(
        inputSampleSequence = sample.inputSampleSequence,
        sampledAtUptimeMillis = sample.sampledAtUptimeMillis,
        evaluatedAtUptimeMillis = sample.evaluatedAtUptimeMillis,
        temperatureC = sample.temperatureC
    )
    val lastCommitted = base.committedLivePoints.lastOrNull()
    val committed = when {
        lastCommitted == null -> listOf(point)
        point.sampledAtUptimeMillis - lastCommitted.sampledAtUptimeMillis >=
            LIVE_POINT_INTERVAL_MILLIS -> base.committedLivePoints + point
        else -> base.committedLivePoints
    }.filter { committedPoint ->
        point.sampledAtUptimeMillis - committedPoint.sampledAtUptimeMillis <= LIVE_WINDOW_MILLIS
    }.takeLast(MAXIMUM_COMMITTED_LIVE_POINTS)

    val anchorEvaluatedAt = base.historyAnchorEvaluatedAtUptimeMillis
        ?: base.historyAnchorEpochMillis?.let { sample.evaluatedAtUptimeMillis }

    return CoolingTemperatureTimelineUpdate(
        state = base.copy(
            timeGeneration = sample.timeGeneration,
            lastInputSampleSequence = sample.inputSampleSequence,
            historyAnchorEvaluatedAtUptimeMillis = anchorEvaluatedAt,
            committedLivePoints = committed,
            currentLivePoint = point
        ),
        sourceReset = sourceReset
    )
}

internal fun CoolingTemperatureTimelinePresentation.withHistoryAnchor(
    generatedAtEpochMillis: Long
): CoolingTemperatureTimelinePresentation = copy(
    historyAnchorEpochMillis = generatedAtEpochMillis,
    historyAnchorEvaluatedAtUptimeMillis = currentLivePoint?.evaluatedAtUptimeMillis
)

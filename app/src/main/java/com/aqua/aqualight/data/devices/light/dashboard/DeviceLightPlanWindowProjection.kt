package com.aqua.aqualight.data.devices.light.dashboard

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanWindowSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomDocument
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightGraph
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightGraphReason
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode

/** Projects an active schedule window only from one coherent Light graph/document frame. */
internal fun DeviceLightGraph.toApplicationActiveWindow(
    activeProgramId: String?,
    customDocument: DeviceLightCustomDocument?
): DeviceLightPlanWindowSnapshot? = when (mode) {
    DeviceLightMode.AUTO -> autoActiveWindow(activeProgramId)
    DeviceLightMode.CUSTOM -> customActiveWindow(customDocument)
    DeviceLightMode.MANUAL -> null
}

private fun DeviceLightGraph.autoActiveWindow(
    activeProgramId: String?
): DeviceLightPlanWindowSnapshot? = if (!hasValidSchedule()) {
    null
} else {
    val span = activeProgramId
        ?.let { programId -> autoSpans.firstOrNull { it.programId == programId } }
        ?: autoSpans.singleOrNull()
    span?.let { resolved ->
        DeviceLightPlanWindowSnapshot(
            startTimeMs = resolved.startTimeMsWithinToday,
            endTimeMs = resolved.endTimeMsWithinToday
        )
    }
}

private fun DeviceLightGraph.customActiveWindow(
    customDocument: DeviceLightCustomDocument?
): DeviceLightPlanWindowSnapshot? = customDocument
    ?.takeIf { document -> hasValidSchedule() && document.isCompleteSchedule() }
    ?.points
    ?.let { points ->
        DeviceLightPlanWindowSnapshot(
            startTimeMs = points.first().timeMs,
            endTimeMs = points.last().timeMs
        )
    }

private fun DeviceLightGraph.hasValidSchedule(): Boolean =
    available &&
        reason == DeviceLightGraphReason.OK &&
        hasScheduleToday

private fun DeviceLightCustomDocument.isCompleteSchedule(): Boolean =
    installed &&
        pointCount == points.size &&
        points.isNotEmpty()

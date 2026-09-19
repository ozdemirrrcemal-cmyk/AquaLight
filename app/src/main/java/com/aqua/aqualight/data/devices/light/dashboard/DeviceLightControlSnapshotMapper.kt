package com.aqua.aqualight.data.devices.light.dashboard

import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightAdaptationSummary
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightOutputCondition
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanPointSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightSystemSummary
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSnapshot
import com.aqua.aqualight.data.devices.light.supportsLightAdaptation
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationState
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomDocument
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightGraph
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightGraphReason
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightOutputReason
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus

internal fun DeviceLightStatus.toControlSnapshot(
    deviceUid: DeviceUid,
    graph: DeviceLightGraph,
    customDocument: DeviceLightCustomDocument?,
    systemSupported: Boolean,
    systemSnapshot: DeviceLightSystemSnapshot?
) = DeviceLightControlSnapshot(
    deviceUid = deviceUid.value,
    productKey = product.wireValue,
    physicalChannelCount = runtime.physicalChannelCount,
    channelKeys = channels.sortedBy { channel -> channel.order }.map { channel -> channel.key },
    activeAutomaticProgramId = auto.activeProgramId,
    hero = DeviceLightHeroSnapshot(
        mode = mode.toApplicationMode(),
        outputActive = outputActive,
        outputCondition = outputReason.toApplicationCondition(),
        outputHealthy = runtime.physicalOutputHealthy,
        estimatedPowerWatts = power.estimatedLedPowerW
            ?.takeIf { power.available },
        estimatedColorTemperatureKelvin = color.estimatedCctK
            ?.takeIf { color.available && color.cctAvailable }
    ),
    adaptation = DeviceLightAdaptationSummary(
        supported = supportsLightAdaptation(),
        state = acclimation.state?.toApplicationState(),
        currentPermille = acclimation.currentPermille,
        remainingSeconds = acclimation.remainingSeconds
    ),
    systemSupported = systemSupported,
    system = systemSnapshot?.toDashboardSummary(),
    channels = channels.sortedBy { channel -> channel.order }.map { channel ->
        DeviceLightChannelOutputSnapshot(
            key = channel.key,
            displayName = channel.displayName,
            displayColorRgb = channel.displayColorRgb,
            effectivePercent = effective.percents.getValue(channel.percentField)
        )
    },
    plan = DeviceLightPlanSnapshot(
        available = graph.available,
        reason = graph.reason.toApplicationReason(),
        nowTimeMs = graph.nowTimeMs,
        channelScale = graph.channelScale,
        hasScheduleToday = graph.hasScheduleToday,
        points = graph.points.map { point ->
            DeviceLightPlanPointSnapshot(
                timeMs = point.timeMs,
                channelLevels = point.channelPermille
            )
        },
        activeWindow = graph.toApplicationActiveWindow(auto.activeProgramId, customDocument)
    ),
    automaticProgramCount = auto.programCount,
    customCurvePointCount = custom.pointCount
)

private fun DeviceLightSystemSnapshot.toDashboardSummary() = DeviceLightSystemSummary(
    temperatureCelsius = temperatureCelsius,
    fanPercents = fans.map { fan -> fan.percent },
    condition = condition
)

private fun DeviceLightGraphReason.toApplicationReason(): DeviceLightPlanReason = when (this) {
    DeviceLightGraphReason.OK -> DeviceLightPlanReason.OK
    DeviceLightGraphReason.MODE_HAS_NO_SCHEDULE ->
        DeviceLightPlanReason.MODE_HAS_NO_SCHEDULE
    DeviceLightGraphReason.RTC_NOT_READY -> DeviceLightPlanReason.RTC_NOT_READY
    DeviceLightGraphReason.NO_ENABLED_AUTO_PROGRAM_TODAY ->
        DeviceLightPlanReason.NO_ENABLED_AUTO_PROGRAM_TODAY
    DeviceLightGraphReason.CUSTOM_NOT_INSTALLED -> DeviceLightPlanReason.CUSTOM_NOT_INSTALLED
    DeviceLightGraphReason.CUSTOM_NOT_SCHEDULED_TODAY ->
        DeviceLightPlanReason.CUSTOM_NOT_SCHEDULED_TODAY
}

private fun DeviceLightAcclimationState.toApplicationState(): DeviceLightAdaptationState =
    when (this) {
        DeviceLightAcclimationState.DISABLED -> DeviceLightAdaptationState.DISABLED
        DeviceLightAcclimationState.ACTIVE -> DeviceLightAdaptationState.ACTIVE
        DeviceLightAcclimationState.COMPLETED -> DeviceLightAdaptationState.COMPLETED
    }

private fun DeviceLightMode.toApplicationMode(): DeviceLightControlMode = when (this) {
    DeviceLightMode.MANUAL -> DeviceLightControlMode.MANUAL
    DeviceLightMode.AUTO -> DeviceLightControlMode.AUTOMATIC
    DeviceLightMode.CUSTOM -> DeviceLightControlMode.CUSTOM
}

private fun DeviceLightOutputReason.toApplicationCondition(): DeviceLightOutputCondition =
    when (this) {
        DeviceLightOutputReason.ACTIVE -> DeviceLightOutputCondition.ACTIVE
        DeviceLightOutputReason.SCHEDULED_OFF -> DeviceLightOutputCondition.SCHEDULED_OFF
        DeviceLightOutputReason.ALL_CHANNELS_ZERO ->
            DeviceLightOutputCondition.ALL_CHANNELS_ZERO
        DeviceLightOutputReason.RTC_NOT_READY -> DeviceLightOutputCondition.CLOCK_UNAVAILABLE
        DeviceLightOutputReason.THERMAL_SHUTDOWN ->
            DeviceLightOutputCondition.THERMAL_PROTECTION
        DeviceLightOutputReason.POWER_LIMITED -> DeviceLightOutputCondition.POWER_LIMITED
        DeviceLightOutputReason.HARDWARE_FAULT -> DeviceLightOutputCondition.HARDWARE_FAULT
    }

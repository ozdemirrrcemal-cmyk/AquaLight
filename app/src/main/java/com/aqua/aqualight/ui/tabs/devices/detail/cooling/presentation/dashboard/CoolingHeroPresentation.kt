package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingControlMode
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingHealthState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.DeviceCoolingRootUiState

internal enum class CoolingHeroVisualStatus {
    COOLING,
    STANDBY,
    ATTENTION,
    WAITING_FOR_DATA,
    OFFLINE
}

internal data class CoolingHeroPresentation(
    val status: CoolingHeroVisualStatus,
    val fanPercent: Int?,
    val temperatureC: Double?,
    val mode: CoolingControlMode?,
    val isCooling: Boolean
) {
    val motionIntensity: Float
        get() = if (isCooling) {
            fanPercent.orZero().coerceIn(MINIMUM_PERCENT, MAXIMUM_PERCENT) / MAXIMUM_PERCENT_FLOAT
        } else {
            NO_MOTION
        }
}

internal data class CoolingHeroMotion(
    val isActive: Boolean,
    val intensity: Float
)

internal fun CoolingHeroPresentation.resolveMotion(allowWaitingMotion: Boolean): CoolingHeroMotion {
    val waitingMotion = allowWaitingMotion && status == CoolingHeroVisualStatus.WAITING_FOR_DATA
    return CoolingHeroMotion(
        isActive = isCooling || waitingMotion,
        intensity = if (waitingMotion) WAITING_MOTION_INTENSITY else motionIntensity
    )
}

internal fun DeviceCoolingRootUiState.toCoolingHeroPresentation(): CoolingHeroPresentation {
    return CoolingHeroPresentation(
        status = resolveCoolingHeroStatus(),
        fanPercent = fanPercentNow,
        temperatureC = tankTemperatureC,
        mode = selectedMode,
        isCooling = hasCurrentFanMotion()
    )
}

private fun DeviceCoolingRootUiState.resolveCoolingHeroStatus(): CoolingHeroVisualStatus = when {
    connectionVisualState != DeviceConnectionVisualState.ONLINE || !contentEnabled ->
        CoolingHeroVisualStatus.OFFLINE
    hasCoolingAttentionState() -> CoolingHeroVisualStatus.ATTENTION
    !controlAvailable -> CoolingHeroVisualStatus.WAITING_FOR_DATA
    fanPercentNow.orZero() > MINIMUM_PERCENT -> CoolingHeroVisualStatus.COOLING
    else -> CoolingHeroVisualStatus.STANDBY
}

private fun DeviceCoolingRootUiState.hasCoolingAttentionState(): Boolean =
    fanHealth.requiresAttention() ||
        sensorHealth.requiresAttention() ||
        activeAlarmCount.orZero() > NO_ALARMS

private fun CoolingHealthState.requiresAttention(): Boolean =
    this == CoolingHealthState.FAULT || this == CoolingHealthState.WARNING

private fun DeviceCoolingRootUiState.hasCurrentFanMotion(): Boolean {
    val hasLiveControl = connectionVisualState == DeviceConnectionVisualState.ONLINE &&
        contentEnabled && controlAvailable
    return hasLiveControl && fanHealth != CoolingHealthState.FAULT &&
        fanPercentNow.orZero() > MINIMUM_PERCENT
}

private fun Int?.orZero(): Int = this ?: 0

private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100
private const val MAXIMUM_PERCENT_FLOAT = 100f
private const val NO_MOTION = 0f
private const val NO_ALARMS = 0
private const val WAITING_MOTION_INTENSITY = 0.58f

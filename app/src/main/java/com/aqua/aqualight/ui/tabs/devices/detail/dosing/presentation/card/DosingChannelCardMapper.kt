package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.DosingPumpVisualState

/** Catalog bootstrap shown only until the central firmware snapshot is available. */
internal fun DeviceDosingChannelSlot.toInitialDosingChannelCardUiState(): DosingChannelCardUiState =
    DosingChannelCardUiState(
        slotId = id.value,
        channelNumber = index.position,
        displayName = defaultDisplayName
    )

/** Complete firmware-authoritative card state; no catalog display value survives this mapping. */
internal fun DeviceDosingChannelSnapshot.toDosingChannelCardUiState(): DosingChannelCardUiState =
    DosingChannelCardUiState(
        slotId = slotId,
        channelNumber = channelNumber,
        displayName = channelTitle,
        visualState = toCardVisualState(),
        scheduleDays = toScheduleDaysUiState(),
        programProgress = toProgramProgressUiState(),
        reservoir = toReservoirUiState()
    )

internal fun DosingChannelCardUiState.withChannelSnapshot(
    snapshot: DeviceDosingChannelSnapshot?
): DosingChannelCardUiState = snapshot?.toDosingChannelCardUiState() ?: this

internal fun DeviceDosingChannelSnapshot.toPumpVisualState(): DosingPumpVisualState = when {
    hasAttentionState() -> DosingPumpVisualState.ERROR
    activeRun.active -> DosingPumpVisualState.RUNNING
    else -> DosingPumpVisualState.IDLE
}

private fun DeviceDosingChannelSnapshot.toScheduleDaysUiState() = DosingScheduleDaysUiState(
    selectedDays = ALL_DOSING_WEEKDAYS.zip(program?.weekdays.orEmpty())
        .mapNotNull { (weekday, selected) -> weekday.takeIf { selected } }
)

private fun DeviceDosingChannelSnapshot.toCardVisualState(): DosingChannelVisualState = when {
    !calibrated -> DosingChannelVisualState.NOT_CONFIGURED
    program == null -> DosingChannelVisualState.PROGRAM_NOT_CONFIGURED
    hasAttentionState() -> DosingChannelVisualState.ERROR
    !program.enabled -> DosingChannelVisualState.AUTOMATIC_DOSING_OFF
    activeRun.active -> DosingChannelVisualState.DOSING
    else -> DosingChannelVisualState.CONFIGURED
}

// Exact daily delivery may be unknown for one interrupted occurrence while
// firmware still owns a safe conservative reservoir balance and scheduler.
internal fun DeviceDosingChannelSnapshot.hasAttentionState(): Boolean =
    runtimeReason in ATTENTION_RUNTIME_REASONS ||
        reservoir.trackingEnabled && !reservoir.accountingCertain

internal fun Long.toMilliliters(): Double = toDouble() / MICROLITERS_PER_MILLILITER

private val ATTENTION_RUNTIME_REASONS = setOf(
    DeviceDosingRuntimeReason.INVALID_TIME,
    DeviceDosingRuntimeReason.RESERVOIR_UNAVAILABLE,
    DeviceDosingRuntimeReason.ACCOUNTING_UNCERTAIN,
    DeviceDosingRuntimeReason.UNSAFE_AFTER_CALIBRATION,
    DeviceDosingRuntimeReason.INVALID_PROGRAM,
    DeviceDosingRuntimeReason.UNKNOWN
)

private const val MICROLITERS_PER_MILLILITER = 1_000.0

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
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
        reservoir = toReservoirUiState(),
        setupState = toCardSetupState(),
        runtimeState = toCardRuntimeState(),
        activeRunSource = activeRun.source.toCardRunSource()
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

private fun DeviceDosingChannelSnapshot.toCardSetupState(): DosingChannelSetupUiState = when {
    !calibrated -> DosingChannelSetupUiState.CALIBRATION_REQUIRED
    program == null -> DosingChannelSetupUiState.PROGRAM_NOT_CONFIGURED
    !program.enabled -> DosingChannelSetupUiState.AUTOMATIC_DOSING_OFF
    else -> DosingChannelSetupUiState.CONFIGURED
}

private fun DeviceDosingChannelSnapshot.toCardRuntimeState(): DosingChannelRuntimeUiState = when {
    hasAttentionState() -> DosingChannelRuntimeUiState.ATTENTION
    activeRun.active -> DosingChannelRuntimeUiState.DOSING
    else -> DosingChannelRuntimeUiState.IDLE
}

private fun DeviceDosingChannelSnapshot.toCardVisualState(): DosingChannelVisualState = when {
    hasAttentionState() -> DosingChannelVisualState.ERROR
    !calibrated -> DosingChannelVisualState.NOT_CONFIGURED
    activeRun.active -> DosingChannelVisualState.DOSING
    program == null -> DosingChannelVisualState.PROGRAM_NOT_CONFIGURED
    !program.enabled -> DosingChannelVisualState.AUTOMATIC_DOSING_OFF
    else -> DosingChannelVisualState.CONFIGURED
}

private fun DeviceDosingRunSource.toCardRunSource(): DosingChannelRunSourceUiState = when (this) {
    DeviceDosingRunSource.NONE -> DosingChannelRunSourceUiState.NONE
    DeviceDosingRunSource.SCHEDULED -> DosingChannelRunSourceUiState.SCHEDULED
    DeviceDosingRunSource.MANUAL -> DosingChannelRunSourceUiState.MANUAL
    DeviceDosingRunSource.CALIBRATION -> DosingChannelRunSourceUiState.CALIBRATION
    DeviceDosingRunSource.VERIFICATION -> DosingChannelRunSourceUiState.VERIFICATION
    DeviceDosingRunSource.PRIME -> DosingChannelRunSourceUiState.PRIME
    DeviceDosingRunSource.UNKNOWN -> DosingChannelRunSourceUiState.UNKNOWN
}

internal fun DeviceDosingChannelSnapshot.hasAttentionState(): Boolean =
    !deliveryAccountingCertain ||
        runtimeReason in ATTENTION_RUNTIME_REASONS ||
        reservoir.trackingEnabled && !reservoir.accountingCertain

private val ATTENTION_RUNTIME_REASONS = setOf(
    DeviceDosingRuntimeReason.INVALID_TIME,
    DeviceDosingRuntimeReason.RESERVOIR_UNAVAILABLE,
    DeviceDosingRuntimeReason.ACCOUNTING_UNCERTAIN,
    DeviceDosingRuntimeReason.UNSAFE_AFTER_CALIBRATION,
    DeviceDosingRuntimeReason.INVALID_PROGRAM,
    DeviceDosingRuntimeReason.UNKNOWN
)

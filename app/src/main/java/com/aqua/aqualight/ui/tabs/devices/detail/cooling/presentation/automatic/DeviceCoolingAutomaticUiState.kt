package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.automatic

import com.aqua.aqualight.application.devices.cooling.DEVICE_COOLING_AUTOMATIC_SILENT_MODE_MAXIMUM_FAN_PERCENT
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import kotlin.math.abs

enum class DeviceCoolingAutomaticLoadState {
    INITIAL,
    LOADING,
    CONTENT,
    REFRESHING,
    UNSUPPORTED,
    UNAVAILABLE,
    OPERATION_ERROR
}

enum class DeviceCoolingAutomaticSaveState {
    IDLE,
    SAVING,
    SAVED,
    ERROR
}

data class DeviceCoolingAutomaticSettingsUiState(
    val deviceUid: String = "",
    val dataState: CoolingDataState<
        DeviceCoolingAutomaticSettingsSnapshot,
        DeviceCoolingAutomaticFailure
        > = CoolingDataState.Initial,
    val mutationState: CoolingMutationState<DeviceCoolingAutomaticFailure> =
        CoolingMutationState.Idle,
    val editable: Boolean = false,
    val persistedStartTemperatureC: Double? = null,
    val persistedMaximumSpeedTemperatureC: Double? = null,
    val draftStartTemperatureC: Double? = null,
    val draftMaximumSpeedTemperatureC: Double? = null,
    val persistedSilentModeEnabled: Boolean? = null,
    val draftSilentModeEnabled: Boolean = false,
    val silentModeMaximumFanPercent: Int =
        DEVICE_COOLING_AUTOMATIC_SILENT_MODE_MAXIMUM_FAN_PERCENT,
    val tankTemperatureC: Double? = null,
    val fanPercentNow: Double? = null,
    val policy: DeviceCoolingAutomaticTemperaturePolicy? = null
) {
    val loadState: DeviceCoolingAutomaticLoadState
        get() = dataState.toAutomaticLoadState()

    val saveState: DeviceCoolingAutomaticSaveState
        get() = mutationState.toAutomaticSaveState()

    val loadFailure: DeviceCoolingAutomaticFailure?
        get() = dataState.automaticLoadFailureOrNull()

    val saveFailure: DeviceCoolingAutomaticFailure?
        get() = mutationState.automaticSaveFailureOrNull()

    val hasFirmwareSnapshot: Boolean
        get() = persistedStartTemperatureC != null &&
            persistedMaximumSpeedTemperatureC != null &&
            policy != null

    val isCurrentAuthoritative: Boolean
        get() = when (val state = dataState) {
            is CoolingDataState.Content -> state.freshness == CoolingDataFreshness.CURRENT
            is CoolingDataState.Empty -> state.freshness == CoolingDataFreshness.CURRENT
            CoolingDataState.Initial,
            CoolingDataState.Loading,
            CoolingDataState.Unavailable,
            CoolingDataState.Unsupported,
            is CoolingDataState.OperationError -> false
        }

    val editorPolicy: DeviceCoolingAutomaticTemperaturePolicy?
        get() = policy

    val editorStartTemperatureC: Double?
        get() = draftStartTemperatureC

    val editorMaximumSpeedTemperatureC: Double?
        get() = draftMaximumSpeedTemperatureC

    val silentModeFirmwareBacked: Boolean
        get() = persistedSilentModeEnabled != null

    val silentModeEditable: Boolean
        get() = silentModeFirmwareBacked && editable && isCurrentAuthoritative

    val hasTemperatureChanges: Boolean
        get() = !sameTemperature(persistedStartTemperatureC, draftStartTemperatureC) ||
            !sameTemperature(
                persistedMaximumSpeedTemperatureC,
                draftMaximumSpeedTemperatureC
            )

    val hasSilentModeChanges: Boolean
        get() = persistedSilentModeEnabled?.let { persisted ->
            persisted != draftSilentModeEnabled
        } == true

    val hasChanges: Boolean
        get() = hasTemperatureChanges || hasSilentModeChanges

    private val hasSavePrerequisites: Boolean
        get() = isCurrentAuthoritative && editable && hasFirmwareSnapshot

    val canSave: Boolean
        get() = hasSavePrerequisites &&
            hasChanges &&
            mutationState != CoolingMutationState.Saving
}

private fun CoolingDataState<
    DeviceCoolingAutomaticSettingsSnapshot,
    DeviceCoolingAutomaticFailure
    >.toAutomaticLoadState(): DeviceCoolingAutomaticLoadState = when (this) {
    CoolingDataState.Initial -> DeviceCoolingAutomaticLoadState.INITIAL
    CoolingDataState.Loading -> DeviceCoolingAutomaticLoadState.LOADING
    is CoolingDataState.Content -> freshness.toAutomaticLoadedState()
    is CoolingDataState.Empty -> freshness.toAutomaticLoadedState()
    CoolingDataState.Unsupported -> DeviceCoolingAutomaticLoadState.UNSUPPORTED
    CoolingDataState.Unavailable -> DeviceCoolingAutomaticLoadState.UNAVAILABLE
    is CoolingDataState.OperationError -> DeviceCoolingAutomaticLoadState.OPERATION_ERROR
}

private fun CoolingDataFreshness.toAutomaticLoadedState(): DeviceCoolingAutomaticLoadState =
    if (this == CoolingDataFreshness.REFRESHING) {
        DeviceCoolingAutomaticLoadState.REFRESHING
    } else {
        DeviceCoolingAutomaticLoadState.CONTENT
    }

private fun CoolingMutationState<DeviceCoolingAutomaticFailure>.toAutomaticSaveState():
    DeviceCoolingAutomaticSaveState = when (this) {
    CoolingMutationState.Idle -> DeviceCoolingAutomaticSaveState.IDLE
    CoolingMutationState.Saving -> DeviceCoolingAutomaticSaveState.SAVING
    CoolingMutationState.Saved -> DeviceCoolingAutomaticSaveState.SAVED
    CoolingMutationState.ValidationError,
    is CoolingMutationState.OperationError -> DeviceCoolingAutomaticSaveState.IDLE
}

private fun CoolingDataState<
    DeviceCoolingAutomaticSettingsSnapshot,
    DeviceCoolingAutomaticFailure
    >.automaticLoadFailureOrNull(): DeviceCoolingAutomaticFailure? = when (this) {
    is CoolingDataState.Content -> refreshFailure
    is CoolingDataState.Empty -> refreshFailure
    CoolingDataState.Unsupported -> DeviceCoolingAutomaticFailure.Unsupported
    CoolingDataState.Unavailable -> DeviceCoolingAutomaticFailure.Unavailable
    is CoolingDataState.OperationError -> failure
    CoolingDataState.Initial,
    CoolingDataState.Loading -> null
}

private fun CoolingMutationState<DeviceCoolingAutomaticFailure>.automaticSaveFailureOrNull():
    DeviceCoolingAutomaticFailure? = when (this) {
    is CoolingMutationState.OperationError -> failure
    CoolingMutationState.ValidationError -> DeviceCoolingAutomaticFailure.InvalidConfiguration
    CoolingMutationState.Idle,
    CoolingMutationState.Saving,
    CoolingMutationState.Saved -> null
}

private fun sameTemperature(first: Double?, second: Double?): Boolean = when {
    first == null || second == null -> first == second
    else -> abs(first - second) <= TEMPERATURE_EPSILON
}

private const val TEMPERATURE_EPSILON = 0.000_001

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.automatic

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState

internal fun DeviceCoolingAutomaticSettingsUiState.beginRefresh():
    DeviceCoolingAutomaticSettingsUiState = copy(
    dataState = dataState.beginAutomaticDataRefresh(),
    editable = false
)

internal fun DeviceCoolingAutomaticSettingsUiState.afterRefreshFailure(
    failure: DeviceCoolingAutomaticFailure
): DeviceCoolingAutomaticSettingsUiState = if (
    failure == DeviceCoolingAutomaticFailure.Unsupported
) {
    clearAutomaticConfiguration(CoolingDataState.Unsupported)
} else {
    copy(
        dataState = dataState.preserveAutomaticOrResolveFailure(failure),
        editable = false
    )
}

internal fun DeviceCoolingAutomaticSettingsUiState.withSnapshot(
    snapshot: DeviceCoolingAutomaticSettingsSnapshot
): DeviceCoolingAutomaticSettingsUiState {
    val configuration = snapshot.completeConfiguration
    return when {
        !snapshot.loaded -> this
        !snapshot.available -> clearAutomaticConfiguration(
            dataState = CoolingDataState.Unsupported,
            snapshot = snapshot
        )
        configuration != null -> applyCompleteSnapshot(snapshot, configuration)
        hasFirmwareSnapshot -> copy(
            dataState = dataState.preserveAutomaticOrResolveFailure(
                DeviceCoolingAutomaticFailure.InvalidConfiguration
            ),
            editable = false
        )
        else -> clearAutomaticConfiguration(
            dataState = CoolingDataState.OperationError(
                DeviceCoolingAutomaticFailure.InvalidConfiguration
            ),
            snapshot = snapshot
        )
    }
}

private fun DeviceCoolingAutomaticSettingsUiState.applyCompleteSnapshot(
    snapshot: DeviceCoolingAutomaticSettingsSnapshot,
    configuration: AutomaticSnapshotConfiguration
): DeviceCoolingAutomaticSettingsUiState {
    val preserveTemperatureDraft = hasFirmwareSnapshot && hasTemperatureChanges
    val preserveSilentModeDraft = silentModeFirmwareBacked && hasSilentModeChanges
    val incomingSilentMode = snapshot.silentModeEnabled
    return copy(
        dataState = CoolingDataState.Content(snapshot),
        editable = snapshot.editable,
        persistedStartTemperatureC = configuration.startTemperatureC,
        persistedMaximumSpeedTemperatureC = configuration.maximumSpeedTemperatureC,
        draftStartTemperatureC = if (preserveTemperatureDraft) {
            draftStartTemperatureC
        } else {
            configuration.startTemperatureC
        },
        draftMaximumSpeedTemperatureC = if (preserveTemperatureDraft) {
            draftMaximumSpeedTemperatureC
        } else {
            configuration.maximumSpeedTemperatureC
        },
        persistedSilentModeEnabled = incomingSilentMode,
        draftSilentModeEnabled = when {
            incomingSilentMode == null -> draftSilentModeEnabled
            preserveSilentModeDraft -> draftSilentModeEnabled
            else -> incomingSilentMode
        },
        silentModeMaximumFanPercent = snapshot.silentModeMaximumFanPercent,
        tankTemperatureC = snapshot.tankTemperatureC,
        fanPercentNow = snapshot.fanPercentNow,
        operatingState = snapshot.operatingState,
        policy = configuration.policy
    )
}

private fun CoolingDataState<
    DeviceCoolingAutomaticSettingsSnapshot,
    DeviceCoolingAutomaticFailure
    >.beginAutomaticDataRefresh(): CoolingDataState<
    DeviceCoolingAutomaticSettingsSnapshot,
    DeviceCoolingAutomaticFailure
    > = when (this) {
    is CoolingDataState.Content -> copy(
        freshness = CoolingDataFreshness.REFRESHING,
        refreshFailure = null
    )
    is CoolingDataState.Empty -> copy(
        freshness = CoolingDataFreshness.REFRESHING,
        refreshFailure = null
    )
    CoolingDataState.Initial,
    CoolingDataState.Loading,
    CoolingDataState.Unavailable,
    CoolingDataState.Unsupported,
    is CoolingDataState.OperationError -> CoolingDataState.Loading
}

private fun CoolingDataState<
    DeviceCoolingAutomaticSettingsSnapshot,
    DeviceCoolingAutomaticFailure
    >.preserveAutomaticOrResolveFailure(
    failure: DeviceCoolingAutomaticFailure
): CoolingDataState<DeviceCoolingAutomaticSettingsSnapshot, DeviceCoolingAutomaticFailure> =
    when (this) {
        is CoolingDataState.Content -> copy(
            freshness = CoolingDataFreshness.STALE,
            refreshFailure = failure
        )
        is CoolingDataState.Empty -> copy(
            freshness = CoolingDataFreshness.STALE,
            refreshFailure = failure
        )
        CoolingDataState.Initial,
        CoolingDataState.Loading,
        CoolingDataState.Unavailable,
        CoolingDataState.Unsupported,
        is CoolingDataState.OperationError -> failure.toAutomaticTerminalDataState()
    }

private fun DeviceCoolingAutomaticSettingsUiState.clearAutomaticConfiguration(
    dataState: CoolingDataState<DeviceCoolingAutomaticSettingsSnapshot, DeviceCoolingAutomaticFailure>,
    snapshot: DeviceCoolingAutomaticSettingsSnapshot? = null
): DeviceCoolingAutomaticSettingsUiState = copy(
    dataState = dataState,
    editable = false,
    persistedStartTemperatureC = null,
    persistedMaximumSpeedTemperatureC = null,
    draftStartTemperatureC = null,
    draftMaximumSpeedTemperatureC = null,
    persistedSilentModeEnabled = snapshot?.silentModeEnabled,
    draftSilentModeEnabled = snapshot?.silentModeEnabled ?: false,
    silentModeMaximumFanPercent = snapshot?.silentModeMaximumFanPercent,
    tankTemperatureC = snapshot?.tankTemperatureC,
    fanPercentNow = snapshot?.fanPercentNow,
    operatingState = snapshot?.operatingState,
    policy = null
)

private fun DeviceCoolingAutomaticFailure.toAutomaticTerminalDataState(): CoolingDataState<
    DeviceCoolingAutomaticSettingsSnapshot,
    DeviceCoolingAutomaticFailure
    > = when (this) {
    DeviceCoolingAutomaticFailure.Unsupported -> CoolingDataState.Unsupported
    DeviceCoolingAutomaticFailure.Unavailable,
    DeviceCoolingAutomaticFailure.NotConnected,
    DeviceCoolingAutomaticFailure.TemporaryFailure -> CoolingDataState.Unavailable
    DeviceCoolingAutomaticFailure.ReadOnly,
    DeviceCoolingAutomaticFailure.InvalidConfiguration,
    is DeviceCoolingAutomaticFailure.Rejected -> CoolingDataState.OperationError(this)
}

private val DeviceCoolingAutomaticSettingsSnapshot.completeConfiguration:
    AutomaticSnapshotConfiguration?
    get() = startTemperatureC?.let { start ->
        maximumSpeedTemperatureC?.let { maximum ->
            policy?.let { temperaturePolicy ->
                AutomaticSnapshotConfiguration(
                    startTemperatureC = start,
                    maximumSpeedTemperatureC = maximum,
                    policy = temperaturePolicy
                )
            }
        }
    }

private data class AutomaticSnapshotConfiguration(
    val startTemperatureC: Double,
    val maximumSpeedTemperatureC: Double,
    val policy: DeviceCoolingAutomaticTemperaturePolicy
)

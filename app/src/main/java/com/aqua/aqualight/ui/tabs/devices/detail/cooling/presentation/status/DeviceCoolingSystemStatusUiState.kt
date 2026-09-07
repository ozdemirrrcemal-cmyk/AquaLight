package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.authoritativeValueOrNull

data class DeviceCoolingSystemStatusUiState(
    val deviceUid: String = "",
    val online: Boolean = false,
    val dataState: CoolingDataState<DeviceCoolingControlSnapshot, DeviceCoolingControlFailure> =
        CoolingDataState.Initial
) {
    val snapshot: DeviceCoolingControlSnapshot?
        get() = dataState.authoritativeValueOrNull

    val telemetryAvailable: Boolean
        get() = snapshot?.telemetry != null

    val stale: Boolean
        get() = when (val state = dataState) {
            is CoolingDataState.Content -> state.freshness == CoolingDataFreshness.STALE
            is CoolingDataState.Empty -> state.freshness == CoolingDataFreshness.STALE
            CoolingDataState.Initial,
            CoolingDataState.Loading,
            CoolingDataState.Unavailable,
            CoolingDataState.Unsupported,
            is CoolingDataState.OperationError -> false
        }
}

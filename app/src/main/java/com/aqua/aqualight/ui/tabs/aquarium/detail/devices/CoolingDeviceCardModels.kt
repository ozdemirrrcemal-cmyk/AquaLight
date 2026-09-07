package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import androidx.annotation.DrawableRes
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardState
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardSummary
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

data class CoolingDeviceSpotlightHeaderUi(
    val displayName: String,
    @DrawableRes val iconRes: Int,
    val statusStyle: DeviceConnectionVisualState,
    val isBusy: Boolean
)

enum class CoolingDeviceSpotlightContentState {
    PREPARING,
    READY,
    UNAVAILABLE
}

data class CoolingDeviceSpotlightCardUi(
    val header: CoolingDeviceSpotlightHeaderUi,
    val contentState: CoolingDeviceSpotlightContentState,
    val summary: DeviceCoolingCardSummary?
)

internal fun DeviceCompactCardUi.toCoolingSpotlightCardUi(
    state: DeviceCoolingCardState?
): CoolingDeviceSpotlightCardUi = CoolingDeviceSpotlightCardUi(
    header = CoolingDeviceSpotlightHeaderUi(
        displayName = displayName,
        iconRes = iconRes,
        statusStyle = statusStyle,
        isBusy = isBusy
    ),
    contentState = when (state) {
        is DeviceCoolingCardState.Ready -> CoolingDeviceSpotlightContentState.READY
        is DeviceCoolingCardState.Unavailable -> CoolingDeviceSpotlightContentState.UNAVAILABLE
        DeviceCoolingCardState.Preparing,
        null -> CoolingDeviceSpotlightContentState.PREPARING
    },
    summary = (state as? DeviceCoolingCardState.Ready)?.summary
)

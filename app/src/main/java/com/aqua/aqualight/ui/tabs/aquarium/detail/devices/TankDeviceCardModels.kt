package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import androidx.annotation.DrawableRes
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardChannelSummary
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardSummary
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

data class DosingDeviceSpotlightHeaderUi(
    val displayName: String,
    @DrawableRes val iconRes: Int,
    val statusStyle: DeviceConnectionVisualState,
    val isBusy: Boolean
)

enum class DosingDeviceSpotlightContentState {
    PREPARING,
    READY,
    UNAVAILABLE
}

data class DosingDeviceSpotlightCardUi(
    val header: DosingDeviceSpotlightHeaderUi,
    val contentState: DosingDeviceSpotlightContentState,
    val summary: DeviceDosingCardSummary?,
    val selectedChannel: DeviceDosingCardChannelSummary?,
    val selectedIndex: Int,
    val pageCount: Int
)

internal fun DeviceCompactCardUi.toDosingSpotlightCardUi(
    state: DeviceDosingCardState?,
    selectedIndex: Int
): DosingDeviceSpotlightCardUi {
    val summary = (state as? DeviceDosingCardState.Ready)?.summary
    val channels = summary?.channels.orEmpty()
    val safeIndex = if (channels.isEmpty()) {
        0
    } else {
        selectedIndex.coerceIn(0, channels.lastIndex)
    }
    val contentState = when (state) {
        is DeviceDosingCardState.Ready -> DosingDeviceSpotlightContentState.READY
        is DeviceDosingCardState.Unavailable -> DosingDeviceSpotlightContentState.UNAVAILABLE
        DeviceDosingCardState.Preparing,
        null -> DosingDeviceSpotlightContentState.PREPARING
    }
    return DosingDeviceSpotlightCardUi(
        header = DosingDeviceSpotlightHeaderUi(
            displayName = displayName,
            iconRes = iconRes,
            statusStyle = statusStyle,
            isBusy = isBusy
        ),
        contentState = contentState,
        summary = summary,
        selectedChannel = channels.getOrNull(safeIndex),
        selectedIndex = safeIndex,
        pageCount = channels.size
    )
}

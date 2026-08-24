package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import androidx.annotation.DrawableRes
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardChannelSummary
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardSummary
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactStatusStyle
import com.aqua.aqualight.ui.common.dosing.pump.isSupportedDosingPumpCount

data class DosingDeviceSpotlightHeaderUi(
    val displayName: String,
    @DrawableRes val iconRes: Int,
    val dosingChannelCount: Int?,
    val statusStyle: DeviceCompactStatusStyle,
    val isBusy: Boolean
)

data class DosingDeviceSpotlightCardUi(
    val header: DosingDeviceSpotlightHeaderUi,
    val summary: DeviceDosingCardSummary?,
    val selectedChannel: DeviceDosingCardChannelSummary?,
    val selectedIndex: Int,
    val pageCount: Int
)

internal fun DeviceCompactCardUi.toDosingSpotlightCardUi(
    summary: DeviceDosingCardSummary?,
    selectedIndex: Int
): DosingDeviceSpotlightCardUi {
    val expectedChannelCount = dosingChannelCount?.takeIf(::isSupportedDosingPumpCount)
    val trustedSummary = summary?.takeIf { value ->
        expectedChannelCount != null && value.channelCount == expectedChannelCount
    }
    val channels = trustedSummary?.channels.orEmpty()
    val safeIndex = if (channels.isEmpty()) {
        0
    } else {
        selectedIndex.coerceIn(0, channels.lastIndex)
    }
    return DosingDeviceSpotlightCardUi(
        header = DosingDeviceSpotlightHeaderUi(
            displayName = displayName,
            iconRes = iconRes,
            dosingChannelCount = dosingChannelCount,
            statusStyle = statusStyle,
            isBusy = isBusy
        ),
        summary = trustedSummary,
        selectedChannel = channels.getOrNull(safeIndex),
        selectedIndex = safeIndex,
        pageCount = channels.size
    )
}

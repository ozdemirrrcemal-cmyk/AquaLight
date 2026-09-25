package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.preset

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualColors

internal data class DeviceLightAutomaticPresetVisuals(
    val colors: AquaLightManualColors,
    val typography: AquaDeviceCardTypography
)

internal object DeviceLightAutomaticPresetGeometry {
    val screenHorizontalPadding = 10.dp
    val screenTopPadding = 2.dp
    val screenBottomPadding = 8.dp
    val sectionGap = 8.dp
    val gridGap = 7.dp

    val introductionPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    val introductionIconSize = 24.dp
    val introductionIconGap = 10.dp
    val introductionTextGap = 2.dp

    val cardHeight = 142.dp
    val cardPadding = PaddingValues(10.dp)
    val cardShape = RoundedCornerShape(AquaDeviceCardGeometry.cornerRadius)
    val cardIconSize = PRESET_CARD_ICON_SIZE_DP.dp
    val cardIconGap = 8.dp
    val cardHeadingHeight = 46.dp
    val cardTextGap = 2.dp
    val selectedIndicatorSize = 22.dp
    val selectedIndicatorIconSize = 15.dp
    val selectedIndicatorReserve = 24.dp
    val dividerHeight = 1.dp
    val dividerVerticalGap = 7.dp
    val scheduleIconSize = 16.dp
    val scheduleIconStrokeWidth = 1.4.dp
    val scheduleGap = 7.dp
    val channelsTopGap = 8.dp
    val channelDotSize = 8.dp
    val channelDotGap = 4.dp

    val actionHeight = 50.dp
    val actionGap = 8.dp
    val actionShape = RoundedCornerShape(13.dp)
    val actionBorderWidth = 1.dp
    val actionTopPadding = 6.dp
}

internal object DeviceLightAutomaticPresetAlpha {
    const val selectedSurface = 0.10f
    const val divider = 0.72f
}

private const val PRESET_CARD_ICON_SIZE_DP = 34

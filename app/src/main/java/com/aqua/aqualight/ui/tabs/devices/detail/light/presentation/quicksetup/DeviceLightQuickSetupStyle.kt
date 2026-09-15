package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightManualColors

@Immutable
internal data class DeviceLightQuickSetupVisuals(
    val colors: AquaLightManualColors,
    val typography: AquaDeviceCardTypography
)

internal object DeviceLightQuickSetupGeometry {
    val horizontalPadding = 14.dp
    val topPadding = 8.dp
    val bottomPadding = 10.dp
    val sectionGap = 12.dp
    val cardPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp)
    val compactCardPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
    val contentGap = 12.dp
    val smallGap = 7.dp
    val microGap = 4.dp
    val iconSize = 34.dp
    val smallIconSize = 22.dp
    val iconGap = 12.dp
    val chipHeight = 48.dp
    val chipShape = RoundedCornerShape(13.dp)
    val chipPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    val statusShape = RoundedCornerShape(12.dp)
    val statusPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp)
    val dividerHeight = 1.dp
    val stepButtonSize = 48.dp
    val actionHeight = 56.dp
    val actionShape = RoundedCornerShape(16.dp)
    val actionBorderWidth = 1.dp
    val actionTopPadding = 10.dp
    val phaseMarkerSize = 30.dp
    val channelDotSize = 9.dp
    val largeValueSize = 25.sp
    val largeValueLineHeight = 31.sp
}

internal object DeviceLightQuickSetupAlpha {
    const val softSurface = 0.12f
    const val selectedSurface = 0.18f
    const val disabled = 0.38f
    const val divider = 0.66f
    const val secondarySurface = 0.55f
}

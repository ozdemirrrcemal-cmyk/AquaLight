package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightManualColors

@Immutable
internal data class DeviceLightSystemVisuals(
    val colors: AquaLightManualColors,
    val typography: AquaDeviceCardTypography
)

internal object DeviceLightSystemGeometry {
    val screenHorizontalPadding = 12.dp
    val screenTopPadding = 4.dp
    val screenBottomPadding = 12.dp
    val sectionGap = 8.dp
    val cardPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    val statusCardHeight = 200.dp
    val modeCardHeight = 116.dp
    val automaticCardHeight = 206.dp
    val protectionCardHeight = 154.dp
    val cardTitleBottomGap = 6.dp
    val gaugeSize = 112.dp
    val gaugeTrackWidth = 11.dp
    const val gaugeSweep = 280f
    const val gaugeStartAngle = -85f
    const val gaugeMinimumTemperature = 0f
    const val gaugeMaximumTemperature = 70f
    val gaugeValueSize = 22.sp
    val gaugeStatusGap = 4.dp
    val statusDotSize = 7.dp
    val fanCardHeight = 44.dp
    val fanCardGap = 7.dp
    val fanCardShape = RoundedCornerShape(7.dp)
    val fanCardPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
    val fanIconSize = 24.dp
    val fanTrackHeight = 7.dp
    val fanTrackShape = RoundedCornerShape(percent = 50)
    val fanValueWidth = 34.dp
    val dividerHeight = 1.dp
    val sensorRowHeight = 24.dp
    val infoIconSize = 16.dp
    val infoTextGap = 8.dp
    val modeLabelGap = 2.dp
    val segmentHeight = 42.dp
    val segmentShape = RoundedCornerShape(8.dp)
    val segmentInnerShape = RoundedCornerShape(7.dp)
    val segmentOutlineWidth = 1.dp
    val modeHelperGap = 6.dp
    val chartHeight = 65.dp
    val chartYAxisWidth = 28.dp
    val chartXAxisHeight = 14.dp
    val chartGridWidth = 1.dp
    val chartLineWidth = 2.dp
    val chartPointRadius = 3.dp
    val controlRowHeight = 42.dp
    val controlLabelWidth = 66.dp
    val controlValueWidth = 49.dp
    val controlRowGap = 0.dp
    val controlSliderHeight = 36.dp
    val controlSliderTrackHeight = 6.dp
    val controlSliderThumbRadius = 8.dp
    val controlSliderInset = 8.dp
    val controlStepTouchSize = 36.dp
    val controlStepVisualSize = 28.dp
    val controlStepBorderWidth = 1.dp
    val helperTopGap = 1.dp
    val protectionIntroHeight = 49.dp
    val protectionIconSize = 42.dp
    val protectionTextGap = 10.dp
    val protectionBadgeShape = RoundedCornerShape(9.dp)
    val protectionBadgePadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    val protectionDividerGap = 5.dp
    val thresholdLimitIndent = 101.dp
    val thresholdLimitTopOffset = (-5).dp
    val actionHeight = 54.dp
    val actionShape = RoundedCornerShape(14.dp)
    val actionTopPadding = 4.dp
}

internal object DeviceLightSystemAlpha {
    const val track = 0.28f
    const val divider = 0.70f
    const val disabled = 0.38f
    const val badgeSurface = 0.12f
    const val grid = 0.55f
}

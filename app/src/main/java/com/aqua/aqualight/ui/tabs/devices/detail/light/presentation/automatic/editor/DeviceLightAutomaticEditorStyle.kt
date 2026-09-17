package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

internal object DeviceLightAutomaticEditorGeometry {
    val screenPadding = 10.dp
    val screenTopPadding = 2.dp
    val screenBottomPadding = 8.dp
    val sectionGap = 6.dp
    val cardPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)
    val cardContentGap = 7.dp

    val cycleCardHeight = 314.dp
    val cycleDialSize = 210.dp
    val cycleTimeFieldHeight = 48.dp
    val cycleTimeFieldGap = 8.dp
    val cycleTimeFieldShape = RoundedCornerShape(12.dp)
    val cycleTimeChevronSize = 12.dp
    val dialStrokeWidth = 10.dp
    val dialGuideStrokeWidth = 1.dp
    val dialMarkerRadius = 8.dp
    val dialMarkerOutlineWidth = 2.dp
    val dialMarkerHaloRadius = 15.dp
    val dialCenterWidth = 104.dp
    val dialEventIconSize = 22.dp
    val dialEventIconGap = 8.dp
    val dialEventStrokeWidth = 1.6.dp
    val dialMoonSize = 19.dp

    val sectionIconSize = 21.dp
    val sectionIconGap = 7.dp
    val headingTextGap = 1.dp
    val dayButtonHeight = 36.dp
    val dayButtonGap = 5.dp
    val dayButtonShape = RoundedCornerShape(9.dp)
    val quickDayButtonHeight = 36.dp
    val quickDayButtonGap = 6.dp

    val rampControlHeight = 60.dp
    val rampSliderTouchHeight = 34.dp
    val rampSliderTrackWidth = 2.dp
    val rampSliderThumbRadius = 7.dp
    val rampSliderStepRadius = 2.5.dp
    val rampValueWidth = 58.dp

    val presetHeight = 44.dp
    val presetChevronSize = 16.dp

    val channelRowHeight = 42.dp
    val channelLabelWidth = 54.dp
    val channelValueWidth = 42.dp

    val actionHeight = 50.dp
    val actionTopPadding = 6.dp
    val actionGap = 8.dp
    val actionShape = RoundedCornerShape(13.dp)
    val actionBorderWidth = 1.dp
}

internal object DeviceLightAutomaticEditorAlpha {
    const val centerScrim = 0.72f
    const val neutralDial = 0.28f
    const val dialGuide = 0.62f
    const val dialMarkerHalo = 0.28f
    const val disabled = 0.38f
    const val selectedSurface = 0.20f
    const val unselectedSurface = 0.06f
}

internal object DeviceLightAutomaticDialSpec {
    const val fullCircleDegrees = 360f
    const val topOriginDegrees = -90f
    const val halfTurnDegrees = 180f
    const val quarterDay = 0.25f
    const val halfDay = 0.50f
    const val threeQuarterDay = 0.75f
    const val markerAngleOffsetDegrees = 90.0
    const val radiusInsetFraction = 0.09f
    const val moonOrbitFraction = 0.67f
    const val centerScrimRadiusFraction = 0.35f
}

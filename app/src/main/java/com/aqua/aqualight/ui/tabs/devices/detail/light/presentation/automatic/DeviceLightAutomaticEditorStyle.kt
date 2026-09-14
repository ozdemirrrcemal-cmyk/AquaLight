package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

internal object DeviceLightAutomaticEditorGeometry {
    val screenPadding = 9.dp
    val screenTopPadding = 3.dp
    val screenBottomPadding = 18.dp
    val sectionGap = 7.dp
    val cardPadding = PaddingValues(horizontal = 11.dp, vertical = 9.dp)
    val cardContentGap = 8.dp

    val simulationHeight = 174.dp
    val simulationCornerRadius = 16.dp
    val simulationContentPadding = 12.dp
    val simulationTextWidthFraction = 0.42f
    val simulationTitleTopGap = 4.dp
    val simulationCopyGap = 5.dp
    val simulationStatusHeight = 27.dp
    val simulationStatusHorizontalPadding = 10.dp
    val simulationStatusDotSize = 7.dp
    val simulationStatusGap = 6.dp
    val dialSize = 142.dp
    val dialStrokeWidth = 9.dp
    val dialGuideStrokeWidth = 1.dp
    val dialMarkerRadius = 5.dp
    val dialMarkerOutlineWidth = 2.dp
    val dialCenterWidth = 78.dp

    val sectionIconSize = 23.dp
    val sectionIconGap = 9.dp
    val headingTextGap = 1.dp
    val dayButtonHeight = 38.dp
    val dayButtonGap = 5.dp
    val dayButtonShape = RoundedCornerShape(10.dp)
    val quickDayButtonHeight = 36.dp
    val quickDayButtonGap = 6.dp

    val timeCardGap = 7.dp
    val timeCardHeight = 66.dp
    val timeValueMinWidth = 82.dp
    val timeValueHeight = 40.dp
    val timeValueShape = RoundedCornerShape(10.dp)
    val timeChevronSize = 12.dp

    val rampButtonHeight = 38.dp
    val rampButtonGap = 6.dp
    val rampButtonShape = RoundedCornerShape(10.dp)
    val informationIconSize = 17.dp
    val informationGap = 7.dp

    val presetHeight = 64.dp
    val presetChevronSize = 16.dp

    val channelRowHeight = 40.dp
    val channelRowGap = 3.dp
    val channelLabelWidth = 55.dp
    val channelValueWidth = 42.dp
    val channelSliderGap = 7.dp

    val chartHeight = 88.dp
    val chartTopGap = 7.dp

    val actionHeight = 50.dp
    val actionGap = 8.dp
    val actionShape = RoundedCornerShape(13.dp)
    val actionBorderWidth = 1.dp
}

internal object DeviceLightAutomaticEditorAlpha {
    const val imageScrim = 0.34f
    const val imageColorOverlay = 0.24f
    const val neutralDial = 0.32f
    const val dialGuide = 0.50f
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
    const val radiusInsetFraction = 0.12f
    const val centerTextStartFraction = 0.31f
    const val centerTextEndFraction = 0.69f
    const val centerDividerFirstFraction = 0.43f
    const val centerDividerSecondFraction = 0.61f
    const val minimumOverlayChannel = 0f
    const val maximumOverlayChannel = 1f
    const val whiteMixWeight = 0.55f
    const val colorMixWeight = 0.45f
}

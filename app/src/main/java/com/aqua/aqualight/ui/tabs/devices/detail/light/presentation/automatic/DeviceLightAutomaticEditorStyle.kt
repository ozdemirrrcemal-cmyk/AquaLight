package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

internal object DeviceLightAutomaticEditorGeometry {
    val screenPadding = 10.dp
    val screenTopPadding = 2.dp
    val screenBottomPadding = 18.dp
    val sectionGap = 6.dp
    val cardPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
    val cardContentGap = 6.dp

    const val simulationAspectRatio = 2.44f
    val simulationContentPadding = 11.dp
    const val simulationImageWidthFraction = 0.58f
    const val simulationImageHorizontalBias = 0.30f
    const val simulationTextWidthFraction = 0.43f
    val simulationTitleTopGap = 3.dp
    val simulationCopyGap = 4.dp
    val simulationStatusHeight = 23.dp
    val simulationStatusHorizontalPadding = 8.dp
    val simulationStatusDotSize = 6.dp
    val simulationStatusGap = 5.dp
    val dialSize = 128.dp
    val dialStrokeWidth = 8.dp
    val dialGuideStrokeWidth = 1.dp
    val dialMarkerRadius = 4.dp
    val dialMarkerOutlineWidth = 1.5.dp
    val dialCenterWidth = 70.dp

    val sectionIconSize = 21.dp
    val sectionIconGap = 7.dp
    val headingTextGap = 1.dp
    val dayCardHeight = 69.dp
    val dayButtonHeight = 30.dp
    val dayButtonGap = 3.dp
    val dayButtonShape = RoundedCornerShape(9.dp)
    val quickDayButtonHeight = 34.dp
    val quickDayButtonGap = 3.dp
    val dayGroupGap = 8.dp
    val dayGroupDividerWidth = 1.dp

    val timeCardGap = 6.dp
    val timeCardHeight = 51.dp
    val timeValueMinWidth = 64.dp
    val timeValueHeight = 36.dp
    val timeExpandedContentMinWidth = 190.dp
    val timeValueShape = RoundedCornerShape(10.dp)
    val timeChevronSize = 12.dp

    val rampButtonHeight = 34.dp
    val rampButtonGap = 5.dp
    val rampButtonShape = RoundedCornerShape(10.dp)
    val informationIconSize = 17.dp
    val informationGap = 7.dp

    val presetHeight = 44.dp
    val presetChevronSize = 16.dp

    val channelRowHeight = 36.dp
    val channelRowGap = 2.dp
    val channelLabelWidth = 52.dp
    val channelValueWidth = 39.dp
    val channelSliderGap = 5.dp

    val chartHeight = 88.dp
    val chartTopGap = 7.dp

    val actionHeight = 50.dp
    val actionGap = 8.dp
    val actionShape = RoundedCornerShape(13.dp)
    val actionBorderWidth = 1.dp
}

internal object DeviceLightAutomaticEditorAlpha {
    const val imageActive = 0.86f
    const val imageInactive = 0.36f
    const val imageColorOverlay = 0.13f
    const val heroLeftScrim = 0.90f
    const val heroCenterScrim = 0.12f
    const val heroDialScrim = 0.82f
    const val heroEdgeScrim = 0.94f
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

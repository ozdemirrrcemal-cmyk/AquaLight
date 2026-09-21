package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowGeometry

internal object DeviceLightQuickSetupGeometry {
    val screenHorizontalPadding = AquaGuidedFlowGeometry.screenHorizontalPadding
    val screenBottomPadding = AquaGuidedFlowGeometry.screenBottomPadding
    val screenTopPadding = 8.dp
    val sectionGap = 12.dp
    val compactGap = AquaGuidedFlowGeometry.compactGap
    val progressHeight = 4.dp
    val progressRadius = RoundedCornerShape(percent = 50)
    val footerGap = 8.dp
    val inputHeight = 56.dp
    val inputHorizontalPadding = 16.dp
    val measurementIllustrationHeight = 190.dp
    val timeMetricHeight = 92.dp
    val switchWidth = 50.dp
    val switchHeight = 28.dp
    val switchThumbSize = 22.dp
    val switchInset = 3.dp
    val switchShape = RoundedCornerShape(percent = 50)
    val switchThumbTravel = switchWidth - switchThumbSize - switchInset - switchInset
    val timelineDotSize = 10.dp
    val channelTrackHeight = 8.dp
    val chartHeight = 164.dp
    val chartGridStroke = 1.dp
    val chartLineStroke = 2.dp
    val spinnerSize = 88.dp
    val spinnerStroke = 8.dp
    val actionBottomPadding = 12.dp
    val errorPadding = 12.dp

    const val profileValueWeight = 1.15f
    val illustrationPadding = 18.dp
    val illustrationOutlineStroke = 2.dp
    val illustrationBorderInset = 2.dp
    val illustrationDoubleBorderInset = 4.dp
    val illustrationArrowHalfWidth = 8.dp
    val fixtureArrowHalfWidth = 7.dp
    val fixtureStrokeWidth = 8.dp
    val fixtureArrowGap = 6.dp

    const val waterTankLeftFraction = 0.18f
    const val waterTankRightFraction = 0.72f
    const val waterTankTopFraction = 0.12f
    const val waterTankBottomFraction = 0.88f
    const val waterSurfaceFraction = 0.30f
    const val waterFillAlpha = 0.34f
    const val substrateTopFraction = 0.78f
    const val substrateFillAlpha = 0.32f
    const val waterArrowXFraction = 0.84f

    const val fixtureTankLeftFraction = 0.13f
    const val fixtureTankRightFraction = 0.87f
    const val fixtureWaterTopFraction = 0.58f
    const val fixtureTankBottomFraction = 0.90f
    const val fixtureWaterAlpha = 0.30f
    const val fixtureYFraction = 0.22f
    const val fixtureStartFraction = 0.30f
    const val fixtureEndFraction = 0.70f
    const val fixtureArrowXFraction = 0.78f

    const val transitionPermillePerPercent = 10f
    const val chartGridLineCount = 5
    const val chartGridIntervalCount = 4f
    const val maturePhotoperiodHours = 8f
    const val percentageScale = 100f
    const val timeValueWeight = 1.3f
    const val footerPrimaryWeightWithBack = 1.6f
}

internal object DeviceLightQuickSetupAlpha {
    const val progressTrack = 0.22f
    const val selectedSurface = 0.12f
    const val infoSurface = 0.10f
    const val dangerSurface = 0.10f
    const val grid = 0.34f
    const val disabled = 0.42f
}

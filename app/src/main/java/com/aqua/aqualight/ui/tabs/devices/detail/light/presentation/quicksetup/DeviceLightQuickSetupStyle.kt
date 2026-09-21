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
    val timelineDotSize = 10.dp
    val channelTrackHeight = 8.dp
    val chartHeight = 164.dp
    val chartGridStroke = 1.dp
    val chartLineStroke = 2.dp
    val spinnerSize = 88.dp
    val spinnerStroke = 8.dp
    val actionBottomPadding = 12.dp
    val errorPadding = 12.dp
}

internal object DeviceLightQuickSetupAlpha {
    const val progressTrack = 0.22f
    const val selectedSurface = 0.12f
    const val infoSurface = 0.10f
    const val dangerSurface = 0.10f
    const val grid = 0.34f
    const val disabled = 0.42f
}

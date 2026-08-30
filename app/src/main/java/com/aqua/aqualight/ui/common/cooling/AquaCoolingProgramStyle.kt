package com.aqua.aqualight.ui.common.cooling

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Central visual contract for the Cooling multi-period program editor. */
object AquaCoolingProgramGeometry {
    val screenHorizontalPadding = AquaCoolingDashboardGeometry.screenHorizontalPadding
    val screenTopPadding = 10.dp
    val screenBottomPadding = 28.dp
    val sectionGap = 10.dp

    val activeCardMinimumHeight = 76.dp
    val activeRowGap = 5.dp
    val activeDotSize = 7.dp

    val timelineCardMinimumHeight = 132.dp
    val timelineHeight = 54.dp
    val timelineTrackHeight = 8.dp
    val timelineMarkerRadius = 3.dp
    val timelineNowStrokeWidth = 2.dp
    val timelineAxisGap = 7.dp
    val timelineLegendGap = 8.dp

    val slotCardMinimumHeight = 124.dp
    val slotGap = 8.dp
    val slotHeaderGap = 5.dp
    val slotMetricGap = 5.dp
    val slotChevronWidth = 18.dp
    val slotChevronHeight = 18.dp

    val addActionHeight = 44.dp
    val addActionShape = RoundedCornerShape(14.dp)
    val addActionHorizontalPadding = 14.dp

    val editorCardMinimumHeight = 274.dp
    val editorRowShape = RoundedCornerShape(13.dp)
    val editorRowHorizontalPadding = 10.dp
    val editorRowVerticalPadding = 10.dp
    val editorRowGap = 7.dp
    val editorChevronWidth = 18.dp
    val editorChevronHeight = 18.dp

    val saveActionHeight = 48.dp
    val saveActionShape = RoundedCornerShape(14.dp)
    val saveActionHorizontalPadding = 16.dp
}

object AquaCoolingProgramAlpha {
    const val activeDot = 0.96f
    const val timelineTrack = 0.22f
    const val timelineQuiet = 0.50f
    const val timelineIntensive = 1f
    const val timelineNight = 0.68f
    const val timelineCustom = 0.82f
    const val timelineNow = 0.92f
    const val slotSelectedOutline = 0.84f
    const val slotSelectedBackground = 0.07f
    const val editorRowBackground = 0.58f
    const val editorRowOutline = 0.78f
    const val addBackground = 0.07f
    const val addOutline = 0.52f
    const val saveEnabled = 0.92f
    const val saveDisabled = 0.30f
}

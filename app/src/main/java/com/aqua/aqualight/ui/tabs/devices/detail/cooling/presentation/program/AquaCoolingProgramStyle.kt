package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry

/** Central visual contract for the Cooling multi-period program editor. */
object AquaCoolingProgramGeometry {
    const val timelineCenterFraction = 0.50f
    const val timelineNowExtentMultiplier = 1.35f
    const val expandedChevronRotationDegrees = 90f

    val screenHorizontalPadding = AquaCoolingDashboardGeometry.screenHorizontalPadding
    val screenTopPadding = 10.dp
    val screenBottomPadding = 28.dp
    val sectionGap = 10.dp

    val activeCardMinimumHeight = 62.dp
    val activeRowGap = 4.dp
    val activeDotSize = 7.dp

    val timelineCardMinimumHeight = 124.dp
    val timelineHeight = 48.dp
    val timelineTrackHeight = 8.dp
    val timelineMarkerRadius = 3.dp
    val timelineNowStrokeWidth = 2.dp
    val timelineAxisGap = 6.dp

    val slotHeaderShape = RoundedCornerShape(10.dp)
    val slotHeaderVerticalPadding = 2.dp
    val slotHeaderGap = 7.dp
    val slotMetricGap = 6.dp
    val slotChevronWidth = 18.dp
    val slotChevronHeight = 18.dp
    val selectedSlotOutlineWidth = 1.dp
    val chevronStrokeWidth = 1.6.dp

    val inlineActionShape = RoundedCornerShape(10.dp)
    val inlineActionHorizontalPadding = 8.dp
    val inlineActionVerticalPadding = 4.dp

    val expandedSectionTopGap = 5.dp
    val expandedDividerHeight = 1.dp
    val editorRowShape = RoundedCornerShape(12.dp)
    val editorRowHorizontalPadding = 10.dp
    val editorRowVerticalPadding = 9.dp
    val editorRowGap = 7.dp

    val fanLimitSliderHeight = 36.dp
    val fanLimitTrackHeight = 6.dp
    val fanLimitThumbRadius = 8.dp
    val fanLimitThumbOutlineWidth = 2.dp
}

object AquaCoolingProgramAlpha {
    const val inlineActionEnabled = 1f
    const val activeDot = 0.96f
    const val timelineTrack = 0.22f
    const val timelinePeriod = 0.88f
    const val timelineNow = 0.92f
    const val slotSelectedOutline = 0.84f
    const val editorRowBackground = 0.58f
    const val editorRowOutline = 0.78f
    const val expandedDivider = 0.46f
    const val inlineActionDisabled = 0.40f
    const val fanLimitInactiveTrack = 0.30f
}

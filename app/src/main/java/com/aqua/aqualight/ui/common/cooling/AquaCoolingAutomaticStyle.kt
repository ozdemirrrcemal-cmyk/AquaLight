package com.aqua.aqualight.ui.common.cooling

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Central geometry/alpha contract for the Cooling automatic temperature editor. */
object AquaCoolingAutomaticGeometry {
    val screenHorizontalPadding = AquaCoolingDashboardGeometry.screenHorizontalPadding
    val screenTopPadding = 10.dp
    val screenBottomPadding = 28.dp
    val sectionGap = 10.dp

    val liveCardMinimumHeight = 92.dp
    val liveMetricGap = 8.dp
    val liveMetricVerticalPadding = 4.dp

    val rangeCardMinimumHeight = 238.dp
    val editorRowShape = RoundedCornerShape(13.dp)
    val editorRowHorizontalPadding = 10.dp
    val editorRowVerticalPadding = 10.dp
    val editorRowGap = 7.dp
    val editorChevronWidth = 18.dp
    val editorChevronHeight = 18.dp

    val rangeVisualHeight = 58.dp
    val rangeTrackHeight = 5.dp
    val rangeMarkerRadius = 6.dp
    val rangeMarkerOutlineWidth = 2.dp
    val rangeTrackHorizontalPadding = 8.dp
    val rangeLegendGap = 5.dp

    val silentModeCardMinimumHeight = 104.dp
    val silentModeContentGap = 8.dp

    val behaviorCardMinimumHeight = 126.dp
    val behaviorRowVerticalPadding = 7.dp
    val behaviorDividerHeight = 1.dp

    val actionHeight = 48.dp
    val actionShape = RoundedCornerShape(14.dp)
    val actionHorizontalPadding = 16.dp

    // Connection/read state is a compact banner; it must never replace the editor layout.
    val messageCardMinimumHeight = 92.dp
    val messageGap = 7.dp
    val retryShape = RoundedCornerShape(18.dp)
    val retryHorizontalPadding = 14.dp
    val retryVerticalPadding = 7.dp
}

object AquaCoolingAutomaticAlpha {
    const val rowBackground = 0.58f
    const val rowOutline = 0.78f
    const val rangeInactiveTrack = 0.20f
    const val rangeActiveTrack = 1f
    const val rangeMarkerFill = 1f
    const val divider = 0.50f
    const val saveDisabled = 0.30f
    const val saveEnabled = 0.92f
    const val statusDot = 0.96f
    const val retryBackground = 0.10f
}

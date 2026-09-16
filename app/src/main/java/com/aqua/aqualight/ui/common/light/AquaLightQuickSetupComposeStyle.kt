@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.common.light

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Central geometry and state opacity for the managed-light guided flow. */
object AquaLightQuickSetupGeometry {
    val screenTopPadding = 12.dp
    val screenHorizontalPadding = 20.dp
    val screenBottomPadding = 28.dp
    val progressHeight = 4.dp
    val progressGap = 6.dp
    val titleTopPadding = 18.dp
    val titleBottomPadding = 18.dp
    val sectionGap = 14.dp
    val compactGap = 8.dp
    val tinyGap = 4.dp
    val cardPadding = 16.dp
    val rowPadding = 12.dp
    val chipHorizontalPadding = 11.dp
    val chipVerticalPadding = 7.dp
    val chipGap = 7.dp
    val chipShape = RoundedCornerShape(12.dp)
    val segmentedShape = RoundedCornerShape(14.dp)
    val segmentedPadding = 4.dp
    val segmentedItemPadding = 11.dp
    val controlHeight = 48.dp
    val controlButtonSize = 44.dp
    val switchWidth = 48.dp
    val switchHeight = 28.dp
    val switchThumb = 22.dp
    val switchInset = 3.dp
    val metricCardMinHeight = 78.dp
    val graphHeight = 130.dp
    val graphLineWidth = 2.dp
    val graphGuideLineWidth = 1.dp
    val phaseBarHeight = 8.dp
    val actionTopPadding = 12.dp
    val badgeSize = 28.dp
    val dividerHeight = 1.dp
    val automationHeroIconSize = 46.dp
    val automationHeroIconRadius = 14.dp
    val automationHeroGlyphSize = 24.dp
    val profileIconSize = 22.dp
    val profileRowVerticalPadding = 10.dp
    val chartPadding = 12.dp
    val programChartHeight = 142.dp
    val chartGridWidth = 1.dp
    val chartLineWidth = 2.dp
    val channelDotSize = 8.dp
    val summaryMetricPadding = 10.dp
    val channelOutputDotSize = 13.dp
    val reasonBadgeSize = 26.dp
    val adaptationIconSize = 28.dp
    val phaseLabelWidth = 96.dp
}

object AquaLightQuickSetupAlpha {
    const val SUBTLE = 0.18f
    const val OUTLINE = 0.45f
    const val DISABLED = 0.5f
    const val GRAPH_FILL = 0.22f
    const val GUIDE = 0.35f
}

@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.common.cooling

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Central Compose contract for Cooling dashboard surfaces.
 *
 * Shared device-card colors and typography remain owned by AquaDeviceCardComposeStyle. This file
 * owns only Cooling-specific geometry, motion-independent drawing values, alpha rules and type
 * scale adjustments so feature composables do not declare local visual primitives.
 */
object AquaCoolingDashboardGeometry {
    val screenHorizontalPadding = 16.dp
    val screenTopPadding = 12.dp
    val screenBottomPadding = 24.dp
    val cardGap = 10.dp
    val splitCardGap = 10.dp

    val temperatureCardMinimumHeight = 184.dp
    val temperatureChartHeight = 112.dp
    val temperatureChartCornerRadius = 14.dp
    val temperatureChartPadding = 10.dp
    val temperatureMetricWidth = 92.dp
    val temperatureMetricGap = 10.dp
    val chartGridStrokeWidth = 1.dp
    val chartLineStrokeWidth = 2.dp
    val chartPointRadius = 2.5.dp

    val compactCardMinimumHeight = 210.dp
    val statusCardMinimumHeight = 146.dp
    val gaugeSize = 102.dp
    val gaugeStrokeWidth = 9.dp
    val gaugeInnerGap = 4.dp

    val optionShape = RoundedCornerShape(12.dp)
    val optionHorizontalPadding = 10.dp
    val optionVerticalPadding = 16.dp
    val optionGap = 6.dp
    val radioSize = 18.dp
    val radioStrokeWidth = 1.5.dp
    val radioDotRadius = 4.dp

    val profileShape = RoundedCornerShape(12.dp)
    val profileMinimumHeight = 54.dp
    val profileHorizontalPadding = 6.dp
    val profileVerticalPadding = 8.dp
    val profileGap = 6.dp
    val profileGlyphSize = 18.dp

    val controlCardMinimumHeight = 128.dp
    val controlRowVerticalPadding = 6.dp
    val controlDividerHeight = 1.dp
    val editShape = RoundedCornerShape(11.dp)
    val editHorizontalPadding = 12.dp
    val editVerticalPadding = 16.dp

    val sliderTouchHeight = 48.dp
    val sliderTrackHeight = 6.dp
    val sliderThumbRadius = 9.dp
    val sliderThumbOutlineWidth = 2.dp
    val sliderLabelGap = 8.dp

    val statusDotSize = 7.dp
    val statusRowGap = 7.dp
    val statusValueGap = 6.dp
}

object AquaCoolingDashboardTypography {
    val metricValueSize = 22.sp
    val gaugeValueSize = 25.sp
    val controlValueSize = 15.sp
    val compactValueSize = 17.sp
}

object AquaCoolingInteractionStyle {
    const val enabledContentAlpha = 1f
    const val disabledContentAlpha = 0.52f
}

object AquaCoolingDashboardAlpha {
    const val selectedBackground = 0.14f
    const val selectedOutline = 0.44f
    const val chartBackground = 0.05f
    const val chartGrid = 0.16f
    const val chartLine = 0.94f
    const val trackInactive = 0.18f
    const val trackActive = 0.92f
    const val editBackground = 0.12f
    const val statusDot = 0.90f
}

object AquaCoolingGaugeSpec {
    const val startAngle = 145f
    const val sweepAngle = 250f
    const val minimumPercent = 0
    const val maximumPercent = 100
}

object AquaCoolingTemperatureChartSpec {
    const val minimumVerticalSpanC = 4f
    const val verticalPaddingC = 1f
    const val horizontalGridLineCount = 4
}

@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.common.cooling

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography

/**
 * Central visual contract for the Cooling control surface.
 *
 * The application background remains owned by the global Android theme. Cooling owns only its
 * feature-local card surfaces, blue operational accent, compact geometry and type scale here so
 * individual composables never invent visual constants.
 */
object AquaCoolingDashboardGeometry {
    val screenHorizontalPadding = 16.dp
    val screenTopPadding = 10.dp
    val screenBottomPadding = 24.dp
    val cardGap = 8.dp
    val splitCardGap = 8.dp

    val cardCornerRadius = 16.dp
    val cardHorizontalPadding = 12.dp
    val cardVerticalPadding = 10.dp

    val temperatureCardMinimumHeight = 154.dp
    val temperatureChartHeight = 96.dp
    val temperatureChartCornerRadius = 10.dp
    val temperatureChartPadding = 6.dp
    val temperatureMetricWidth = 74.dp
    val temperatureMetricGap = 8.dp
    val chartGridStrokeWidth = 1.dp
    val chartLineStrokeWidth = 2.2.dp
    val chartGlowStrokeWidth = 6.dp
    val chartPointRadius = 2.7.dp

    val compactCardMinimumHeight = 146.dp
    val statusCardMinimumHeight = 130.dp
    val gaugeSize = 84.dp
    val gaugeStrokeWidth = 8.dp
    val gaugeInnerGap = 3.dp

    val optionShape = RoundedCornerShape(20.dp)
    val optionHorizontalPadding = 9.dp
    val optionVerticalPadding = 7.dp
    val optionGap = 6.dp
    val radioSize = 16.dp
    val radioStrokeWidth = 1.5.dp
    val radioDotRadius = 4.dp

    val profileShape = RoundedCornerShape(14.dp)
    val profileMinimumHeight = 48.dp
    val profileHorizontalPadding = 5.dp
    val profileVerticalPadding = 6.dp
    val profileGap = 7.dp
    val profileGlyphSize = 17.dp

    val controlCardMinimumHeight = 104.dp
    val controlRowVerticalPadding = 3.dp
    val controlDividerHeight = 1.dp
    val editShape = RoundedCornerShape(18.dp)
    val editHorizontalPadding = 11.dp
    val editVerticalPadding = 7.dp

    val sliderTouchHeight = 40.dp
    val sliderTrackHeight = 5.dp
    val sliderThumbRadius = 8.dp
    val sliderThumbOutlineWidth = 2.dp
    val sliderLabelGap = 6.dp

    val statusDotSize = 6.dp
    val statusRowGap = 6.dp
    val statusValueGap = 5.dp
}

object AquaCoolingDashboardTypography {
    val titleSize = 13.sp
    val titleLineHeight = 16.sp
    val bodySize = 11.5.sp
    val bodyLineHeight = 14.sp
    val captionSize = 10.5.sp
    val captionLineHeight = 13.sp
    val microSize = 9.5.sp
    val microLineHeight = 12.sp
    val metricValueSize = 18.sp
    val gaugeValueSize = 21.sp
    val controlValueSize = 13.sp
    val compactValueSize = 14.sp
}

object AquaCoolingInteractionStyle {
    const val enabledContentAlpha = 1f
    const val disabledContentAlpha = 0.52f
}

object AquaCoolingDashboardAlpha {
    const val selectedBackground = 0.08f
    const val selectedOutline = 0.72f
    const val profileSelectedBackground = 0.88f
    const val chartBackground = 0.16f
    const val chartGrid = 0.17f
    const val chartLine = 1f
    const val chartGlow = 0.18f
    const val trackInactive = 0.22f
    const val trackActive = 1f
    const val editBackground = 0.10f
    const val statusDot = 0.96f
}

/** Colors sampled toward the approved compact Cooling references; global screen color is untouched. */
object AquaCoolingDashboardPalette {
    val cardSurface = Color(0xFF061727)
    val cardOutline = Color(0xFF2C3E52)
    val insetSurface = Color(0xFF081C2B)
    val insetOutline = Color(0xFF30485F)
    val primaryText = Color(0xFFE3ECFC)
    val secondaryText = Color(0xFFA6AFBD)
    val accent = Color(0xFF1474FF)
    val warning = Color(0xFFF5B942)
    val danger = Color(0xFFFF6574)
    val success = Color(0xFF68C86B)
}

@Composable
fun aquaCoolingDashboardColors(): AquaDeviceCardColors = AquaDeviceCardColors(
    surface = AquaCoolingDashboardPalette.cardSurface,
    outline = AquaCoolingDashboardPalette.cardOutline,
    mediaSurface = AquaCoolingDashboardPalette.insetSurface,
    mediaOutline = AquaCoolingDashboardPalette.insetOutline,
    primaryText = AquaCoolingDashboardPalette.primaryText,
    secondaryText = AquaCoolingDashboardPalette.secondaryText,
    accent = AquaCoolingDashboardPalette.accent,
    warning = AquaCoolingDashboardPalette.warning,
    danger = AquaCoolingDashboardPalette.danger
)

fun aquaCoolingDashboardTypography(colors: AquaDeviceCardColors): AquaDeviceCardTypography {
    val base = aquaDeviceCardTypography(colors)
    return base.copy(
        title = base.title.copy(
            fontSize = AquaCoolingDashboardTypography.titleSize,
            lineHeight = AquaCoolingDashboardTypography.titleLineHeight
        ),
        body = base.body.copy(
            fontSize = AquaCoolingDashboardTypography.bodySize,
            lineHeight = AquaCoolingDashboardTypography.bodyLineHeight
        ),
        caption = base.caption.copy(
            fontSize = AquaCoolingDashboardTypography.captionSize,
            lineHeight = AquaCoolingDashboardTypography.captionLineHeight
        ),
        micro = base.micro.copy(
            fontSize = AquaCoolingDashboardTypography.microSize,
            lineHeight = AquaCoolingDashboardTypography.microLineHeight
        )
    )
}

@Composable
fun AquaCoolingDashboardCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(AquaCoolingDashboardGeometry.cardCornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(AquaCoolingDashboardPalette.cardSurface)
            .border(
                width = 1.dp,
                color = AquaCoolingDashboardPalette.cardOutline,
                shape = shape
            )
            .padding(
                horizontal = AquaCoolingDashboardGeometry.cardHorizontalPadding,
                vertical = AquaCoolingDashboardGeometry.cardVerticalPadding
            ),
        content = content
    )
}

object AquaCoolingGaugeSpec {
    const val startAngle = 145f
    const val sweepAngle = 250f
    const val minimumPercent = 0
    const val maximumPercent = 100
}

object AquaCoolingProfileGlyphSpec {
    const val quietRadiusFraction = 0.20f
    const val balancedRadiusFraction = 0.29f
    const val performanceRadiusFraction = 0.37f
    const val boostRadiusFraction = 0.44f
    val centerDotRadius = 1.5.dp
}

object AquaCoolingTemperatureChartSpec {
    const val minimumVerticalSpanC = 4f
    const val verticalPaddingC = 1f
    const val horizontalGridLineCount = 4
}

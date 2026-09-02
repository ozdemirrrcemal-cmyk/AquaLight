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
    val screenHorizontalPadding = 12.dp
    val screenTopPadding = 8.dp
    val screenBottomPadding = 24.dp
    val cardGap = 9.dp
    val splitCardGap = 8.dp

    val cardCornerRadius = 14.dp
    val cardHorizontalPadding = 12.dp
    val cardVerticalPadding = 10.dp

    val temperatureCardMinimumHeight = 170.dp
    val temperatureChartHeight = 100.dp
    val temperatureChartPadding = 3.dp
    val temperatureYAxisWidth = 30.dp
    val temperatureYAxisGap = 5.dp
    val temperatureMetricWidth = 72.dp
    val temperatureMetricGap = 8.dp
    val temperatureMetricRowHeight = 38.dp
    val temperatureMetricIconSize = 16.dp
    val temperatureMetricIconGap = 5.dp
    val temperatureMetricDividerWidth = 1.dp
    val temperatureMetricDividerHeight = 115.dp
    val temperatureMetricRowDividerHeight = 1.dp
    val temperatureMetricRowDividerInset = 2.dp
    val chartGridStrokeWidth = 1.dp
    val chartGridDashLength = 2.dp
    val chartGridDashGap = 2.dp
    val chartLineStrokeWidth = 1.8.dp
    val chartSecondaryLineStrokeWidth = 1.5.dp
    val chartGlowStrokeWidth = 5.dp

    val compactCardMinimumHeight = 165.dp
    val statusCardMinimumHeight = 116.dp
    val telemetryContentGap = 6.dp
    val powerGlyphContainerSize = 48.dp
    val powerGlyphSize = 24.dp
    val powerContentGap = 12.dp
    val powerDividerHeight = 1.dp
    val powerValueGap = 3.dp
    val gaugeSize = 117.dp
    val gaugeStrokeWidth = 9.dp
    val gaugeInnerGap = 4.dp
    val gaugeCaptionTopPadding = 26.dp
    val gaugeLabelsBottomPadding = 3.dp

    val optionShape = RoundedCornerShape(18.dp)
    val optionHorizontalPadding = 9.dp
    val optionVerticalPadding = 5.dp
    val optionGap = 5.dp
    val radioSize = 17.dp
    val radioStrokeWidth = 1.5.dp
    val radioCheckStrokeWidth = 1.8.dp
    val modeStatusDotSize = 7.dp
    val modeStatusGap = 7.dp
    val modeStatusTopPadding = 2.dp

    val modeSettingsCardMinimumHeight = 174.dp
    val modeSettingsHeaderGap = 1.dp
    val modeSettingsContentTopPadding = 4.dp
    val modeSettingsRowHeight = 39.dp
    val modeSettingsRowIconContainerSize = 28.dp
    val modeSettingsRowIconSize = 18.dp
    val modeSettingsRowIconGap = 10.dp
    val modeSettingsRowValueGap = 1.dp
    val modeSettingsDividerHeight = 1.dp
    val modeSettingsChevronSize = 12.dp
    val modeSettingsChevronStrokeWidth = 1.8.dp
    val modeSettingsTrailingGap = 8.dp
    val activeChipShape = RoundedCornerShape(7.dp)
    val activeChipHorizontalPadding = 8.dp
    val activeChipVerticalPadding = 4.dp

    val dashboardIconStrokeWidth = 1.7.dp

    val sliderTouchHeight = 40.dp
    val sliderTrackHeight = 5.dp
    val sliderThumbRadius = 8.dp
    val sliderThumbOutlineWidth = 2.dp

    val statusDotSize = 6.dp
    val statusRowGap = 5.dp
    val statusValueGap = 5.dp

    val liveHeroHeight = 196.dp
    val liveHeroStatusPanelWidth = 104.dp
    val liveHeroStatusPanelStartPadding = 14.dp
    val liveHeroStatusPanelVerticalPadding = 9.dp
    val liveHeroStatusPanelHorizontalPadding = 9.dp
    val liveHeroStatusGap = 5.dp
    val liveHeroStatusDotSize = 7.dp
    val liveHeroDeviceTopOffset = 3.dp
    val liveHeroDeviceEndOffset = 7.dp
    val liveHeroOutlineWidth = 1.dp
    val liveHeroDeviceHighlightWidth = 1.5.dp
}

object AquaCoolingHistoryGeometry {
    val screenHorizontalPadding = AquaCoolingDashboardGeometry.screenHorizontalPadding
    val screenTopPadding = 10.dp
    val screenBottomPadding = 28.dp
    val sectionGap = 10.dp

    val rangeContainerShape = RoundedCornerShape(16.dp)
    val rangeContainerPadding = 4.dp
    val rangeSegmentShape = RoundedCornerShape(12.dp)
    val rangeSegmentVerticalPadding = 9.dp
    val rangeSegmentHorizontalPadding = 6.dp
    val rangeSegmentGap = 4.dp

    val chartCardMinimumHeight = 270.dp
    val chartHeight = 210.dp
    val chartCornerRadius = 12.dp
    val chartPadding = 8.dp
    val chartYAxisWidth = 35.dp
    val chartYAxisGap = 5.dp
    val chartGridStrokeWidth = 1.dp
    val chartLineStrokeWidth = 2.4.dp
    val chartGlowStrokeWidth = 7.dp
    val chartPointRadius = 3.dp
    val chartAxisGap = 7.dp

    val summaryGap = 8.dp
    val summaryCardMinimumHeight = 72.dp
    val summaryValueSize = 18.sp

    val tableHeaderVerticalPadding = 8.dp
    val tableRowVerticalPadding = 10.dp
    val tableColumnGap = 6.dp
    val tableDividerHeight = 1.dp
    const val tableDateWeight = 1.35f
    const val tableValueWeight = 1f

    val sourceDotSize = 6.dp
    val sourceGap = 6.dp

    // Read state is supplementary; chart/summary/table remain visible underneath it.
    val messageCardMinimumHeight = 96.dp
    val messageGap = 7.dp
    val retryShape = RoundedCornerShape(18.dp)
    val retryHorizontalPadding = 14.dp
    val retryVerticalPadding = 7.dp
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
    val gaugeValueSize = 30.sp
    val gaugeCaptionSize = 10.sp
    val gaugeScaleSize = 9.sp
    val temperatureMetricValueSize = 14.sp
    val powerValueSize = 18.sp
    val powerUnitSize = 11.sp
    val statusValueSize = 9.5.sp
    val modeSettingsTitleSize = 12.sp
    val modeSettingsValueSize = 10.5.sp
    val activeChipSize = 9.sp
    val liveHeroStatusSize = 10.sp
    val liveHeroTemperatureSize = 25.sp
    val liveHeroDetailSize = 10.5.sp
}

object AquaCoolingInteractionStyle {
    const val enabledContentAlpha = 1f
    const val disabledContentAlpha = 0.52f
}

object AquaCoolingDashboardAlpha {
    const val selectedBackground = 0.08f
    const val selectedOutline = 0.72f
    const val chartBackground = 0.16f
    const val chartGrid = 0.17f
    const val chartLine = 1f
    const val chartGlow = 0.18f
    const val chartAreaTop = 0.22f
    const val chartAreaBottom = 0f
    const val trackInactive = 0.22f
    const val statusDot = 0.96f
    const val iconContainerBackground = 0.08f
    const val iconContainerOutline = 0.42f
    const val activeChipBackground = 0.20f
    const val divider = 0.72f
    const val liveHeroPanelTop = 0.64f
    const val liveHeroPanelBottom = 0.46f
    const val liveHeroPanelOutline = 0.38f
    const val liveHeroDeviceStandby = 0.90f
    const val liveHeroDeviceUnavailable = 0.66f
    const val liveHeroDeviceOffline = 0.42f
    const val liveHeroAtmosphere = 0.28f
    const val liveHeroWater = 0.40f
    const val liveHeroWaterDepth = 0.24f
    const val liveHeroWaterEdge = 0.56f
    const val liveHeroWavePrimary = 0.42f
    const val liveHeroWaveSecondary = 0.22f
    const val liveHeroGlassPane = 0.07f
    const val liveHeroGlassEdge = 0.34f
    const val liveHeroAirflow = 0.14f
    const val liveHeroDeviceGlow = 0.24f
}

object AquaCoolingHistoryAlpha {
    const val rangeSelectedBackground = 0.92f
    const val rangeIdleBackground = 0.44f
    const val chartBackground = 0.20f
    const val chartGrid = 0.18f
    const val chartGlow = 0.20f
    const val chartArea = 0.10f
    const val divider = 0.54f
    const val sourceDot = 0.96f
    const val retryBackground = 0.10f
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
    const val startAngle = 150f
    const val sweepAngle = 240f
    const val minimumPercent = 0
    const val maximumPercent = 100
}

object AquaCoolingTemperatureChartSpec {
    const val defaultMinimumC = 21f
    const val defaultMaximumC = 30f
    const val expansionStepC = 3f
    const val horizontalGridLineCount = 4
}

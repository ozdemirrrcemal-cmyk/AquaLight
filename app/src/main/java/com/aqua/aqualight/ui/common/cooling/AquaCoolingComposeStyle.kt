package com.aqua.aqualight.ui.common.cooling

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography

/**
 * Central visual contract for the Cooling control surface.
 *
 * The application background and shared card visuals remain owned by the global design system.
 * Cooling owns only its blue operational accent, feature layout and compact type scale here so
 * individual composables never invent visual constants or a parallel card surface.
 */
object AquaCoolingDashboardGeometry {
    val screenHorizontalPadding = 12.dp
    val screenTopPadding = 8.dp
    val screenBottomPadding = 24.dp
    val cardGap = 9.dp
    val splitCardGap = 8.dp

    val cardCornerRadius = AquaDeviceCardGeometry.cornerRadius
    val cardHorizontalPadding = AquaDeviceCardGeometry.contentHorizontalPadding
    val cardVerticalPadding = AquaDeviceCardGeometry.contentVerticalPadding

    val temperatureCardMinimumHeight = 170.dp
    val temperatureChartHeight = 100.dp
    val temperatureChartPadding = 3.dp
    val temperatureYAxisWidth = 30.dp
    val temperatureYAxisGap = 5.dp
    val temperatureMetricWidth = 76.dp
    val temperatureChartMetricGap = 4.dp
    val temperatureMetricContentGap = 8.dp
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
    val chartPointRadius = 2.8.dp
    val chartPointGlowRadius = 6.dp

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
    val optionVerticalPadding = 8.dp
    val optionGap = 5.dp
    val radioSize = 21.dp
    val radioStrokeWidth = 1.5.dp
    val radioCheckStrokeWidth = 1.8.dp

    val modeSettingsCardMinimumHeight = 174.dp
    val modeSettingsHeaderGap = 1.dp
    val modeSettingsContentTopPadding = 4.dp
    val modeSettingsRowHeight = 41.dp
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

    val manualFanPreviewHeight = 164.dp
    val manualFanHousingStrokeWidth = 1.dp
    val manualFanShroudStrokeWidth = 2.dp
    val manualFanAccentStrokeWidth = 3.dp
    val manualFanHubStrokeWidth = 1.dp

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
    const val chartTimeGrid = 0.11f
    const val chartNowGuide = 0.32f
    const val chartLine = 1f
    const val chartGlow = 0.18f
    const val chartAreaTop = 0.22f
    const val chartAreaBottom = 0f
    const val trackInactive = 0.22f
    const val manualFanAmbientIdle = 0.08f
    const val manualFanAmbientActive = 0.24f
    const val manualFanHousingCenter = 0.94f
    const val manualFanHousingEdge = 0.62f
    const val manualFanShroud = 0.74f
    const val manualFanRing = 0.46f
    const val manualFanBladeHighlight = 0.92f
    const val manualFanBladeAccent = 0.68f
    const val manualFanBladeShade = 0.52f
    const val manualFanHubHighlight = 0.90f
    const val manualFanHubShade = 0.60f
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
    const val liveHeroWater = 0.30f
    const val liveHeroWaterDepth = 0.19f
    const val liveHeroWaterSurface = 0.32f
    const val liveHeroWaterSubsurface = 0.10f
    const val liveHeroWaterSpecular = 0.22f
    const val liveHeroWaterCaustic = 0.08f
    const val liveHeroWaterReflection = 0.10f
    const val liveHeroGlassPane = 0.04f
    const val liveHeroGlassEdge = 0.24f
    const val liveHeroAirflow = 0.10f
}

object AquaCoolingHistoryAlpha {
    const val rangeSelectedBackground = 0.92f
    const val rangeIdleBackground = 0.44f
    const val chartBackground = 0.20f
    const val chartGrid = 0.18f
    const val chartMinorGrid = 0.09f
    const val chartGlow = 0.20f
    const val chartArea = 0.10f
    const val divider = 0.54f
    const val sourceDot = 0.96f
    const val retryBackground = 0.10f
    const val axisMinorLabel = 0.70f
}

@Composable
fun aquaCoolingDashboardColors(): AquaDeviceCardColors = aquaDeviceCardColors().copy(
    accent = colorResource(R.color.aqua_card_device_cooling_accent)
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

object AquaCoolingGaugeSpec {
    const val startAngle = 150f
    const val sweepAngle = 240f
    const val minimumPercent = 0
    const val maximumPercent = 100
}

object AquaCoolingHistoryChartSpec {
    const val temperatureAxisLabelCount = 10
    const val temperatureMajorGridStride = 3
    const val timeAxisLabelCount = 5
}

object AquaCoolingTemperatureChartSpec {
    const val defaultMinimumC = 21f
    const val defaultMaximumC = 30f
    const val expansionStepC = 3f
    const val horizontalGridLineCount = 4
}

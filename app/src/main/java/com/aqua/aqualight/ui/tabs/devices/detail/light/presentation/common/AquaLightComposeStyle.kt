package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography

/** Central visual contract for the Light control-surface hero. */
object AquaLightHeroGeometry {
    val screenHorizontalPadding = 12.dp
    val screenTopPadding = 0.dp
    const val heroAspectRatio = HERO_ARTWORK_WIDTH / HERO_ARTWORK_HEIGHT

    val powerBounds = AquaLightHeroBounds(
        left = 0.890f,
        top = 0.510f,
        width = 0.095f,
        height = 0.100f
    )
    val colorTemperatureBounds = AquaLightHeroBounds(
        left = 0.890f,
        top = 0.665f,
        width = 0.095f,
        height = 0.100f
    )
}

/** Central layout contract for the Light root below its hero. */
object AquaLightDashboardGeometry {
    val screenHorizontalPadding = AquaLightHeroGeometry.screenHorizontalPadding
    val screenTopPadding = AquaLightHeroGeometry.screenTopPadding
    val screenBottomPadding = 24.dp
    val cardGap = 9.dp

    val planCardMinimumHeight = 172.dp
    val planCardBottomPadding = 4.dp
    val planHeaderGap = 2.dp
    val planContentTopGap = 2.dp
    val planMarkerLabelHeight = 16.dp
    val planMarkerLabelWidth = 76.dp
    val planPlotHeight = 76.dp
    val planYAxisWidth = 28.dp
    val planYAxisStartOffset = PLAN_Y_AXIS_START_OFFSET_DP.dp
    val planYAxisGap = 2.dp
    val planXAxisHeight = 12.dp
    val planLegendTopGap = 2.dp
    val planLegendHeight = 14.dp
    val planLegendItemGap = 20.dp
    val planLegendDotSize = 8.dp
    val planLegendTextGap = 5.dp
    val planChevronSize = 14.dp
    val planChevronStrokeWidth = 1.6.dp
    const val planChevronStartXFraction = 0.38f
    const val planChevronTopYFraction = 0.22f
    const val planChevronEndXFraction = 0.68f
    const val planChevronMiddleYFraction = 0.50f
    const val planChevronBottomYFraction = 0.78f
    val planGridStrokeWidth = 1.dp
    val planLineStrokeWidth = 1.8.dp
    val planCurrentGuideStrokeWidth = 1.2.dp
    val planCurrentGuideDash = 5.dp
    val planCurrentGuideGap = 4.dp
    val planCurrentPointRadius = 3.dp
    val planActionHeight = 30.dp
    val planActionHorizontalPadding = 16.dp
    val planActionOutlineWidth = 1.dp
    val planActionShape = RoundedCornerShape(percent = 50)
    val planManualMessageActionGap = 8.dp

    val liveOutputCardMinimumHeight = 133.dp
    val liveOutputTitleBottomGap = 10.dp
    val liveOutputRowHeight = 16.dp
    val liveOutputRowGap = 7.dp
    val liveOutputLabelWidth = 42.dp
    val liveOutputLabelTrackGap = 8.dp
    val liveOutputTrackHeight = 8.dp
    val liveOutputTrackValueGap = 12.dp
    val liveOutputValueWidth = 36.dp
    val liveOutputTrackShape = RoundedCornerShape(percent = 50)
    val modeSelectorTitleBottomGap = 6.dp
    val modeSelectorHeight = 40.dp
    val modeSelectorSegmentHeight = 34.dp
    val modeSelectorOuterPadding = 3.dp
    val modeSelectorSegmentGap = 0.dp
    val modeSelectorSegmentShape = RoundedCornerShape(
        AquaDeviceCardGeometry.cornerRadius - modeSelectorOuterPadding
    )


    val controlsHeaderHeight = 30.dp
    val controlsHeaderHorizontalPadding = 2.dp
    val quickSetupButtonHeight = 26.dp
    val quickSetupButtonHorizontalPadding = 10.dp
    val quickSetupButtonContentGap = 5.dp
    val quickSetupIconSize = 15.dp
    val quickSetupOutlineWidth = 1.dp
    val quickSetupShape = RoundedCornerShape(percent = 50)

    val controlsCardMinimumHeight = 156.dp
    val controlsRowHeight = 52.dp
    val controlsRowHorizontalPadding = 14.dp
    val controlsRowIconSize = 27.dp
    val controlsRowIconGap = 12.dp
    val controlsRowTextGap = 1.dp
    val controlsDividerHeight = 1.dp
    val controlsChevronSize = 14.dp
    val controlsChevronStrokeWidth = 1.8.dp
    val controlsTrailingGap = 8.dp
    val activeChipShape = RoundedCornerShape(12.dp)
    val activeChipHorizontalPadding = 10.dp
    val activeChipVerticalPadding = 4.dp

    val secondaryCardGap = 8.dp
    val secondaryCardMinimumHeight = 92.dp
    val secondaryIconSize = 29.dp
    val secondaryIconGap = 10.dp
    val secondaryTitleGap = 2.dp
    val secondaryProgressTopGap = 5.dp
    val secondaryProgressHeight = 3.dp
    val secondaryProgressShape = RoundedCornerShape(percent = 50)
    val systemStatusTopGap = 3.dp
    val systemStatusDotSize = 6.dp
    val systemStatusGap = 6.dp
    const val adaptationCardWeight = 0.96f
    const val systemCardWeight = 1.04f

    val dashboardIconStrokeWidth = 1.7.dp
}

private const val PLAN_Y_AXIS_START_OFFSET_DP = -2

/** Central layout contract for the product-adaptive Manual light surface. */
object AquaLightManualGeometry {
    val screenHorizontalPadding = 8.dp
    val screenTopPadding = 2.dp
    val screenBottomPadding = 16.dp
    val sectionGap = 7.dp

    val controlContentVerticalPadding = 8.dp
    val controlContentHorizontalPadding = 8.dp
    val powerToChannelsGap = 8.dp
    val channelRowGap = 2.dp
    val powerGaugeSize = 116.dp
    val powerGaugeTrackWidth = 12.dp
    val powerGaugeValueGap = 12.dp

    val channelRowHeight = 40.dp
    val channelLabelWidth = 44.dp
    val channelValueWidth = 36.dp
    val channelStepTouchSize = 38.dp
    val channelStepVisualSize = 30.dp
    val channelStepOutlineWidth = 1.dp
    val channelSliderTouchHeight = 34.dp
    val channelSliderTrackHeight = 7.dp
    val channelSliderThumbRadius = 8.dp
    val channelSliderHorizontalInset = 1.dp

    val bannerCornerRadius = AquaDeviceCardGeometry.cornerRadius
    val bannerOutlineWidth = AquaDeviceCardGeometry.outlineWidth
    val bannerHorizontalPadding = 12.dp
    val bannerVerticalPadding = 10.dp
    val bannerIconSize = 20.dp
    val bannerContentGap = 10.dp
    val bannerTextGap = 2.dp

    val quickSceneButtonHeight = 46.dp
    val quickSceneButtonGap = 6.dp
    val quickSceneButtonCornerRadius = 12.dp
    val quickSceneSwatchSize = 20.dp
    val quickSceneContentGap = 8.dp
    val quickSceneHorizontalPadding = 10.dp

    val actionButtonHeight = 54.dp
    val actionButtonGap = 8.dp
    val actionButtonCornerRadius = 13.dp
    val actionButtonOutlineWidth = 1.dp
    val actionButtonIconSize = 24.dp
    val actionButtonContentGap = 8.dp
    val actionButtonHorizontalPadding = 10.dp

    val powerOffButtonHeight = actionButtonHeight
    val powerOffIconSize = 24.dp
    val powerOffContentGap = 7.dp
    val powerOffTextGap = 2.dp

    val informationIconSize = 18.dp
    val informationContentGap = 7.dp
    val informationHorizontalPadding = 8.dp
}

object AquaLightTankCardGeometry {
    val contentPadding = AquaDeviceCardGeometry.contentHorizontalPadding
    val verticalPadding = 8.dp
    val cardMinimumHeight = 0.dp
    val mediaSize = 58.dp
    val mediaCornerRadius = RoundedCornerShape(14.dp)
    val mediaImageSize = 48.dp
    val headerGap = 8.dp
    val titleIconSize = 20.dp
    val titleIconGap = 6.dp
    val headerRowGap = 4.dp
    val statusChipGap = 6.dp
    val statusScheduleGap = 6.dp
    val statusDotSize = 7.dp
    val statusContentGap = 6.dp
    val modeGlyphSize = 19.dp
    val modeGlyphBorderWidth = 1.dp
    val modeGlyphGap = 6.dp
    val scheduleTopGap = 6.dp
    val scheduleGap = 5.dp
    val scheduleIconSize = 19.dp
    val scheduleIconGap = 4.dp
    val scheduleDividerWidth = 1.dp
    val scheduleDividerHeight = 32.dp
    val dividerTopGap = 5.dp
    val dividerBottomGap = 5.dp
    val sectionTitleBottomGap = 4.dp
    val channelRowHeight = 15.dp
    val channelRowGap = 3.dp
    val channelTrackHeight = 7.dp
    val channelLabelWidth = 48.dp
    val channelLabelGap = 6.dp
    val channelValueGap = 8.dp
    val channelValueWidth = 28.dp
}

object AquaLightTankCardAlpha {
    const val offlineDetails = 0.38f
    const val offlineMedia = 0.62f
    const val statusSurface = 0.16f
    const val modeSurface = 0.14f
}

object AquaLightManualAlpha {
    const val gaugeTrack = 0.24f
    const val channelTrack = 0.24f
    const val disabledControl = 0.38f
    const val bannerSurface = 0.10f
    const val buttonPressed = 0.14f
}

/** Central layout contract for the reusable Manual and Custom light library. */
object AquaLightLibraryGeometry {
    val screenHorizontalPadding = 12.dp
    val screenTopPadding = 4.dp
    val screenBottomPadding = 24.dp
    val sectionGap = 10.dp
    val cardGap = 8.dp

    val tabHeight = 44.dp
    val tabCornerRadius = 13.dp
    val tabOutlineWidth = AquaDeviceCardGeometry.outlineWidth
    val tabInnerPadding = 4.dp

    val sectionHeaderHorizontalPadding = 2.dp
    val badgeCornerRadius = 9.dp

    val customDaysMaxWidth = 94.dp
    val customMetadataGap = 6.dp

    val emptyIconSize = 42.dp
    val emptyVerticalPadding = 34.dp
    val emptyTextGap = 6.dp
    val informationIconSize = 18.dp
    val informationTouchSize = 32.dp
    val informationPopupMaxWidth = 260.dp
}

object AquaLightLibraryAlpha {
    const val selectedTabSurface = 0.20f
}

object AquaLightManualTypographySpec {
    val powerValueFontSize = 22.sp
    val powerValueLineHeight = 26.sp
}

object AquaLightDashboardAlpha {
    const val horizontalGrid = 0.40f
    const val verticalGrid = 0.30f
    const val currentGuide = 0.88f
    const val inactiveLine = 0.96f
    const val liveOutputRail = 0.22f
    const val liveOutputFillStart = 0.86f
    const val disabledControl = 0.38f
    const val activeChipBackground = 0.20f
    const val controlsDivider = 0.72f
}

object AquaLightPlanChartSpec {
    const val minimumHour = 0
    const val maximumHour = 24
    const val hourStep = 2
    const val verticalGridHourStep = 1
    const val minimumPercent = 0
    const val maximumPercent = 100
    const val percentStep = 25
}

object AquaLightManualControlSpec {
    const val minimumPercent = 0
    const val maximumPercent = 100
    const val stepPercent = 1
}

@Immutable
data class AquaLightPlanChartColors(
    val grid: Color,
    val currentGuide: Color
)

@Immutable
data class AquaLightLiveOutputColors(
    val rail: Color
)

@Immutable
data class AquaLightLiveOutputTypography(
    val title: TextStyle,
    val label: TextStyle,
    val value: TextStyle
)

@Immutable
data class AquaLightManualColors(
    val card: AquaDeviceCardColors,
    val red: Color,
    val green: Color,
    val blue: Color,
    val white: Color,
    val fish: Color,
    val shrimp: Color,
    val action: Color
)

@Immutable
data class AquaLightHeroBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

@Immutable
data class AquaLightHeroTypography(
    val metricValue: TextStyle
)

private val interSemiBold = FontFamily(Font(R.font.inter_semibold))

@Composable
fun aquaLightHeroTypography(): AquaLightHeroTypography =
    AquaLightHeroTypography(
        metricValue = TextStyle(
            color = colorResource(R.color.aqua_content_on_dark),
            fontFamily = interSemiBold,
            fontSize = 10.5.sp,
            lineHeight = 13.sp
        )
    )

@Composable
fun aquaLightDashboardColors(): AquaDeviceCardColors = aquaDeviceCardColors().copy(
    accent = colorResource(R.color.aqua_card_device_light_accent)
)

fun aquaLightDashboardTypography(
    colors: AquaDeviceCardColors
): AquaDeviceCardTypography = aquaDeviceCardTypography(colors)

@Composable
fun aquaLightPlanChartColors(
    colors: AquaDeviceCardColors
): AquaLightPlanChartColors = AquaLightPlanChartColors(
    grid = colors.outline,
    currentGuide = colors.primaryText
)

@Composable
fun aquaLightLiveOutputColors(
    colors: AquaDeviceCardColors
): AquaLightLiveOutputColors = AquaLightLiveOutputColors(
    rail = colors.secondaryText.copy(alpha = AquaLightDashboardAlpha.liveOutputRail)
)

fun aquaLightLiveOutputTypography(
    colors: AquaDeviceCardColors
): AquaLightLiveOutputTypography {
    val typography = aquaLightDashboardTypography(colors)
    return AquaLightLiveOutputTypography(
        title = typography.title,
        label = typography.body,
        value = typography.body.copy(fontFamily = interSemiBold)
    )
}

@Composable
fun aquaLightManualColors(): AquaLightManualColors {
    val cardColors = aquaLightDashboardColors()
    return AquaLightManualColors(
        card = cardColors,
        red = colorResource(R.color.aqua_card_state_danger),
        green = colorResource(R.color.aqua_accent),
        blue = colorResource(R.color.aqua_button_blue),
        white = colorResource(R.color.aqua_content_on_dark),
        fish = colorResource(R.color.aqua_card_state_warning),
        shrimp = colorResource(R.color.aqua_light_preset_shrimp_swatch),
        action = colorResource(R.color.aqua_button_blue)
    )
}

private const val HERO_ARTWORK_WIDTH = 1_942f
private const val HERO_ARTWORK_HEIGHT = 809f

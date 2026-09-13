@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.common.light

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
    val heroOutlineWidth = AquaDeviceCardGeometry.outlineWidth
    const val heroAspectRatio = 1942f / 809f

    val titleBounds = AquaLightHeroBounds(
        left = 0.042f,
        top = 0.245f,
        width = 0.275f,
        height = 0.140f
    )
    val subtitleBounds = AquaLightHeroBounds(
        left = 0.042f,
        top = 0.360f,
        width = 0.285f,
        height = 0.085f
    )
    val statusBounds = AquaLightHeroBounds(
        left = 0.042f,
        top = 0.455f,
        width = 0.232f,
        height = 0.149f
    )
    val powerBounds = AquaLightHeroBounds(
        left = 0.890f,
        top = 0.510f,
        width = 0.095f,
        height = 0.100f
    )
    val estimatedBounds = AquaLightHeroBounds(
        left = 0.890f,
        top = 0.595f,
        width = 0.095f,
        height = 0.070f
    )
    val colorTemperatureBounds = AquaLightHeroBounds(
        left = 0.890f,
        top = 0.665f,
        width = 0.095f,
        height = 0.100f
    )

    val statusHorizontalPadding = 5.dp
    val statusIconSize = 14.dp
    val statusIconGlyphSize = 10.dp
    val statusContentGap = 5.dp
}

/** Central layout contract for the Light root below its hero. */
object AquaLightDashboardGeometry {
    val screenHorizontalPadding = AquaLightHeroGeometry.screenHorizontalPadding
    val screenTopPadding = AquaLightHeroGeometry.screenTopPadding
    val screenBottomPadding = 24.dp
    val cardGap = 9.dp

    val planCardMinimumHeight = 187.dp
    val planHeaderGap = 2.dp
    val planContentTopGap = 4.dp
    val planMarkerLabelHeight = 18.dp
    val planMarkerLabelWidth = 76.dp
    val planPlotHeight = 76.dp
    val planYAxisWidth = 31.dp
    val planYAxisStartOffset = (-4).dp
    val planYAxisGap = 6.dp
    val planXAxisHeight = 14.dp
    val planLegendTopGap = 7.dp
    val planLegendHeight = 16.dp
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

/** Central layout contract for the product-adaptive Manual light surface. */
object AquaLightManualGeometry {
    val screenHorizontalPadding = AquaLightDashboardGeometry.screenHorizontalPadding
    val screenTopPadding = 2.dp
    val screenBottomPadding = AquaLightDashboardGeometry.screenBottomPadding
    val sectionGap = AquaLightDashboardGeometry.cardGap

    val controlContentVerticalPadding = 12.dp
    val controlContentHorizontalPadding = 12.dp
    val controlContentGap = 8.dp
    val powerGaugeSize = 132.dp
    val powerGaugeTrackWidth = 14.dp
    val powerGaugeValueGap = 4.dp

    val channelRowHeight = 52.dp
    val channelLabelWidth = 52.dp
    val channelValueWidth = 42.dp
    val channelStepTouchSize = 48.dp
    val channelStepVisualSize = 38.dp
    val channelStepOutlineWidth = 1.dp
    val channelSliderTouchHeight = 48.dp
    val channelSliderTrackHeight = 8.dp
    val channelSliderThumbRadius = 10.dp
    val channelSliderHorizontalInset = 2.dp

    val bannerCornerRadius = AquaDeviceCardGeometry.cornerRadius
    val bannerOutlineWidth = AquaDeviceCardGeometry.outlineWidth
    val bannerHorizontalPadding = 12.dp
    val bannerVerticalPadding = 10.dp
    val bannerIconSize = 20.dp
    val bannerContentGap = 10.dp
    val bannerTextGap = 2.dp

    val quickSceneButtonHeight = 52.dp
    val quickSceneButtonGap = 8.dp
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

    val powerOffButtonHeight = 72.dp
    val powerOffIconSize = 28.dp
    val powerOffContentGap = 7.dp
    val powerOffTextGap = 2.dp

    val informationIconSize = 18.dp
    val informationContentGap = 7.dp
    val informationHorizontalPadding = 8.dp
}

object AquaLightManualAlpha {
    const val gaugeTrack = 0.24f
    const val channelTrack = 0.24f
    const val disabledControl = 0.38f
    const val bannerSurface = 0.10f
    const val buttonPressed = 0.14f
}

object AquaLightManualTypographySpec {
    val powerValueFontSize = 25.sp
    val powerValueLineHeight = 30.sp
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
    const val minimumPercent = 0
    const val maximumPercent = 100
    const val percentStep = 25
    const val previewHour = 12
    const val previewMinute = 0
}

object AquaLightLiveOutputPreviewSpec {
    const val redPercent = 20
    const val greenPercent = 30
    const val bluePercent = 40
    const val whitePercent = 50
}

object AquaLightControlsPreviewSpec {
    const val fullPercent = 100
    const val programCount = 3
    const val customCurvePointCount = 12
    const val adaptationActive = true
    const val adaptationDaysRemaining = 12
    const val adaptationPercent = 82
    const val systemTemperatureCelsius = 42.8
    const val systemFanOnePercent = 35
    const val systemFanTwoPercent = 35
}

/** UI-only values used until the Manual application boundary is connected. */
object AquaLightManualPreviewSpec {
    const val estimatedPowerWatts = 46
    const val estimatedPowerRatio = 0.46f
    const val redPercent = 20
    const val greenPercent = 30
    const val bluePercent = 40
    const val whitePercent = 50
    const val minimumPercent = 0
    const val maximumPercent = 100
    const val stepPercent = 1
}

@Immutable
data class AquaLightPlanChartColors(
    val red: Color,
    val green: Color,
    val blue: Color,
    val white: Color,
    val grid: Color,
    val currentGuide: Color
)

@Immutable
data class AquaLightLiveOutputColors(
    val rail: Color,
    val red: Color,
    val green: Color,
    val blue: Color,
    val white: Color
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
@Suppress("LongParameterList")
data class AquaLightHeroColors(
    val primaryText: Color,
    val secondaryText: Color,
    val healthyAccent: Color,
    val healthySurface: Color,
    val attentionAccent: Color,
    val neutralAccent: Color,
    val neutralSurface: Color,
    val iconContent: Color
)

@Immutable
data class AquaLightHeroTypography(
    val title: TextStyle,
    val subtitle: TextStyle,
    val status: TextStyle,
    val metricValue: TextStyle,
    val metricCaption: TextStyle
)

private val poppinsBold = FontFamily(Font(R.font.poppins_bold))
private val interRegular = FontFamily(Font(R.font.inter_regular))
private val interMedium = FontFamily(Font(R.font.inter_medium))
private val interSemiBold = FontFamily(Font(R.font.inter_semibold))

@Composable
fun aquaLightHeroColors(): AquaLightHeroColors = AquaLightHeroColors(
    primaryText = colorResource(R.color.aqua_content_on_dark),
    secondaryText = colorResource(R.color.aqua_content_primary_soft),
    healthyAccent = colorResource(R.color.aqua_accent),
    healthySurface = colorResource(R.color.aqua_surface_positive),
    attentionAccent = colorResource(R.color.aqua_card_state_warning),
    neutralAccent = colorResource(R.color.aqua_card_text_secondary),
    neutralSurface = colorResource(R.color.aqua_card_device_media_surface),
    iconContent = colorResource(R.color.aqua_card_device_surface)
)

fun aquaLightHeroTypography(colors: AquaLightHeroColors): AquaLightHeroTypography =
    AquaLightHeroTypography(
        title = TextStyle(
            color = colors.primaryText,
            fontFamily = poppinsBold,
            fontSize = 17.sp,
            lineHeight = 20.sp
        ),
        subtitle = TextStyle(
            color = colors.secondaryText,
            fontFamily = interRegular,
            fontSize = 11.sp,
            lineHeight = 14.sp
        ),
        status = TextStyle(
            color = colors.healthyAccent,
            fontFamily = interSemiBold,
            fontSize = 8.sp,
            lineHeight = 10.sp
        ),
        metricValue = TextStyle(
            color = colors.primaryText,
            fontFamily = interSemiBold,
            fontSize = 10.5.sp,
            lineHeight = 13.sp
        ),
        metricCaption = TextStyle(
            color = colors.secondaryText,
            fontFamily = interMedium,
            fontSize = 7.5.sp,
            lineHeight = 10.sp
        )
    )

@Composable
fun aquaLightDashboardColors(): AquaDeviceCardColors = aquaDeviceCardColors().copy(
    accent = colorResource(R.color.aqua_card_device_cooling_accent)
)

fun aquaLightDashboardTypography(
    colors: AquaDeviceCardColors
): AquaDeviceCardTypography = aquaDeviceCardTypography(colors)

@Composable
fun aquaLightPlanChartColors(
    colors: AquaDeviceCardColors
): AquaLightPlanChartColors = AquaLightPlanChartColors(
    red = colorResource(R.color.aqua_card_state_danger),
    green = colorResource(R.color.aqua_accent),
    blue = colorResource(R.color.aqua_button_blue),
    white = colorResource(R.color.aqua_content_on_dark),
    grid = colors.outline,
    currentGuide = colors.primaryText
)

@Composable
fun aquaLightLiveOutputColors(
    colors: AquaDeviceCardColors
): AquaLightLiveOutputColors {
    val channelColors = aquaLightPlanChartColors(colors)
    return AquaLightLiveOutputColors(
        rail = colors.secondaryText.copy(alpha = AquaLightDashboardAlpha.liveOutputRail),
        red = channelColors.red,
        green = channelColors.green,
        blue = channelColors.blue,
        white = channelColors.white
    )
}

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

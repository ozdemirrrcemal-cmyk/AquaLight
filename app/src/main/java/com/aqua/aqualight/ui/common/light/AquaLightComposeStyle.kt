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
    val screenTopPadding = 8.dp
    val heroOutlineWidth = AquaDeviceCardGeometry.outlineWidth
    const val heroAspectRatio = 16f / 9f

    val titleBounds = AquaLightHeroBounds(
        left = 0.057f,
        top = 0.270f,
        width = 0.295f,
        height = 0.090f
    )
    val subtitleBounds = AquaLightHeroBounds(
        left = 0.057f,
        top = 0.382f,
        width = 0.300f,
        height = 0.072f
    )
    val statusBounds = AquaLightHeroBounds(
        left = 0.057f,
        top = 0.498f,
        width = 0.242f,
        height = 0.120f
    )
    val powerBounds = AquaLightHeroBounds(
        left = 0.885f,
        top = 0.520f,
        width = 0.092f,
        height = 0.064f
    )
    val estimatedBounds = AquaLightHeroBounds(
        left = 0.882f,
        top = 0.588f,
        width = 0.095f,
        height = 0.046f
    )
    val colorTemperatureBounds = AquaLightHeroBounds(
        left = 0.882f,
        top = 0.704f,
        width = 0.095f,
        height = 0.066f
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

    val planCardMinimumHeight = 196.dp
    val planHeaderGap = 2.dp
    val planContentTopGap = 4.dp
    val planMarkerLabelHeight = 18.dp
    val planMarkerLabelWidth = 76.dp
    val planPlotHeight = 76.dp
    val planYAxisWidth = 31.dp
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

    val librarySheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val librarySheetMinimumHeight = 260.dp
    val librarySheetHorizontalPadding = 24.dp
    val librarySheetBottomPadding = 28.dp
    val libraryDragHandleWidth = 36.dp
    val libraryDragHandleHeight = 4.dp
    val libraryDragHandleTopPadding = 10.dp
    val libraryTitleTopPadding = 18.dp
    val libraryEmptyTopPadding = 32.dp
    val libraryEmptyIconContainerSize = 64.dp
    val libraryEmptyIconSize = 32.dp
    val libraryEmptyTitleTopPadding = 16.dp
    val libraryEmptyDescriptionTopPadding = 6.dp
}

object AquaLightDashboardAlpha {
    const val horizontalGrid = 0.20f
    const val verticalGrid = 0.12f
    const val currentGuide = 0.88f
    const val inactiveLine = 0.96f
    const val libraryScrim = 0.62f
    const val libraryHandle = 0.46f
    const val libraryIconSurface = 0.10f
    const val libraryIconOutline = 0.42f
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
    accent = colorResource(R.color.aqua_accent)
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
fun aquaLightLibraryScrimColor(): Color = colorResource(
    R.color.aqua_system_bar_surface_argb
).copy(alpha = AquaLightDashboardAlpha.libraryScrim)

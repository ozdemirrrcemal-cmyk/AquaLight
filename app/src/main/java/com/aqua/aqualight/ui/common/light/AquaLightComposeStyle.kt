@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.common.light

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
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry

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

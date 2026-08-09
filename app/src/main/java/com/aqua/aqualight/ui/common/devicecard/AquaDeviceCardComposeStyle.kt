package com.aqua.aqualight.ui.common.devicecard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.R

/**
 * Central Compose contract for compact device-detail cards.
 *
 * Colors remain owned by the shared Android resource palette. Feature UI may specialize content,
 * but must not invent a parallel card surface, outline, typography, or state-color system.
 */
@Immutable
data class AquaDeviceCardColors(
    val surface: Color,
    val outline: Color,
    val mediaSurface: Color,
    val mediaOutline: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val warning: Color,
    val danger: Color
)

@Immutable
data class AquaDeviceCardTypography(
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val micro: TextStyle
)

object AquaDeviceCardGeometry {
    val cornerRadius = 18.dp
    val contentHorizontalPadding = 14.dp
    val contentVerticalPadding = 12.dp
    val outlineWidth = 1.dp
    val markerSize = 22.dp
    val markerCornerRadius = 7.dp
    val statusCornerRadius = 11.dp
    val statusHorizontalPadding = 8.dp
    val statusVerticalPadding = 4.dp
    val contentGap = 10.dp
    val compactGap = 7.dp
}

private val InterRegular = FontFamily(Font(R.font.inter_regular))
private val InterMedium = FontFamily(Font(R.font.inter_medium))
private val InterSemiBold = FontFamily(Font(R.font.inter_semibold))

@Composable
fun aquaDeviceCardColors(): AquaDeviceCardColors = AquaDeviceCardColors(
    surface = colorResource(R.color.aqua_card_device_surface),
    outline = colorResource(R.color.aqua_card_device_outline),
    mediaSurface = colorResource(R.color.aqua_card_device_media_surface),
    mediaOutline = colorResource(R.color.aqua_card_device_media_outline),
    primaryText = colorResource(R.color.aqua_card_text_primary),
    secondaryText = colorResource(R.color.aqua_card_text_secondary),
    accent = colorResource(R.color.aqua_accent),
    warning = colorResource(R.color.dialog_icon_warning),
    danger = colorResource(R.color.dialog_icon_error)
)

fun aquaDeviceCardTypography(colors: AquaDeviceCardColors): AquaDeviceCardTypography =
    AquaDeviceCardTypography(
        title = TextStyle(
            color = colors.primaryText,
            fontFamily = InterSemiBold,
            fontSize = 15.sp,
            lineHeight = 18.sp
        ),
        body = TextStyle(
            color = colors.primaryText,
            fontFamily = InterMedium,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        caption = TextStyle(
            color = colors.secondaryText,
            fontFamily = InterRegular,
            fontSize = 11.sp,
            lineHeight = 14.sp
        ),
        micro = TextStyle(
            color = colors.secondaryText,
            fontFamily = InterMedium,
            fontSize = 10.sp,
            lineHeight = 12.sp
        )
    )

@Composable
fun AquaDeviceCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = aquaDeviceCardColors()
    val shape = RoundedCornerShape(AquaDeviceCardGeometry.cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = colors.outline,
                shape = shape
            )
            .padding(
                horizontal = AquaDeviceCardGeometry.contentHorizontalPadding,
                vertical = AquaDeviceCardGeometry.contentVerticalPadding
            ),
        content = content
    )
}

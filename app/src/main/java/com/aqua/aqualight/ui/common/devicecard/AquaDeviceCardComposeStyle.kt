package com.aqua.aqualight.ui.common.devicecard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
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
    val compactTitle: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val micro: TextStyle
)

object AquaDeviceCardGeometry {
    val cornerRadius = CARD_CORNER_RADIUS_DP.dp
    val contentHorizontalPadding = CONTENT_HORIZONTAL_PADDING_DP.dp
    val contentVerticalPadding = CONTENT_VERTICAL_PADDING_DP.dp
    val compactContentPadding = COMPACT_CONTENT_PADDING_DP.dp
    val outlineWidth = OUTLINE_WIDTH_DP.dp
    val markerSize = MARKER_SIZE_DP.dp
    val markerCornerRadius = MARKER_CORNER_RADIUS_DP.dp
    val statusCornerRadius = STATUS_CORNER_RADIUS_DP.dp
    val statusHorizontalPadding = STATUS_HORIZONTAL_PADDING_DP.dp
    val statusVerticalPadding = STATUS_VERTICAL_PADDING_DP.dp
    val contentGap = CONTENT_GAP_DP.dp
    val compactGap = COMPACT_GAP_DP.dp
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
    accent = colorResource(R.color.aqua_card_state_active),
    warning = colorResource(R.color.aqua_card_state_warning),
    danger = colorResource(R.color.aqua_card_state_danger)
)

fun aquaDeviceCardTypography(colors: AquaDeviceCardColors): AquaDeviceCardTypography =
    AquaDeviceCardTypography(
        title = TextStyle(
            color = colors.primaryText,
            fontFamily = InterSemiBold,
            fontSize = TITLE_FONT_SIZE_SP.sp,
            lineHeight = TITLE_LINE_HEIGHT_SP.sp
        ),
        compactTitle = TextStyle(
            color = colors.primaryText,
            fontFamily = InterSemiBold,
            fontSize = COMPACT_TITLE_FONT_SIZE_SP.sp,
            lineHeight = COMPACT_TITLE_LINE_HEIGHT_SP.sp
        ),
        body = TextStyle(
            color = colors.primaryText,
            fontFamily = InterMedium,
            fontSize = BODY_FONT_SIZE_SP.sp,
            lineHeight = BODY_LINE_HEIGHT_SP.sp
        ),
        caption = TextStyle(
            color = colors.secondaryText,
            fontFamily = InterRegular,
            fontSize = CAPTION_FONT_SIZE_SP.sp,
            lineHeight = CAPTION_LINE_HEIGHT_SP.sp
        ),
        micro = TextStyle(
            color = colors.secondaryText,
            fontFamily = InterMedium,
            fontSize = MICRO_FONT_SIZE_SP.sp,
            lineHeight = MICRO_LINE_HEIGHT_SP.sp
        )
    )

@Composable
fun AquaDeviceCardSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = AquaDeviceCardGeometry.contentHorizontalPadding,
        vertical = AquaDeviceCardGeometry.contentVerticalPadding
    ),
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
            .padding(contentPadding),
        content = content
    )
}

private const val CARD_CORNER_RADIUS_DP = 18
private const val CONTENT_HORIZONTAL_PADDING_DP = 14
private const val CONTENT_VERTICAL_PADDING_DP = 12
private const val COMPACT_CONTENT_PADDING_DP = 10
private const val OUTLINE_WIDTH_DP = 1
private const val MARKER_SIZE_DP = 22
private const val MARKER_CORNER_RADIUS_DP = 7
private const val STATUS_CORNER_RADIUS_DP = 11
private const val STATUS_HORIZONTAL_PADDING_DP = 8
private const val STATUS_VERTICAL_PADDING_DP = 4
private const val CONTENT_GAP_DP = 10
private const val COMPACT_GAP_DP = 7
private const val TITLE_FONT_SIZE_SP = 15
private const val TITLE_LINE_HEIGHT_SP = 18
private const val COMPACT_TITLE_FONT_SIZE_SP = 11
private const val COMPACT_TITLE_LINE_HEIGHT_SP = 14
private const val BODY_FONT_SIZE_SP = 12
private const val BODY_LINE_HEIGHT_SP = 16
private const val CAPTION_FONT_SIZE_SP = 11
private const val CAPTION_LINE_HEIGHT_SP = 14
private const val MICRO_FONT_SIZE_SP = 10
private const val MICRO_LINE_HEIGHT_SP = 12

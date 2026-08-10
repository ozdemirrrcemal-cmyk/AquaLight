@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.common.devicemenu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.R

/** Shared visual contract for compact, grouped device-control menus. */
@Immutable
data class AquaDeviceMenuColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val danger: Color
)

@Immutable
data class AquaDeviceMenuTypography(
    val eyebrow: TextStyle,
    val heroTitle: TextStyle,
    val heroBody: TextStyle,
    val sectionLabel: TextStyle,
    val rowTitle: TextStyle,
    val rowBody: TextStyle
)

enum class AquaDeviceMenuTone {
    ACCENT,
    NEUTRAL,
    DANGER
}

object AquaDeviceMenuGeometry {
    val screenHorizontalPadding = SCREEN_HORIZONTAL_PADDING_DP.dp
    val screenTopPadding = SCREEN_TOP_PADDING_DP.dp
    val screenBottomPadding = SCREEN_BOTTOM_PADDING_DP.dp
    val sectionGap = SECTION_GAP_DP.dp
    val sectionLabelBottomSpacing = SECTION_LABEL_BOTTOM_SPACING_DP.dp
    val surfaceRadius = SURFACE_RADIUS_DP.dp
    val surfaceOutlineWidth = SURFACE_OUTLINE_WIDTH_DP.dp
    val heroPadding = HERO_PADDING_DP.dp
    val heroContentGap = HERO_CONTENT_GAP_DP.dp
    val heroAccentWidth = HERO_ACCENT_WIDTH_DP.dp
    val heroAccentHeight = HERO_ACCENT_HEIGHT_DP.dp
    val heroAccentRadius = HERO_ACCENT_RADIUS_DP.dp
    val rowMinHeight = ROW_MIN_HEIGHT_DP.dp
    val rowHorizontalPadding = ROW_HORIZONTAL_PADDING_DP.dp
    val rowVerticalPadding = ROW_VERTICAL_PADDING_DP.dp
    val rowContentGap = ROW_CONTENT_GAP_DP.dp
    val rowTextGap = ROW_TEXT_GAP_DP.dp
    val iconContainerSize = ICON_CONTAINER_SIZE_DP.dp
    val iconContainerRadius = ICON_CONTAINER_RADIUS_DP.dp
    val iconSize = ICON_SIZE_DP.dp
    val trailingIconSize = TRAILING_ICON_SIZE_DP.dp
    val dividerIndent = DIVIDER_INDENT_DP.dp
    val dividerHeight = DIVIDER_HEIGHT_DP.dp
    val sectionContentPadding = SECTION_CONTENT_PADDING_DP.dp
    val compactGap = COMPACT_GAP_DP.dp
    val choiceChipMinHeight = CHOICE_CHIP_MIN_HEIGHT_DP.dp
    val choiceChipRadius = CHOICE_CHIP_RADIUS_DP.dp
    val choiceChipHorizontalPadding = CHOICE_CHIP_HORIZONTAL_PADDING_DP.dp
    val toggleWidth = TOGGLE_WIDTH_DP.dp
    val toggleHeight = TOGGLE_HEIGHT_DP.dp
    val toggleThumbSize = TOGGLE_THUMB_SIZE_DP.dp
    val togglePadding = TOGGLE_PADDING_DP.dp
    val valueMaxWidth = VALUE_MAX_WIDTH_DP.dp
}

private val InterRegular = FontFamily(Font(R.font.inter_regular))
private val InterSemiBold = FontFamily(Font(R.font.inter_semibold))

@Composable
fun aquaDeviceMenuColors(): AquaDeviceMenuColors = AquaDeviceMenuColors(
    background = colorResource(R.color.background_color),
    surface = colorResource(R.color.aqua_card_device_surface),
    surfaceRaised = colorResource(R.color.aqua_card_device_media_surface),
    outline = colorResource(R.color.aqua_card_device_outline),
    divider = colorResource(R.color.aqua_card_device_outline),
    textPrimary = colorResource(R.color.aqua_card_text_primary),
    textSecondary = colorResource(R.color.aqua_card_text_secondary),
    accent = colorResource(R.color.aqua_card_state_active),
    danger = colorResource(R.color.aqua_card_state_danger)
)

fun aquaDeviceMenuTypography(colors: AquaDeviceMenuColors): AquaDeviceMenuTypography =
    AquaDeviceMenuTypography(
        eyebrow = TextStyle(
            color = colors.accent,
            fontFamily = InterSemiBold,
            fontSize = EYEBROW_FONT_SIZE_SP.sp,
            lineHeight = EYEBROW_LINE_HEIGHT_SP.sp,
            letterSpacing = EYEBROW_LETTER_SPACING_SP.sp
        ),
        heroTitle = TextStyle(
            color = colors.textPrimary,
            fontFamily = InterSemiBold,
            fontSize = HERO_TITLE_FONT_SIZE_SP.sp,
            lineHeight = HERO_TITLE_LINE_HEIGHT_SP.sp
        ),
        heroBody = TextStyle(
            color = colors.textSecondary,
            fontFamily = InterRegular,
            fontSize = HERO_BODY_FONT_SIZE_SP.sp,
            lineHeight = HERO_BODY_LINE_HEIGHT_SP.sp
        ),
        sectionLabel = TextStyle(
            color = colors.textSecondary,
            fontFamily = InterSemiBold,
            fontSize = SECTION_LABEL_FONT_SIZE_SP.sp,
            lineHeight = SECTION_LABEL_LINE_HEIGHT_SP.sp,
            letterSpacing = SECTION_LABEL_LETTER_SPACING_SP.sp
        ),
        rowTitle = TextStyle(
            color = colors.textPrimary,
            fontFamily = InterSemiBold,
            fontSize = ROW_TITLE_FONT_SIZE_SP.sp,
            lineHeight = ROW_TITLE_LINE_HEIGHT_SP.sp
        ),
        rowBody = TextStyle(
            color = colors.textSecondary,
            fontFamily = InterRegular,
            fontSize = ROW_BODY_FONT_SIZE_SP.sp,
            lineHeight = ROW_BODY_LINE_HEIGHT_SP.sp
        )
    )

@Composable
fun AquaDeviceMenuSectionSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = aquaDeviceMenuColors()
    val shape = RoundedCornerShape(AquaDeviceMenuGeometry.surfaceRadius)
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(
                width = AquaDeviceMenuGeometry.surfaceOutlineWidth,
                color = colors.outline,
                shape = shape
            ),
        content = content
    )
}

@Composable
fun AquaDeviceMenuRow(
    title: String,
    description: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    tone: AquaDeviceMenuTone = AquaDeviceMenuTone.ACCENT,
    onClick: (() -> Unit)? = null
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    val iconTint = when (tone) {
        AquaDeviceMenuTone.ACCENT -> colors.accent
        AquaDeviceMenuTone.NEUTRAL -> colors.textSecondary
        AquaDeviceMenuTone.DANGER -> colors.danger
    }
    val iconShape = RoundedCornerShape(AquaDeviceMenuGeometry.iconContainerRadius)
    val interactionModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            role = Role.Button,
            onClick = onClick
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AquaDeviceMenuGeometry.rowMinHeight)
            .semantics(mergeDescendants = true) {}
            .then(interactionModifier)
            .padding(
                horizontal = AquaDeviceMenuGeometry.rowHorizontalPadding,
                vertical = AquaDeviceMenuGeometry.rowVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AquaDeviceMenuGeometry.iconContainerSize)
                .clip(iconShape)
                .background(colors.surfaceRaised)
                .background(iconTint.copy(alpha = ICON_BACKGROUND_ALPHA)),
            contentAlignment = Alignment.Center
        ) {
            DeviceMenuIcon(
                painter = painterResource(iconRes),
                tint = iconTint,
                modifier = Modifier.size(AquaDeviceMenuGeometry.iconSize)
            )
        }

        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AquaDeviceMenuGeometry.rowContentGap)
        ) {
            BasicText(
                text = title,
                style = typography.rowTitle.copy(
                    color = if (tone == AquaDeviceMenuTone.DANGER) {
                        colors.danger
                    } else {
                        colors.textPrimary
                    }
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = description,
                modifier = Modifier.padding(top = AquaDeviceMenuGeometry.rowTextGap),
                style = typography.rowBody,
                maxLines = ROW_DESCRIPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }

        DeviceMenuIcon(
            painter = painterResource(R.drawable.ic_arrow_right),
            tint = if (tone == AquaDeviceMenuTone.DANGER) colors.danger else colors.textSecondary,
            modifier = Modifier.size(AquaDeviceMenuGeometry.trailingIconSize)
        )
    }
}

@Composable
fun AquaDeviceMenuDivider(modifier: Modifier = Modifier) {
    val colors = aquaDeviceMenuColors()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AquaDeviceMenuGeometry.dividerIndent)
            .background(colors.divider)
            .defaultMinSize(minHeight = AquaDeviceMenuGeometry.dividerHeight)
    )
}

/** Shared label/value row for static device setting previews and connected forms. */
@Composable
fun AquaDeviceMenuValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    tone: AquaDeviceMenuTone = AquaDeviceMenuTone.NEUTRAL
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    val valueColor = when (tone) {
        AquaDeviceMenuTone.ACCENT -> colors.accent
        AquaDeviceMenuTone.NEUTRAL -> colors.textPrimary
        AquaDeviceMenuTone.DANGER -> colors.danger
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(AquaDeviceMenuGeometry.sectionContentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = label, style = typography.rowTitle)
            description?.let { supportingText ->
                BasicText(
                    text = supportingText,
                    modifier = Modifier.padding(top = AquaDeviceMenuGeometry.rowTextGap),
                    style = typography.rowBody
                )
            }
        }
        BasicText(
            text = value,
            modifier = Modifier
                .padding(start = AquaDeviceMenuGeometry.compactGap)
                .widthIn(max = AquaDeviceMenuGeometry.valueMaxWidth),
            style = typography.rowTitle.copy(
                color = valueColor,
                textAlign = TextAlign.End
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Non-mutating choice visual used while a setting screen is preview-only. */
@Composable
fun AquaDeviceMenuChoiceChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    val shape = RoundedCornerShape(AquaDeviceMenuGeometry.choiceChipRadius)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = AquaDeviceMenuGeometry.choiceChipMinHeight)
            .clip(shape)
            .background(
                if (selected) colors.accent.copy(alpha = CHOICE_SELECTED_ALPHA) else colors.surfaceRaised
            )
            .border(
                width = AquaDeviceMenuGeometry.surfaceOutlineWidth,
                color = if (selected) colors.accent else colors.outline,
                shape = shape
            )
            .padding(
                horizontal = AquaDeviceMenuGeometry.choiceChipHorizontalPadding,
                vertical = AquaDeviceMenuGeometry.compactGap
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = typography.rowBody.copy(
                color = if (selected) colors.accent else colors.textSecondary,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Accessible, display-only toggle for UI shells that do not bind device data yet. */
@Composable
fun AquaDeviceMenuToggle(
    checked: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceMenuColors()
    val shape = RoundedCornerShape(AquaDeviceMenuGeometry.toggleHeight)
    Box(
        modifier = modifier
            .width(AquaDeviceMenuGeometry.toggleWidth)
            .height(AquaDeviceMenuGeometry.toggleHeight)
            .clip(shape)
            .background(if (checked) colors.accent else colors.surfaceRaised)
            .border(
                width = AquaDeviceMenuGeometry.surfaceOutlineWidth,
                color = if (checked) colors.accent else colors.outline,
                shape = shape
            )
            .semantics { this.contentDescription = contentDescription }
            .padding(AquaDeviceMenuGeometry.togglePadding),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(AquaDeviceMenuGeometry.toggleThumbSize)
                .clip(RoundedCornerShape(AquaDeviceMenuGeometry.toggleThumbSize))
                .background(colors.textPrimary)
        )
    }
}

@Composable
private fun DeviceMenuIcon(
    painter: Painter,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}

private const val ICON_BACKGROUND_ALPHA = 0.12f
private const val CHOICE_SELECTED_ALPHA = 0.14f
private const val ROW_DESCRIPTION_MAX_LINES = 2
private const val SCREEN_HORIZONTAL_PADDING_DP = 16
private const val SCREEN_TOP_PADDING_DP = 14
private const val SCREEN_BOTTOM_PADDING_DP = 28
private const val SECTION_GAP_DP = 20
private const val SECTION_LABEL_BOTTOM_SPACING_DP = 8
private const val SURFACE_RADIUS_DP = 20
private const val SURFACE_OUTLINE_WIDTH_DP = 1
private const val HERO_PADDING_DP = 18
private const val HERO_CONTENT_GAP_DP = 14
private const val HERO_ACCENT_WIDTH_DP = 4
private const val HERO_ACCENT_HEIGHT_DP = 58
private const val HERO_ACCENT_RADIUS_DP = 2
private const val ROW_MIN_HEIGHT_DP = 78
private const val ROW_HORIZONTAL_PADDING_DP = 14
private const val ROW_VERTICAL_PADDING_DP = 13
private const val ROW_CONTENT_GAP_DP = 13
private const val ROW_TEXT_GAP_DP = 3
private const val ICON_CONTAINER_SIZE_DP = 42
private const val ICON_CONTAINER_RADIUS_DP = 13
private const val ICON_SIZE_DP = 23
private const val TRAILING_ICON_SIZE_DP = 20
private const val DIVIDER_INDENT_DP = 69
private const val DIVIDER_HEIGHT_DP = 1
private const val SECTION_CONTENT_PADDING_DP = 16
private const val COMPACT_GAP_DP = 8
private const val CHOICE_CHIP_MIN_HEIGHT_DP = 38
private const val CHOICE_CHIP_RADIUS_DP = 12
private const val CHOICE_CHIP_HORIZONTAL_PADDING_DP = 12
private const val TOGGLE_WIDTH_DP = 46
private const val TOGGLE_HEIGHT_DP = 26
private const val TOGGLE_THUMB_SIZE_DP = 18
private const val TOGGLE_PADDING_DP = 3
private const val VALUE_MAX_WIDTH_DP = 144
private const val EYEBROW_FONT_SIZE_SP = 11
private const val EYEBROW_LINE_HEIGHT_SP = 14
private const val EYEBROW_LETTER_SPACING_SP = 0.7
private const val HERO_TITLE_FONT_SIZE_SP = 20
private const val HERO_TITLE_LINE_HEIGHT_SP = 25
private const val HERO_BODY_FONT_SIZE_SP = 13
private const val HERO_BODY_LINE_HEIGHT_SP = 19
private const val SECTION_LABEL_FONT_SIZE_SP = 11
private const val SECTION_LABEL_LINE_HEIGHT_SP = 14
private const val SECTION_LABEL_LETTER_SPACING_SP = 0.6
private const val ROW_TITLE_FONT_SIZE_SP = 15
private const val ROW_TITLE_LINE_HEIGHT_SP = 19
private const val ROW_BODY_FONT_SIZE_SP = 12
private const val ROW_BODY_LINE_HEIGHT_SP = 17

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightManualColors

@Composable
internal fun CustomOutlinedButton(
    button: CustomOutlinedButtonState,
    appearance: CustomOutlinedButtonAppearance,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val alpha = if (button.enabled) ENABLED_ALPHA else DISABLED_ALPHA
    val foreground = (appearance.contentColor ?: appearance.color).copy(alpha = alpha)
    val shape = RoundedCornerShape(OUTLINED_BUTTON_CORNER_DP.dp)
    Row(
        modifier = modifier.height(appearance.height).clip(shape)
            .background(
                if (appearance.filled) appearance.color.copy(alpha = alpha) else Color.Transparent
            )
            .border(BORDER_WIDTH_DP.dp, appearance.color.copy(alpha = alpha), shape)
            .clearAndSetSemantics { contentDescription = button.description }
            .clickable(enabled = button.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = OUTLINED_BUTTON_PADDING_DP.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ButtonLeadingVisual(appearance, foreground)
        BasicText(
            text = button.label,
            style = TextStyle(color = foreground),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ButtonLeadingVisual(appearance: CustomOutlinedButtonAppearance, color: Color) {
    if (appearance.showPlayIcon) {
        PlayGlyph(color)
        Spacer(Modifier.width(PLAY_TEXT_GAP_DP.dp))
    }
    appearance.iconRes?.let { iconRes ->
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(OUTLINED_ICON_SIZE_DP.dp)
        )
        Spacer(Modifier.width(ICON_TEXT_GAP_DP.dp))
    }
}

@Composable
private fun PlayGlyph(color: Color) {
    Canvas(Modifier.size(PLAY_ICON_SIZE_DP.dp)) {
        val play = Path().apply {
            moveTo(size.width * PLAY_LEFT_FRACTION, size.height * PLAY_TOP_FRACTION)
            lineTo(size.width * PLAY_RIGHT_FRACTION, size.height * PLAY_CENTER_FRACTION)
            lineTo(size.width * PLAY_LEFT_FRACTION, size.height * PLAY_BOTTOM_FRACTION)
            close()
        }
        drawPath(play, color)
    }
}

@Composable
internal fun CompactActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightCustomVisuals
) {
    val alpha = if (enabled) ENABLED_ALPHA else DISABLED_ALPHA
    val shape = RoundedCornerShape(percent = CIRCLE_SHAPE_PERCENT)
    Row(
        modifier = Modifier.height(COMPACT_ACTION_HEIGHT_DP.dp).clip(shape)
            .border(BORDER_WIDTH_DP.dp, visuals.colors.action.copy(alpha = alpha), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = COMPACT_ACTION_PADDING_DP.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_add_24),
            contentDescription = null,
            colorFilter = ColorFilter.tint(visuals.colors.action.copy(alpha = alpha)),
            modifier = Modifier.size(COMPACT_ICON_SIZE_DP.dp)
        )
        BasicText(
            label,
            style = visuals.typography.caption.copy(color = visuals.colors.action.copy(alpha = alpha)),
            modifier = Modifier.padding(start = COMPACT_TEXT_PADDING_DP.dp)
        )
    }
}

@Composable
internal fun SquareIconButton(
    iconRes: Int,
    description: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val alpha = if (enabled) ENABLED_ALPHA else DISABLED_ALPHA
    val shape = RoundedCornerShape(SQUARE_BUTTON_CORNER_DP.dp)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(SQUARE_BUTTON_SIZE_DP.dp).clip(shape)
            .border(BORDER_WIDTH_DP.dp, color.copy(alpha = alpha), shape)
            .clearAndSetSemantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color.copy(alpha = alpha)),
            modifier = Modifier.size(SQUARE_ICON_SIZE_DP.dp)
        )
    }
}

@Composable
internal fun ClockGlyph(color: Color) {
    Canvas(Modifier.size(CLOCK_SIZE_DP.dp)) {
        val strokeWidth = CLOCK_STROKE_DP.dp.toPx()
        drawCircle(color, style = Stroke(width = strokeWidth))
        drawLine(
            color,
            center,
            Offset(center.x, center.y - CLOCK_MINUTE_HAND_DP.dp.toPx()),
            strokeWidth
        )
        drawLine(
            color,
            center,
            Offset(center.x + CLOCK_HOUR_HAND_DP.dp.toPx(), center.y),
            strokeWidth
        )
    }
}

@Composable
internal fun CustomInformation(visuals: DeviceLightCustomVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = INFORMATION_HORIZONTAL_PADDING_DP.dp,
            vertical = INFORMATION_VERTICAL_PADDING_DP.dp
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(visuals.colors.card.secondaryText),
            modifier = Modifier.size(INFORMATION_ICON_SIZE_DP.dp)
        )
        BasicText(
            text = stringResource(R.string.device_light_custom_information),
            style = visuals.typography.micro,
            modifier = Modifier.padding(start = INFORMATION_TEXT_PADDING_DP.dp)
        )
    }
}

@Composable
internal fun SectionHeading(
    title: String,
    subtitle: String,
    visuals: DeviceLightCustomVisuals
) {
    Column {
        BasicText(
            text = title,
            style = visuals.typography.title.copy(color = visuals.colors.card.primaryText)
        )
        BasicText(
            text = subtitle,
            style = visuals.typography.caption.copy(color = visuals.colors.card.secondaryText)
        )
    }
}

internal fun DeviceLightCustomVisuals.channelColor(channel: DeviceLightCustomChannelId): Color =
    when (channel) {
        DeviceLightCustomChannelId.RED -> colors.red
        DeviceLightCustomChannelId.GREEN -> colors.green
        DeviceLightCustomChannelId.BLUE -> colors.blue
        DeviceLightCustomChannelId.WHITE -> colors.white
    }

@Immutable
internal data class DeviceLightCustomVisuals(
    val colors: AquaLightManualColors,
    val typography: AquaDeviceCardTypography
)

@Immutable
internal data class CustomOutlinedButtonState(
    val label: String,
    val description: String,
    val enabled: Boolean
)

@Immutable
internal data class CustomOutlinedButtonAppearance(
    val color: Color,
    val iconRes: Int? = null,
    val height: Dp = DEFAULT_BUTTON_HEIGHT_DP.dp,
    val showPlayIcon: Boolean = false,
    val filled: Boolean = false,
    val contentColor: Color? = null
)

internal const val CIRCLE_SHAPE_PERCENT = 50
private const val ENABLED_ALPHA = 1f
private const val DISABLED_ALPHA = 0.38f
private const val OUTLINED_BUTTON_CORNER_DP = 13
private const val OUTLINED_BUTTON_PADDING_DP = 10
private const val BORDER_WIDTH_DP = 1
private const val PLAY_TEXT_GAP_DP = 5
private const val ICON_TEXT_GAP_DP = 8
private const val OUTLINED_ICON_SIZE_DP = 23
private const val PLAY_ICON_SIZE_DP = 15
private const val PLAY_LEFT_FRACTION = 0.28f
private const val PLAY_TOP_FRACTION = 0.16f
private const val PLAY_RIGHT_FRACTION = 0.82f
private const val PLAY_CENTER_FRACTION = 0.5f
private const val PLAY_BOTTOM_FRACTION = 0.84f
private const val COMPACT_ACTION_HEIGHT_DP = 31
private const val COMPACT_ACTION_PADDING_DP = 11
private const val COMPACT_ICON_SIZE_DP = 16
private const val COMPACT_TEXT_PADDING_DP = 4
private const val SQUARE_BUTTON_CORNER_DP = 11
private const val SQUARE_BUTTON_SIZE_DP = 44
private const val SQUARE_ICON_SIZE_DP = 22
private const val CLOCK_SIZE_DP = 24
private const val CLOCK_STROKE_DP = 2
private const val CLOCK_MINUTE_HAND_DP = 6
private const val CLOCK_HOUR_HAND_DP = 5
private const val INFORMATION_HORIZONTAL_PADDING_DP = 17
private const val INFORMATION_VERTICAL_PADDING_DP = 3
private const val INFORMATION_ICON_SIZE_DP = 18
private const val INFORMATION_TEXT_PADDING_DP = 7
private const val DEFAULT_BUTTON_HEIGHT_DP = 53

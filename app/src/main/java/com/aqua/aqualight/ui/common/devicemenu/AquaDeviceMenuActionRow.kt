package com.aqua.aqualight.ui.common.devicemenu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/** Shared device-menu row with a compact, explicit trailing action. */
@Composable
fun AquaDeviceMenuActionRow(
    content: AquaDeviceMenuRowContent,
    action: AquaDeviceMenuRowAction,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    val iconTint = when (content.tone) {
        AquaDeviceMenuTone.ACCENT -> colors.accent
        AquaDeviceMenuTone.NEUTRAL -> colors.textSecondary
        AquaDeviceMenuTone.DANGER -> colors.danger
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AquaDeviceMenuGeometry.rowMinHeight)
            .then(
                onClick?.let { callback ->
                    Modifier.clickable(role = Role.Button, onClick = callback)
                } ?: Modifier
            )
            .padding(
                horizontal = AquaDeviceMenuGeometry.rowHorizontalPadding,
                vertical = AquaDeviceMenuGeometry.rowVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AquaDeviceMenuRowLayout.LeadingIcon(
            iconRes = content.iconRes,
            tint = iconTint,
            surfaceColor = colors.surfaceRaised
        )
        AquaDeviceMenuRowLayout.Text(
            content = content,
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        AquaDeviceMenuInlineAction(
            text = action.text,
            enabled = action.enabled,
            onClick = action.onClick
        )
    }
}

@Composable
private fun AquaDeviceMenuInlineAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    val shape = RoundedCornerShape(AquaDeviceMenuGeometry.inlineActionRadius)
    val actionColor = if (enabled) colors.accent else colors.textSecondary

    Box(
        modifier = Modifier
            .widthIn(max = AquaDeviceMenuGeometry.inlineActionMaxWidth)
            .defaultMinSize(minHeight = AquaDeviceMenuGeometry.inlineActionMinHeight)
            .clip(shape)
            .background(actionColor.copy(alpha = INLINE_ACTION_BACKGROUND_ALPHA))
            .border(
                width = AquaDeviceMenuGeometry.surfaceOutlineWidth,
                color = if (enabled) colors.accent else colors.outline,
                shape = shape
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(
                horizontal = AquaDeviceMenuGeometry.inlineActionHorizontalPadding,
                vertical = AquaDeviceMenuGeometry.inlineActionVerticalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = typography.rowTitle.copy(
                color = actionColor,
                fontSize = AquaDeviceMenuTypographyScale.inlineActionFontSize,
                lineHeight = AquaDeviceMenuTypographyScale.inlineActionLineHeight,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val INLINE_ACTION_BACKGROUND_ALPHA = 0.12f

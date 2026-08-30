package com.aqua.aqualight.ui.common.devicemenu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R

/** Shared radio-style row for mutually exclusive device-setting destinations. */
@Composable
fun AquaDeviceMenuSelectionRow(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    showTrailingIcon: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    val interactionModifier = onClick?.let { callback ->
        Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = callback
        )
    } ?: Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AquaDeviceMenuGeometry.selectionRowMinHeight)
            .then(interactionModifier)
            .padding(
                horizontal = AquaDeviceMenuGeometry.sectionContentPadding,
                vertical = AquaDeviceMenuGeometry.selectionRowVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AquaDeviceMenuSelectionIndicator(selected = selected)
        BasicText(
            text = text,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AquaDeviceMenuGeometry.selectionContentGap),
            style = typography.rowTitle.copy(
                color = if (selected) colors.accent else colors.textPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showTrailingIcon) {
            Image(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                modifier = Modifier.size(AquaDeviceMenuGeometry.trailingIconSize),
                colorFilter = ColorFilter.tint(colors.textSecondary)
            )
        }
    }
}

@Composable
private fun AquaDeviceMenuSelectionIndicator(selected: Boolean) {
    val colors = aquaDeviceMenuColors()
    Box(
        modifier = Modifier
            .size(AquaDeviceMenuGeometry.selectionIndicatorSize)
            .clip(CircleShape)
            .border(
                width = AquaDeviceMenuGeometry.surfaceOutlineWidth,
                color = if (selected) colors.accent else colors.textSecondary,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(AquaDeviceMenuGeometry.selectionIndicatorDotSize)
                    .clip(CircleShape)
                    .background(colors.accent)
            )
        }
    }
}

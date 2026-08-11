package com.aqua.aqualight.ui.common.devicemenu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R

/** Shared row for a prominent editable value such as time, duration or target level. */
@Composable
fun AquaDeviceMenuEditableValueRow(
    label: String,
    value: String,
    description: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AquaDeviceMenuGeometry.rowMinHeight)
            .semantics(mergeDescendants = true) {}
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(
                horizontal = AquaDeviceMenuGeometry.rowHorizontalPadding,
                vertical = AquaDeviceMenuGeometry.rowVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AquaDeviceMenuRowLayout.LeadingIcon(
            iconRes = iconRes,
            tint = if (enabled) colors.accent else colors.textSecondary,
            surfaceColor = colors.surfaceRaised
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AquaDeviceMenuGeometry.rowContentGap)
        ) {
            BasicText(text = label, style = typography.rowTitle)
            BasicText(
                text = description,
                modifier = Modifier.padding(top = AquaDeviceMenuGeometry.rowTextGap),
                style = typography.rowBody,
                maxLines = VALUE_DESCRIPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
        BasicText(
            text = value,
            modifier = Modifier.padding(end = AquaDeviceMenuGeometry.compactGap),
            style = typography.heroTitle.copy(
                color = if (enabled) colors.accent else colors.textSecondary,
                fontSize = AquaDeviceMenuTypographyScale.editableValueFontSize,
                lineHeight = AquaDeviceMenuTypographyScale.editableValueLineHeight,
                textAlign = TextAlign.End
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Image(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(AquaDeviceMenuGeometry.trailingIconSize),
            colorFilter = ColorFilter.tint(colors.textSecondary)
        )
    }
}

private const val VALUE_DESCRIPTION_MAX_LINES = 2

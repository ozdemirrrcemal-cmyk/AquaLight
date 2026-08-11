package com.aqua.aqualight.ui.common.devicemenu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/** Shared labeled surface used by device setting and editor screens. */
@Composable
fun AquaDeviceMenuSection(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) ENABLED_ALPHA else DISABLED_ALPHA)
    ) {
        BasicText(
            text = title,
            modifier = Modifier.padding(
                start = AquaDeviceMenuGeometry.rowHorizontalPadding,
                bottom = AquaDeviceMenuGeometry.sectionLabelBottomSpacing
            ),
            style = typography.sectionLabel
        )
        AquaDeviceMenuSectionSurface(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

private const val ENABLED_ALPHA = 1f
private const val DISABLED_ALPHA = 0.42f

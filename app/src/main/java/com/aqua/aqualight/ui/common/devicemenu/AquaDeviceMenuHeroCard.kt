package com.aqua.aqualight.ui.common.devicemenu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

/** Shared introductory card for focused device-menu destinations. */
@Composable
fun AquaDeviceMenuHeroCard(
    eyebrow: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)

    AquaDeviceMenuSectionSurface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(AquaDeviceMenuGeometry.heroPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(AquaDeviceMenuGeometry.heroAccentWidth)
                    .height(AquaDeviceMenuGeometry.heroAccentHeight)
                    .clip(RoundedCornerShape(AquaDeviceMenuGeometry.heroAccentRadius))
                    .background(colors.accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AquaDeviceMenuGeometry.heroContentGap),
                verticalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.rowTextGap)
            ) {
                BasicText(text = eyebrow, style = typography.eyebrow)
                BasicText(text = title, style = typography.heroTitle)
                BasicText(text = description, style = typography.heroBody)
            }
        }
    }
}

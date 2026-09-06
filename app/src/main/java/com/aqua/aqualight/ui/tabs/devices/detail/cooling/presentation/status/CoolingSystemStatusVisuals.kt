package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

internal data class CoolingSystemStatusVisuals(
    val colors: AquaDeviceCardColors,
    val typography: AquaDeviceCardTypography
)

@Composable
internal fun CoolingSystemStatusSection(
    title: String,
    visuals: CoolingSystemStatusVisuals,
    modifier: Modifier = Modifier,
    tone: CoolingSystemStatusTone? = null,
    content: @Composable () -> Unit
) {
    AquaDeviceCardSurface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.contentGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
            ) {
                tone?.let { CoolingSystemStatusDot(it, visuals.colors) }
                BasicText(
                    text = title,
                    style = visuals.typography.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            content()
        }
    }
}

@Composable
internal fun CoolingSystemStatusDetailRow(
    label: String,
    value: String,
    visuals: CoolingSystemStatusVisuals,
    tone: CoolingSystemStatusTone = CoolingSystemStatusTone.NEUTRAL
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.contentGap)
    ) {
        BasicText(
            text = label,
            style = visuals.typography.caption.copy(color = visuals.colors.secondaryText),
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = value,
            style = visuals.typography.body.copy(color = tone.color(visuals.colors)),
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun CoolingSystemStatusParagraph(
    text: String,
    visuals: CoolingSystemStatusVisuals,
    tone: CoolingSystemStatusTone = CoolingSystemStatusTone.NEUTRAL
) {
    BasicText(
        text = text,
        style = visuals.typography.caption.copy(color = tone.color(visuals.colors)),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun CoolingSystemStatusDivider(visuals: CoolingSystemStatusVisuals) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingDashboardGeometry.powerDividerHeight)
            .background(
                visuals.colors.outline.copy(alpha = AquaCoolingDashboardAlpha.divider)
            )
    )
}

@Composable
private fun CoolingSystemStatusDot(
    tone: CoolingSystemStatusTone,
    colors: AquaDeviceCardColors
) {
    Box(
        modifier = Modifier
            .size(AquaCoolingDashboardGeometry.liveHeroStatusDotSize)
            .clip(CircleShape)
            .background(tone.color(colors).copy(alpha = AquaCoolingDashboardAlpha.statusDot))
    )
}

private fun CoolingSystemStatusTone.color(colors: AquaDeviceCardColors): Color = when (this) {
    CoolingSystemStatusTone.SUCCESS -> colors.success
    CoolingSystemStatusTone.WARNING -> colors.warning
    CoolingSystemStatusTone.DANGER -> colors.danger
    CoolingSystemStatusTone.NEUTRAL -> colors.secondaryText
}

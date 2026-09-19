package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightPlanChartSpec

@Composable
internal fun LightChannelTrack(
    percent: Int,
    fill: Color,
    rail: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(AquaLightDashboardGeometry.liveOutputTrackHeight)
            .clip(AquaLightDashboardGeometry.liveOutputTrackShape)
            .background(
                rail.copy(alpha = AquaLightDashboardAlpha.liveOutputRail)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(
                    percent.toFloat() / AquaLightPlanChartSpec.maximumPercent.toFloat()
                )
                .clip(AquaLightDashboardGeometry.liveOutputTrackShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            fill.copy(alpha = AquaLightDashboardAlpha.liveOutputFillStart),
                            fill
                        )
                    )
                )
        )
    }
}

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aqua.aqualight.ui.common.light.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.common.light.AquaLightDashboardIcon
import com.aqua.aqualight.ui.common.light.AquaLightDashboardIconKind

@Composable
internal fun DeviceLightChevron(tint: Color) {
    AquaLightDashboardIcon(
        kind = AquaLightDashboardIconKind.CHEVRON,
        tint = tint,
        modifier = Modifier.size(AquaLightDashboardGeometry.controlsChevronSize),
        strokeWidth = AquaLightDashboardGeometry.controlsChevronStrokeWidth
    )
}

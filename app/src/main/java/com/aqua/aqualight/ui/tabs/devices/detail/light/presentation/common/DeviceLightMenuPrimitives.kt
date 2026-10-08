package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun DeviceLightChevron(tint: Color) {
    AquaLightDashboardIcon(
        kind = AquaLightDashboardIconKind.CHEVRON,
        tint = tint,
        modifier = Modifier.size(AquaLightDashboardGeometry.controlsChevronSize),
        strokeWidth = AquaLightDashboardGeometry.controlsChevronStrokeWidth
    )
}

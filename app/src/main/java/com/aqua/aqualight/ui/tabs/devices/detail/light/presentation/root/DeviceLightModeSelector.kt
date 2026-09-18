package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightDashboardColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightDashboardTypography

@Composable
internal fun DeviceLightModeSelector(
    selectedMode: DeviceLightControlMode?,
    enabled: Boolean,
    onModeSelected: (DeviceLightControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else AquaLightDashboardAlpha.disabledControl),
        verticalArrangement = Arrangement.spacedBy(
            AquaLightDashboardGeometry.modeSelectorTitleBottomGap
        )
    ) {
        BasicText(
            text = stringResource(R.string.device_light_mode_selector_title),
            style = typography.title.copy(color = colors.primaryText)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AquaLightDashboardGeometry.modeSelectorHeight)
                .clip(AquaLightDashboardGeometry.modeSelectorShape)
                .background(colors.mediaSurface)
                .border(
                    width = AquaLightDashboardGeometry.modeSelectorOutlineWidth,
                    color = colors.outline,
                    shape = AquaLightDashboardGeometry.modeSelectorShape
                )
                .padding(AquaLightDashboardGeometry.modeSelectorOuterPadding),
            horizontalArrangement = Arrangement.spacedBy(
                AquaLightDashboardGeometry.modeSelectorSegmentGap
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            deviceLightModeDisplayOrder.forEach { mode ->
                val selected = mode == selectedMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(AquaLightDashboardGeometry.modeSelectorSegmentHeight)
                        .clip(AquaLightDashboardGeometry.modeSelectorSegmentShape)
                        .clickable(
                            enabled = enabled && !selected,
                            onClick = { onModeSelected(mode) }
                        )
                        .background(
                            if (selected) colors.accent else colors.mediaSurface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = stringResource(mode.labelRes()),
                        style = typography.body.copy(
                            color = if (selected) {
                                colors.primaryText
                            } else {
                                colors.secondaryText
                            }
                        )
                    )
                }
            }
        }
    }
}

private val deviceLightModeDisplayOrder = listOf(
    DeviceLightControlMode.CUSTOM,
    DeviceLightControlMode.MANUAL,
    DeviceLightControlMode.AUTOMATIC
)

@StringRes
private fun DeviceLightControlMode.labelRes(): Int = when (this) {
        DeviceLightControlMode.CUSTOM -> R.string.device_light_mode_selector_custom
        DeviceLightControlMode.MANUAL -> R.string.device_light_mode_selector_manual
        DeviceLightControlMode.AUTOMATIC -> R.string.device_light_mode_selector_automatic
    }

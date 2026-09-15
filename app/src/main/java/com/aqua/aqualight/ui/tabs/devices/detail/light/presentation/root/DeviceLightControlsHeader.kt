package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.light.AquaLightDashboardAlpha
import com.aqua.aqualight.ui.common.light.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.common.light.aquaLightDashboardColors
import com.aqua.aqualight.ui.common.light.aquaLightDashboardTypography

@Composable
internal fun DeviceLightControlsHeader(
    enabled: Boolean,
    onQuickSetupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AquaLightDashboardGeometry.controlsHeaderHeight)
            .padding(horizontal = AquaLightDashboardGeometry.controlsHeaderHorizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = stringResource(R.string.device_light_controls_title),
            style = typography.title.copy(color = colors.primaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier
                .height(AquaLightDashboardGeometry.quickSetupButtonHeight)
                .alpha(if (enabled) 1f else AquaLightDashboardAlpha.disabledControl)
                .clip(AquaLightDashboardGeometry.quickSetupShape)
                .border(
                    width = AquaLightDashboardGeometry.quickSetupOutlineWidth,
                    color = colors.accent,
                    shape = AquaLightDashboardGeometry.quickSetupShape
                )
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onQuickSetupClick
                )
                .padding(
                    horizontal = AquaLightDashboardGeometry.quickSetupButtonHorizontalPadding
                ),
            horizontalArrangement = Arrangement.spacedBy(
                AquaLightDashboardGeometry.quickSetupButtonContentGap
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_light_quick_setup),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.accent),
                modifier = Modifier.size(AquaLightDashboardGeometry.quickSetupIconSize)
            )
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_button),
                style = typography.caption.copy(color = colors.accent),
                maxLines = 1
            )
        }
    }
}

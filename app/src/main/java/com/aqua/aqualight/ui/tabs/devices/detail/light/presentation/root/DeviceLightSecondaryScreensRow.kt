package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightControlsPreviewSpec
import com.aqua.aqualight.ui.common.light.AquaLightDashboardAlpha
import com.aqua.aqualight.ui.common.light.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.common.light.AquaLightDashboardIcon
import com.aqua.aqualight.ui.common.light.AquaLightDashboardIconKind
import com.aqua.aqualight.ui.common.light.aquaLightDashboardColors
import com.aqua.aqualight.ui.common.light.aquaLightDashboardTypography

@Composable
internal fun DeviceLightSecondaryScreensRow(
    enabled: Boolean,
    onMenuClick: (DeviceLightMenuDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.secondaryCardGap)
    ) {
        DeviceLightSecondaryCard(
            icon = AquaLightDashboardIconKind.ADAPTATION,
            enabled = enabled,
            onClick = { onMenuClick(DeviceLightMenuDestination.ADAPTATION) },
            modifier = Modifier.weight(AquaLightDashboardGeometry.adaptationCardWeight)
        ) { colors, typography ->
            DeviceLightAdaptationContent(colors, typography)
        }
        DeviceLightSecondaryCard(
            icon = AquaLightDashboardIconKind.SYSTEM,
            enabled = enabled,
            onClick = { onMenuClick(DeviceLightMenuDestination.SYSTEM) },
            modifier = Modifier.weight(AquaLightDashboardGeometry.systemCardWeight)
        ) { colors, typography ->
            DeviceLightSystemContent(colors, typography)
        }
    }
}

@Composable
private fun RowScope.DeviceLightSecondaryCard(
    icon: AquaLightDashboardIconKind,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(AquaDeviceCardColors, AquaDeviceCardTypography) -> Unit
) {
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)
    AquaDeviceCardSurface(
        modifier = modifier
            .height(AquaLightDashboardGeometry.secondaryCardMinimumHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AquaLightDashboardIcon(
                kind = icon,
                tint = colors.accent,
                modifier = Modifier.size(AquaLightDashboardGeometry.secondaryIconSize)
            )
            content(colors, typography)
            DeviceLightChevron(colors.secondaryText)
        }
    }
}

@Composable
private fun RowScope.DeviceLightAdaptationContent(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier
            .padding(start = AquaLightDashboardGeometry.secondaryIconGap)
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.secondaryTitleGap)
    ) {
        DeviceLightSecondaryTitle(R.string.device_light_adaptation_title, colors, typography)
        if (AquaLightControlsPreviewSpec.adaptationActive) {
            BasicText(
                text = stringResource(
                    R.string.device_light_adaptation_percent,
                    AquaLightControlsPreviewSpec.adaptationPercent
                ),
                style = typography.body.copy(color = colors.primaryText),
                maxLines = 1
            )
            BasicText(
                text = pluralStringResource(
                    R.plurals.device_light_adaptation_days_remaining,
                    AquaLightControlsPreviewSpec.adaptationDaysRemaining,
                    AquaLightControlsPreviewSpec.adaptationDaysRemaining
                ),
                style = typography.micro.copy(color = colors.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            DeviceLightAdaptationProgress(
                percent = AquaLightControlsPreviewSpec.adaptationPercent,
                colors = colors
            )
        } else {
            BasicText(
                text = stringResource(R.string.device_light_adaptation_off),
                style = typography.body.copy(color = colors.secondaryText),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DeviceLightAdaptationProgress(
    percent: Int,
    colors: AquaDeviceCardColors
) {
    val progress = percent.coerceIn(0, AquaLightControlsPreviewSpec.fullPercent) /
        AquaLightControlsPreviewSpec.fullPercent.toFloat()
    Box(
        modifier = Modifier
            .padding(top = AquaLightDashboardGeometry.secondaryProgressTopGap)
            .fillMaxWidth()
            .height(AquaLightDashboardGeometry.secondaryProgressHeight)
            .clip(AquaLightDashboardGeometry.secondaryProgressShape)
            .background(colors.secondaryText.copy(alpha = AquaLightDashboardAlpha.liveOutputRail))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(AquaLightDashboardGeometry.secondaryProgressHeight)
                .background(colors.accent)
        )
    }
}

@Composable
private fun RowScope.DeviceLightSystemContent(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier
            .padding(start = AquaLightDashboardGeometry.secondaryIconGap)
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.secondaryTitleGap)
    ) {
        DeviceLightSecondaryTitle(R.string.device_light_system_title, colors, typography)
        BasicText(
            text = stringResource(
                R.string.device_light_system_temperature,
                AquaLightControlsPreviewSpec.systemTemperatureCelsius
            ),
            style = typography.body.copy(color = colors.primaryText),
            maxLines = 1
        )
        BasicText(
            text = stringResource(
                R.string.device_light_system_fans_summary,
                AquaLightControlsPreviewSpec.systemFanOnePercent,
                AquaLightControlsPreviewSpec.systemFanTwoPercent
            ),
            style = typography.micro.copy(color = colors.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        DeviceLightSystemStatus(
            status = stringResource(R.string.device_light_system_status_normal),
            colors = colors,
            typography = typography
        )
    }
}

@Composable
private fun DeviceLightSecondaryTitle(
    @StringRes titleRes: Int,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    BasicText(
        text = stringResource(titleRes),
        style = typography.title.copy(color = colors.primaryText),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DeviceLightSystemStatus(
    status: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.padding(top = AquaLightDashboardGeometry.systemStatusTopGap),
        horizontalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.systemStatusGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AquaLightDashboardGeometry.systemStatusDotSize)
                .clip(AquaLightDashboardGeometry.quickSetupShape)
                .background(colors.accent)
        )
        BasicText(
            text = status,
            style = typography.micro.copy(color = colors.accent),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightAdaptationSummary
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.DeviceLightChevron
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardIcon
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardIconKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightDashboardColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightDashboardTypography

@Composable
internal fun DeviceLightSecondaryScreensRow(
    enabled: Boolean,
    adaptation: DeviceLightAdaptationSummary,
    systemSupported: Boolean,
    onMenuClick: (DeviceLightMenuDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.secondaryCardGap)
    ) {
        if (adaptation.supported) {
            DeviceLightSecondaryCard(
                icon = AquaLightDashboardIconKind.ADAPTATION,
                enabled = enabled,
                onClick = { onMenuClick(DeviceLightMenuDestination.ADAPTATION) },
                modifier = Modifier.weight(AquaLightDashboardGeometry.adaptationCardWeight)
            ) { colors, typography ->
                DeviceLightAdaptationContent(adaptation, colors, typography)
            }
        }
        if (systemSupported) {
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
    adaptation: DeviceLightAdaptationSummary,
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
        val percent = adaptation.displayPercent()
        if (adaptation.state != DeviceLightAdaptationState.DISABLED && percent != null) {
            BasicText(
                text = stringResource(
                    R.string.device_light_adaptation_percent,
                    percent
                ),
                style = typography.body.copy(color = colors.primaryText),
                maxLines = 1
            )
            adaptation.remainingDays()?.let { days ->
                BasicText(
                    text = pluralStringResource(
                        R.plurals.device_light_adaptation_days_remaining,
                        days,
                        days
                    ),
                    style = typography.micro.copy(color = colors.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DeviceLightAdaptationProgress(
                percent = percent,
                colors = colors
            )
        } else if (adaptation.state == DeviceLightAdaptationState.ACTIVE) {
            BasicText(
                text = stringResource(R.string.device_light_active_uppercase),
                style = typography.body.copy(color = colors.accent),
                maxLines = 1
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

private fun DeviceLightAdaptationSummary.displayPercent(): Int? = when (state) {
    DeviceLightAdaptationState.COMPLETED -> FULL_PERCENT
    DeviceLightAdaptationState.ACTIVE -> currentPermille?.div(PERMILLE_PER_PERCENT)
    DeviceLightAdaptationState.DISABLED,
    null -> null
}

private fun DeviceLightAdaptationSummary.remainingDays(): Int? = remainingSeconds?.let { seconds ->
    ((seconds + SECONDS_PER_DAY - 1L) / SECONDS_PER_DAY).toInt()
}

@Composable
private fun DeviceLightAdaptationProgress(
    percent: Int,
    colors: AquaDeviceCardColors
) {
    val progress = percent.coerceIn(0, FULL_PERCENT) / FULL_PERCENT.toFloat()
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
            text = stringResource(R.string.device_light_system_open_details),
            style = typography.body.copy(color = colors.secondaryText),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
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

private const val FULL_PERCENT = 100
private const val PERMILLE_PER_PERCENT = 10
private const val SECONDS_PER_DAY = 86_400L

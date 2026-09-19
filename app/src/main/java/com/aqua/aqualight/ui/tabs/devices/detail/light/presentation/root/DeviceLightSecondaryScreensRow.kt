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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightAdaptationSummary
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightSystemSummary
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemCondition
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
    state: DeviceLightSecondaryScreensState,
    onMenuClick: (DeviceLightMenuDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.secondaryCardGap)
    ) {
        if (state.adaptation.supported) {
            DeviceLightSecondaryCard(
                icon = AquaLightDashboardIconKind.ADAPTATION,
                enabled = state.enabled,
                onClick = { onMenuClick(DeviceLightMenuDestination.ADAPTATION) },
                modifier = Modifier.weight(AquaLightDashboardGeometry.adaptationCardWeight)
            ) { colors, typography ->
                DeviceLightAdaptationContent(state.adaptation, colors, typography)
            }
        }
        if (state.systemSupported) {
            DeviceLightSecondaryCard(
                icon = AquaLightDashboardIconKind.SYSTEM,
                enabled = state.enabled,
                onClick = { onMenuClick(DeviceLightMenuDestination.SYSTEM) },
                modifier = Modifier.weight(AquaLightDashboardGeometry.systemCardWeight)
            ) { colors, typography ->
                DeviceLightSystemContent(state.system, colors, typography)
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
            adaptation.remainingSeconds?.let { seconds ->
                val days = ((seconds + SECONDS_PER_DAY - 1L) / SECONDS_PER_DAY).toInt()
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
    system: DeviceLightSystemSummary?,
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
        if (system == null) {
            BasicText(
                text = stringResource(R.string.device_light_system_open_details),
                style = typography.body.copy(color = colors.secondaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            BasicText(
                text = system.temperatureCelsius?.let { temperature ->
                    stringResource(R.string.device_light_system_temperature, temperature)
                } ?: stringResource(R.string.device_light_system_temperature_unavailable),
                style = typography.body.copy(color = colors.primaryText),
                maxLines = 1
            )
            if (system.fanPercents.size == SYSTEM_FAN_COUNT) {
                BasicText(
                    text = stringResource(
                        R.string.device_light_system_fans_summary,
                        system.fanPercents[FIRST_FAN_INDEX],
                        system.fanPercents[SECOND_FAN_INDEX]
                    ),
                    style = typography.micro.copy(color = colors.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DeviceLightSystemStatus(system.condition, colors, typography)
        }
    }
}

@Composable
private fun DeviceLightSystemStatus(
    condition: DeviceLightSystemCondition,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val statusColor = condition.statusColor(colors)
    Row(
        modifier = Modifier.padding(top = AquaLightDashboardGeometry.systemStatusTopGap),
        horizontalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.systemStatusGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AquaLightDashboardGeometry.systemStatusDotSize)
                .clip(AquaLightDashboardGeometry.quickSetupShape)
                .background(statusColor)
        )
        BasicText(
            text = stringResource(condition.statusLabelRes()),
            style = typography.micro.copy(color = statusColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@StringRes
private fun DeviceLightSystemCondition.statusLabelRes(): Int = when (this) {
    DeviceLightSystemCondition.NORMAL -> R.string.device_light_system_status_normal
    DeviceLightSystemCondition.PROTECTION_ACTIVE ->
        R.string.device_light_system_condition_protection
    DeviceLightSystemCondition.SENSOR_FAIL_SAFE ->
        R.string.device_light_system_condition_sensor_fail_safe
    DeviceLightSystemCondition.FAN_FAULT -> R.string.device_light_system_condition_fan_fault
}

private fun DeviceLightSystemCondition.statusColor(colors: AquaDeviceCardColors): Color =
    when (this) {
        DeviceLightSystemCondition.NORMAL -> colors.accent
        DeviceLightSystemCondition.PROTECTION_ACTIVE -> colors.warning
        DeviceLightSystemCondition.SENSOR_FAIL_SAFE,
        DeviceLightSystemCondition.FAN_FAULT -> colors.danger
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
private const val SYSTEM_FAN_COUNT = 2
private const val FIRST_FAN_INDEX = 0
private const val SECOND_FAN_INDEX = 1

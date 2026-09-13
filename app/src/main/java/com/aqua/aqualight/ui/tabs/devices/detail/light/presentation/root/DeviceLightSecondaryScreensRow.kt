package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)
    val models = deviceLightSecondaryItems()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.secondaryCardGap)
    ) {
        DeviceLightSecondaryCard(
            model = models.first(),
            enabled = enabled,
            colors = colors,
            typography = typography,
            onClick = { onMenuClick(models.first().destination) },
            modifier = Modifier.weight(AquaLightDashboardGeometry.adaptationCardWeight)
        )
        DeviceLightSecondaryCard(
            model = models.last(),
            enabled = enabled,
            colors = colors,
            typography = typography,
            onClick = { onMenuClick(models.last().destination) },
            modifier = Modifier.weight(AquaLightDashboardGeometry.systemCardWeight)
        )
    }
}

@Composable
private fun deviceLightSecondaryItems(): List<DeviceLightSecondaryItem> = listOf(
    DeviceLightSecondaryItem(
        titleRes = R.string.device_light_adaptation_title,
        subtitle = pluralStringResource(
            R.plurals.device_light_adaptation_summary,
            AquaLightControlsPreviewSpec.adaptationDaysRemaining,
            AquaLightControlsPreviewSpec.adaptationDaysRemaining,
            AquaLightControlsPreviewSpec.adaptationPercent
        ),
        icon = AquaLightDashboardIconKind.ADAPTATION,
        destination = DeviceLightMenuDestination.ADAPTATION
    ),
    DeviceLightSecondaryItem(
        titleRes = R.string.device_light_system_title,
        subtitle = stringResource(
            R.string.device_light_system_summary,
            AquaLightControlsPreviewSpec.systemTemperatureCelsius,
            AquaLightControlsPreviewSpec.systemFanOnePercent,
            AquaLightControlsPreviewSpec.systemFanTwoPercent
        ),
        status = stringResource(R.string.device_light_system_normal_status),
        icon = AquaLightDashboardIconKind.SYSTEM,
        destination = DeviceLightMenuDestination.SYSTEM
    )
)

@Composable
private fun RowScope.DeviceLightSecondaryCard(
    model: DeviceLightSecondaryItem,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AquaDeviceCardSurface(
        modifier = modifier
            .heightIn(min = AquaLightDashboardGeometry.secondaryCardMinimumHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AquaLightDashboardIcon(
                kind = model.icon,
                tint = colors.accent,
                modifier = Modifier.size(AquaLightDashboardGeometry.secondaryIconSize)
            )
            DeviceLightSecondaryText(model, colors, typography)
            DeviceLightChevron(colors.secondaryText)
        }
    }
}

@Composable
private fun RowScope.DeviceLightSecondaryText(
    model: DeviceLightSecondaryItem,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier
            .padding(start = AquaLightDashboardGeometry.secondaryIconGap)
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.secondaryTitleGap)
    ) {
        BasicText(
            text = stringResource(model.titleRes),
            style = typography.title.copy(color = colors.primaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = model.subtitle,
            style = typography.micro.copy(color = colors.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        model.status?.let { status -> DeviceLightSystemStatus(status, colors, typography) }
    }
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

private data class DeviceLightSecondaryItem(
    @StringRes val titleRes: Int,
    val subtitle: String,
    val status: String? = null,
    val icon: AquaLightDashboardIconKind,
    val destination: DeviceLightMenuDestination
)

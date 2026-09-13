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
import androidx.compose.foundation.layout.height
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
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
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
internal fun DeviceLightControlScreensCard(
    enabled: Boolean,
    onMenuClick: (DeviceLightMenuDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)
    AquaDeviceCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AquaLightDashboardGeometry.controlsCardMinimumHeight),
        contentPadding = AquaDeviceCardGeometry.edgeToEdgeContentPadding
    ) {
        DeviceLightControlRows(
            items = deviceLightControlItems(),
            enabled = enabled,
            colors = colors,
            typography = typography,
            onMenuClick = onMenuClick
        )
    }
}

@Composable
private fun deviceLightControlItems(): List<DeviceLightControlItem> = listOf(
    DeviceLightControlItem(
        titleRes = R.string.device_menu_manual_control_title,
        subtitle = stringResource(R.string.device_light_manual_control_subtitle),
        icon = AquaLightDashboardIconKind.MANUAL,
        destination = DeviceLightMenuDestination.MANUAL_CONTROL
    ),
    DeviceLightControlItem(
        titleRes = R.string.device_light_automatic_programs_title,
        subtitle = pluralStringResource(
            R.plurals.device_light_program_count,
            AquaLightControlsPreviewSpec.programCount,
            AquaLightControlsPreviewSpec.programCount
        ),
        icon = AquaLightDashboardIconKind.PROGRAM,
        destination = DeviceLightMenuDestination.AUTOMATIC_PROGRAMS
    ),
    DeviceLightControlItem(
        titleRes = R.string.device_light_custom_curve_title,
        subtitle = pluralStringResource(
            R.plurals.device_light_custom_curve_summary,
            AquaLightControlsPreviewSpec.customCurvePointCount,
            AquaLightControlsPreviewSpec.customCurvePointCount
        ),
        icon = AquaLightDashboardIconKind.CURVE,
        destination = DeviceLightMenuDestination.CUSTOM_LIGHT_CURVE,
        active = true
    )
)

@Composable
private fun DeviceLightControlRows(
    items: List<DeviceLightControlItem>,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onMenuClick: (DeviceLightMenuDestination) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            if (index > 0) DeviceLightControlDivider(colors)
            DeviceLightControlRow(
                item = item,
                enabled = enabled,
                colors = colors,
                typography = typography,
                onClick = { onMenuClick(item.destination) }
            )
        }
    }
}

@Composable
private fun DeviceLightControlRow(
    item: DeviceLightControlItem,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightDashboardGeometry.controlsRowHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = AquaLightDashboardGeometry.controlsRowHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AquaLightDashboardIcon(
            kind = item.icon,
            tint = colors.accent,
            modifier = Modifier.size(AquaLightDashboardGeometry.controlsRowIconSize)
        )
        DeviceLightControlRowText(item, colors, typography)
        DeviceLightControlRowAction(item.active, colors, typography)
    }
}

@Composable
private fun RowScope.DeviceLightControlRowText(
    item: DeviceLightControlItem,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier
            .padding(start = AquaLightDashboardGeometry.controlsRowIconGap)
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.controlsRowTextGap)
    ) {
        BasicText(
            text = stringResource(item.titleRes),
            style = typography.title.copy(color = colors.primaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = item.subtitle,
            style = typography.caption.copy(color = colors.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DeviceLightControlRowAction(
    active: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.controlsTrailingGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active) DeviceLightActiveChip(colors, typography)
        DeviceLightChevron(colors.secondaryText)
    }
}

@Composable
private fun DeviceLightActiveChip(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Box(
        modifier = Modifier
            .clip(AquaLightDashboardGeometry.activeChipShape)
            .background(colors.accent.copy(alpha = AquaLightDashboardAlpha.activeChipBackground))
            .padding(
                horizontal = AquaLightDashboardGeometry.activeChipHorizontalPadding,
                vertical = AquaLightDashboardGeometry.activeChipVerticalPadding
            )
    ) {
        BasicText(
            text = stringResource(R.string.device_light_active_uppercase),
            style = typography.micro.copy(color = colors.accent),
            maxLines = 1
        )
    }
}

@Composable
private fun DeviceLightControlDivider(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightDashboardGeometry.controlsDividerHeight)
            .background(colors.outline.copy(alpha = AquaLightDashboardAlpha.controlsDivider))
    )
}

private data class DeviceLightControlItem(
    @StringRes val titleRes: Int,
    val subtitle: String,
    val icon: AquaLightDashboardIconKind,
    val destination: DeviceLightMenuDestination,
    val active: Boolean = false
)

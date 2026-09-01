package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIcon
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIconKind
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardPalette
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.root.CoolingControlMode
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.root.DeviceCoolingRootUiState

@Composable
internal fun CoolingModeSettingsCard(
    state: DeviceCoolingRootUiState,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    actions: DeviceCoolingDashboardActions
) {
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.modeSettingsCardMinimumHeight)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CoolingModeSettingsHeader(colors = colors, typography = typography)
            CoolingModeSettingsRows(
                models = coolingModeSettingsRowModels(state, actions),
                enabled = enabled,
                supportedModes = state.supportedModes,
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun CoolingModeSettingsHeader(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    BasicText(
        text = stringResource(R.string.device_cooling_mode_settings_title),
        style = typography.title
    )
    BasicText(
        text = stringResource(R.string.device_cooling_mode_settings_description),
        style = typography.caption.copy(color = colors.secondaryText),
        modifier = Modifier.padding(top = AquaCoolingDashboardGeometry.modeSettingsHeaderGap)
    )
}

@Composable
private fun CoolingModeSettingsRows(
    models: List<CoolingModeSettingsRowModel>,
    enabled: Boolean,
    supportedModes: Set<CoolingControlMode>,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier.padding(
            top = AquaCoolingDashboardGeometry.modeSettingsContentTopPadding
        )
    ) {
        models.forEachIndexed { index, model ->
            if (index > 0) CoolingModeSettingsDivider(colors)
            CoolingModeSettingsRow(
                model = model,
                enabled = enabled && model.mode in supportedModes,
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun CoolingModeSettingsRow(
    model: CoolingModeSettingsRowModel,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.modeSettingsRowHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = model.onClick)
            .semantics { contentDescription = model.contentDescription },
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoolingModeSettingsRowIcon(model = model, colors = colors)
        CoolingModeSettingsRowText(model = model, colors = colors, typography = typography)
        CoolingModeSettingsRowAction(model = model, colors = colors, typography = typography)
    }
}

@Composable
private fun CoolingModeSettingsRowIcon(
    model: CoolingModeSettingsRowModel,
    colors: AquaDeviceCardColors
) {
    Box(
        modifier = Modifier
            .size(AquaCoolingDashboardGeometry.modeSettingsRowIconContainerSize)
            .clip(CircleShape)
            .background(
                colors.accent.copy(alpha = AquaCoolingDashboardAlpha.iconContainerBackground)
            )
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = colors.accent.copy(alpha = AquaCoolingDashboardAlpha.iconContainerOutline),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        AquaCoolingDashboardIcon(
            kind = model.icon,
            tint = colors.accent,
            modifier = Modifier.size(AquaCoolingDashboardGeometry.modeSettingsRowIconSize)
        )
    }
}

@Composable
private fun RowScope.CoolingModeSettingsRowText(
    model: CoolingModeSettingsRowModel,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier
            .padding(start = AquaCoolingDashboardGeometry.modeSettingsRowIconGap)
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(
            AquaCoolingDashboardGeometry.modeSettingsRowValueGap
        )
    ) {
        BasicText(
            text = coolingModeLabel(model.mode),
            style = typography.body.copy(
                color = colors.primaryText,
                fontSize = AquaCoolingDashboardTypography.modeSettingsTitleSize
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = model.value,
            style = typography.caption.copy(
                color = colors.secondaryText,
                fontSize = AquaCoolingDashboardTypography.modeSettingsValueSize
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoolingModeSettingsRowAction(
    model: CoolingModeSettingsRowModel,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            AquaCoolingDashboardGeometry.modeSettingsTrailingGap
        )
    ) {
        if (model.selected) CoolingActiveModeChip(typography)
        AquaCoolingDashboardIcon(
            kind = AquaCoolingDashboardIconKind.CHEVRON,
            tint = colors.primaryText,
            modifier = Modifier.size(AquaCoolingDashboardGeometry.modeSettingsChevronSize),
            strokeWidth = AquaCoolingDashboardGeometry.modeSettingsChevronStrokeWidth
        )
    }
}

@Composable
private fun CoolingActiveModeChip(typography: AquaDeviceCardTypography) {
    Box(
        modifier = Modifier
            .clip(AquaCoolingDashboardGeometry.activeChipShape)
            .background(
                AquaCoolingDashboardPalette.success.copy(
                    alpha = AquaCoolingDashboardAlpha.activeChipBackground
                )
            )
            .padding(
                horizontal = AquaCoolingDashboardGeometry.activeChipHorizontalPadding,
                vertical = AquaCoolingDashboardGeometry.activeChipVerticalPadding
            )
    ) {
        BasicText(
            text = stringResource(R.string.device_cooling_mode_active_uppercase),
            style = typography.micro.copy(
                color = AquaCoolingDashboardPalette.success,
                fontSize = AquaCoolingDashboardTypography.activeChipSize
            )
        )
    }
}

@Composable
private fun CoolingModeSettingsDivider(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingDashboardGeometry.modeSettingsDividerHeight)
            .background(colors.outline.copy(alpha = AquaCoolingDashboardAlpha.divider))
    )
}

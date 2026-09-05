package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuToggle

private data class AutomaticScreenVisuals(
    val colors: AquaDeviceCardColors,
    val typography: AquaDeviceCardTypography
)

private data class AutomaticEditorRowContent(
    val title: String,
    val helper: String,
    val value: String
)

/**
 * Automatic Cooling editor.
 *
 * Connectivity is gated before this destination is entered. The screen therefore owns only the
 * automatic-control surface; missing firmware values are rendered as unavailable and writes remain
 * disabled until an authoritative editable snapshot arrives.
 */
@Composable
internal fun DeviceCoolingAutomaticSettingsScreen(
    state: DeviceCoolingAutomaticSettingsUiState,
    actions: DeviceCoolingAutomaticSettingsActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    val visuals = AutomaticScreenVisuals(colors = colors, typography = typography)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AquaCoolingAutomaticGeometry.screenHorizontalPadding,
            top = AquaCoolingAutomaticGeometry.screenTopPadding,
            end = AquaCoolingAutomaticGeometry.screenHorizontalPadding,
            bottom = AquaCoolingAutomaticGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.sectionGap)
    ) {
        item(key = "live") {
            AutomaticLiveStatusCard(state, colors, typography)
        }
        item(key = "range") {
            AutomaticTemperatureRangeCard(
                state = state,
                visuals = visuals,
                actions = actions
            )
        }
        item(key = "silent-mode") {
            AutomaticSilentModeCard(
                state = state,
                colors = colors,
                typography = typography,
                onCheckedChange = actions.onSilentModeChanged
            )
        }
        if (state.saveState == DeviceCoolingAutomaticSaveState.ERROR) {
            item(key = "save-error") {
                BasicText(
                    text = stringResource(R.string.device_cooling_automatic_save_failed),
                    style = typography.caption.copy(color = colors.danger),
                    modifier = Modifier.padding(
                        horizontal = AquaCoolingDashboardGeometry.cardHorizontalPadding
                    )
                )
            }
        }
        item(key = "save") {
            AutomaticSaveButton(
                state = state,
                colors = colors,
                typography = typography,
                onSave = actions.onSave
            )
        }
    }
}

@Composable
private fun AutomaticLiveStatusCard(
    state: DeviceCoolingAutomaticSettingsUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingAutomaticGeometry.liveCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.liveMetricGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_automatic_live_status_title),
                style = typography.title.copy(color = colors.primaryText)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.liveMetricGap)
            ) {
                AutomaticLiveMetric(
                    label = stringResource(R.string.device_cooling_automatic_water_temperature),
                    value = automaticTemperatureText(state.tankTemperatureC),
                    visuals = AutomaticScreenVisuals(colors, typography),
                    modifier = Modifier.weight(1f)
                )
                AutomaticLiveMetric(
                    label = stringResource(R.string.device_cooling_automatic_fan_output),
                    value = automaticFanPercentText(state.fanPercentNow),
                    visuals = AutomaticScreenVisuals(colors, typography),
                    modifier = Modifier.weight(1f)
                )
                AutomaticLiveMetric(
                    label = stringResource(R.string.device_cooling_automatic_status),
                    value = automaticRuntimeStatusText(state.operatingState),
                    visuals = AutomaticScreenVisuals(colors, typography),
                    valueColor = when (state.operatingState) {
                        DeviceCoolingOperatingState.COOLING,
                        DeviceCoolingOperatingState.MANUAL,
                        DeviceCoolingOperatingState.PROGRAM -> colors.success
                        DeviceCoolingOperatingState.FAULT -> colors.warning
                        DeviceCoolingOperatingState.IDLE,
                        null -> colors.secondaryText
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AutomaticLiveMetric(
    label: String,
    value: String,
    visuals: AutomaticScreenVisuals,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = visuals.colors.primaryText
) {
    val colors = visuals.colors
    val typography = visuals.typography
    Column(
        modifier = modifier.padding(vertical = AquaCoolingAutomaticGeometry.liveMetricVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.liveMetricGap / 2)
    ) {
        BasicText(
            text = label,
            style = typography.micro.copy(color = colors.secondaryText),
            maxLines = 1
        )
        BasicText(
            text = value,
            style = typography.body.copy(color = valueColor),
            maxLines = 1
        )
    }
}

@Composable
private fun AutomaticTemperatureRangeCard(
    state: DeviceCoolingAutomaticSettingsUiState,
    visuals: AutomaticScreenVisuals,
    actions: DeviceCoolingAutomaticSettingsActions
) {
    val colors = visuals.colors
    val typography = visuals.typography
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingAutomaticGeometry.rangeCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.editorRowGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_automatic_range_title),
                style = typography.title.copy(color = colors.primaryText)
            )
            AutomaticEditorRow(
                content = AutomaticEditorRowContent(
                    title = stringResource(R.string.device_cooling_fan_start_temperature),
                    helper = stringResource(R.string.device_cooling_automatic_start_helper),
                    value = automaticTemperatureText(state.draftStartTemperatureC)
                ),
                enabled = state.editable,
                visuals = visuals,
                onClick = actions.onStartTemperatureClick
            )
            AutomaticEditorRow(
                content = AutomaticEditorRowContent(
                    title = stringResource(R.string.device_cooling_max_speed_temperature),
                    helper = stringResource(R.string.device_cooling_automatic_max_helper),
                    value = automaticTemperatureText(state.draftMaximumSpeedTemperatureC)
                ),
                enabled = state.editable,
                visuals = visuals,
                onClick = actions.onMaximumTemperatureClick
            )
            AutomaticTemperatureRangeVisual(
                startTemperatureC = state.draftStartTemperatureC,
                maximumTemperatureC = state.draftMaximumSpeedTemperatureC,
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun AutomaticSilentModeCard(
    state: DeviceCoolingAutomaticSettingsUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onCheckedChange: (Boolean) -> Unit
) {
    val enabled = state.silentModeEditable
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingAutomaticGeometry.silentModeCardMinimumHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.draftSilentModeEnabled,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .alpha(if (enabled) 1f else 0.55f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                AquaCoolingAutomaticGeometry.silentModeContentGap
            )
        ) {
            AutomaticSilentModeCopy(
                state = state,
                colors = colors,
                typography = typography,
                modifier = Modifier.weight(1f)
            )
            AquaDeviceMenuToggle(
                checked = state.draftSilentModeEnabled,
                contentDescription = stringResource(
                    R.string.device_cooling_automatic_silent_mode_toggle_description
                ),
                activeColor = colors.accent
            )
        }
    }
}

@Composable
private fun AutomaticSilentModeCopy(
    state: DeviceCoolingAutomaticSettingsUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(
            AquaCoolingAutomaticGeometry.silentModeContentGap / 2
        )
    ) {
        BasicText(
            text = stringResource(R.string.device_cooling_automatic_silent_mode_title),
            style = typography.title.copy(color = colors.primaryText)
        )
        state.silentModeMaximumFanPercent?.let { maximumPercent ->
            BasicText(
                text = stringResource(
                    R.string.device_cooling_automatic_silent_mode_description,
                    maximumPercent
                ),
                style = typography.micro.copy(color = colors.secondaryText)
            )
        }
        if (!state.silentModeFirmwareBacked) {
            BasicText(
                text = stringResource(R.string.device_cooling_automatic_silent_mode_unavailable),
                style = typography.micro.copy(color = colors.warning)
            )
        }
    }
}

@Composable
private fun AutomaticEditorRow(
    content: AutomaticEditorRowContent,
    enabled: Boolean,
    visuals: AutomaticScreenVisuals,
    onClick: () -> Unit
) {
    val colors = visuals.colors
    val typography = visuals.typography
    val shape = AquaCoolingAutomaticGeometry.editorRowShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                colors.mediaSurface.copy(
                    alpha = AquaCoolingAutomaticAlpha.rowBackground
                )
            )
            .border(
                width = AquaCoolingDashboardGeometry.chartGridStrokeWidth,
                color = colors.mediaOutline.copy(
                    alpha = AquaCoolingAutomaticAlpha.rowOutline
                ),
                shape = shape
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                horizontal = AquaCoolingAutomaticGeometry.editorRowHorizontalPadding,
                vertical = AquaCoolingAutomaticGeometry.editorRowVerticalPadding
            )
            .alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.editorRowGap / 2)
        ) {
            BasicText(
                text = content.title,
                style = typography.body.copy(color = colors.primaryText),
                maxLines = 1
            )
            BasicText(
                text = content.helper,
                style = typography.micro.copy(color = colors.secondaryText),
                maxLines = 2
            )
        }
        BasicText(
            text = content.value,
            style = typography.body.copy(
                color = colors.primaryText,
                textAlign = TextAlign.End
            ),
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(AquaCoolingAutomaticGeometry.editorRowGap))
        AutomaticChevron(colors = colors)
    }
}

@Composable
private fun AutomaticChevron(colors: AquaDeviceCardColors) {
    Canvas(
        modifier = Modifier
            .width(AquaCoolingAutomaticGeometry.editorChevronWidth)
            .height(AquaCoolingAutomaticGeometry.editorChevronHeight)
    ) {
        val x = size.width * 0.38f
        val top = size.height * 0.28f
        val middle = size.height * 0.50f
        val bottom = size.height * 0.72f
        drawLine(
            color = colors.accent,
            start = Offset(x, top),
            end = Offset(size.width * 0.62f, middle),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = colors.accent,
            start = Offset(size.width * 0.62f, middle),
            end = Offset(x, bottom),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun AutomaticSaveButton(
    state: DeviceCoolingAutomaticSettingsUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onSave: () -> Unit
) {
    val enabled = state.canSave
    val alpha = if (enabled) {
        AquaCoolingAutomaticAlpha.saveEnabled
    } else {
        AquaCoolingAutomaticAlpha.saveDisabled
    }
    val label = when (state.saveState) {
        DeviceCoolingAutomaticSaveState.SAVING ->
            stringResource(R.string.device_cooling_automatic_saving)
        DeviceCoolingAutomaticSaveState.SAVED ->
            stringResource(R.string.device_cooling_automatic_saved)
        DeviceCoolingAutomaticSaveState.IDLE,
        DeviceCoolingAutomaticSaveState.ERROR ->
            stringResource(R.string.device_cooling_automatic_save)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingAutomaticGeometry.actionHeight)
            .clip(AquaCoolingAutomaticGeometry.actionShape)
            .background(colors.accent.copy(alpha = alpha))
            .clickable(enabled = enabled, role = Role.Button, onClick = onSave)
            .padding(horizontal = AquaCoolingAutomaticGeometry.actionHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = typography.body.copy(
                color = colors.primaryText,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}

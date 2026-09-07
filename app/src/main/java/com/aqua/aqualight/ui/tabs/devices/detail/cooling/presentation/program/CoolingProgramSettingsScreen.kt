package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramPolicy
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingFanPercentSlider
import com.aqua.aqualight.ui.common.cooling.AquaCoolingFanPercentSliderState
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

private data class ProgramSlotCardModel(
    val slot: DeviceCoolingProgramSlot,
    val selected: Boolean,
    val minimumFanPercent: Int,
    val maximumFanPercent: Int,
    val fanPercentStep: Int
)

private data class ProgramSlotCardActions(
    val onHeaderClick: () -> Unit,
    val onDeleteClick: () -> Unit,
    val onStartTimeClick: () -> Unit,
    val onEndTimeClick: () -> Unit,
    val onFanOnTemperatureClick: () -> Unit,
    val onTargetFanPercentChange: (Int) -> Unit
)

private data class ProgramScreenVisuals(
    val context: Context,
    val colors: AquaDeviceCardColors,
    val typography: AquaDeviceCardTypography
)

@Composable
internal fun DeviceCoolingProgramSettingsScreen(
    state: DeviceCoolingProgramSettingsUiState,
    actions: DeviceCoolingProgramSettingsActions,
    modifier: Modifier = Modifier
) {
    val policy = requireNotNull(state.policy) {
        "Cooling Program content requires an authoritative application policy."
    }
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    val context = LocalContext.current
    val nowMinutes = state.currentMinuteOfDay

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AquaCoolingProgramGeometry.screenHorizontalPadding,
            top = AquaCoolingProgramGeometry.screenTopPadding,
            end = AquaCoolingProgramGeometry.screenHorizontalPadding,
            bottom = AquaCoolingProgramGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingProgramGeometry.sectionGap)
    ) {
        item(key = "active") {
            ProgramActiveCard(
                activeSlot = state.authoritativeActiveSlot,
                context = context,
                colors = colors,
                typography = typography
            )
        }
        item(key = "timeline") {
            ProgramTimelineCard(
                slots = state.slots,
                nowMinutes = nowMinutes,
                colors = colors,
                typography = typography
            )
        }
        item(key = "slots-header") {
            ProgramSlotsHeader(
                canAddSlot = state.canAddSlot,
                colors = colors,
                typography = typography,
                onAddSlot = actions.onAddSlot
            )
        }
        programSlotItems(
            state = state,
            policy = policy,
            visuals = ProgramScreenVisuals(context, colors, typography),
            actions = actions
        )
    }
}

private fun LazyListScope.programSlotItems(
    state: DeviceCoolingProgramSettingsUiState,
    policy: CoolingProgramPolicy,
    visuals: ProgramScreenVisuals,
    actions: DeviceCoolingProgramSettingsActions
) {
    state.slotItems.forEachIndexed { slotIndex, slotItem ->
        item(key = slotItem.uiKey) {
            val slot = slotItem.slot
            ProgramSlotCard(
                model = ProgramSlotCardModel(
                    slot = slot,
                    selected = slotIndex == state.selectedSlotIndex,
                    minimumFanPercent = policy.minimumFanPercent,
                    maximumFanPercent = policy.maximumFanPercent,
                    fanPercentStep = policy.fanPercentStep
                ),
                visuals = visuals,
                actions = ProgramSlotCardActions(
                    onHeaderClick = { actions.onSlotClick(slotIndex) },
                    onDeleteClick = { actions.onDeleteSlot(slotIndex) },
                    onStartTimeClick = { actions.onStartTimeClick(slotIndex) },
                    onEndTimeClick = { actions.onEndTimeClick(slotIndex) },
                    onFanOnTemperatureClick = { actions.onFanOnTemperatureClick(slotIndex) },
                    onTargetFanPercentChange = { percent ->
                        actions.onTargetFanPercentChange(slotIndex, percent)
                    }
                )
            )
        }
    }
}

@Composable
private fun ProgramSlotCard(
    model: ProgramSlotCardModel,
    visuals: ProgramScreenVisuals,
    actions: ProgramSlotCardActions
) {
    val slot = model.slot
    val selected = model.selected
    val shape = RoundedCornerShape(AquaCoolingDashboardGeometry.cardCornerRadius)
    val selectedModifier = if (selected) {
        Modifier.border(
            width = AquaCoolingProgramGeometry.selectedSlotOutlineWidth,
            color = visuals.colors.accent.copy(
                alpha = AquaCoolingProgramAlpha.slotSelectedOutline
            ),
            shape = shape
        )
    } else {
        Modifier
    }

    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(selectedModifier)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingProgramGeometry.slotMetricGap)
        ) {
            ProgramSlotHeader(model = model, visuals = visuals, onClick = actions.onHeaderClick)
            BasicText(
                text = programSlotSummary(slot),
                style = visuals.typography.caption.copy(color = visuals.colors.secondaryText),
                maxLines = 1
            )
            if (selected) {
                ProgramExpandedSlotEditor(model = model, visuals = visuals, actions = actions)
            }
        }
    }
}

@Composable
private fun ProgramSlotHeader(
    model: ProgramSlotCardModel,
    visuals: ProgramScreenVisuals,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AquaCoolingProgramGeometry.slotHeaderShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AquaCoolingProgramGeometry.slotHeaderVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = formatProgramTimeRange(visuals.context, model.slot),
            style = visuals.typography.title.copy(
                color = if (model.selected) {
                    visuals.colors.accent
                } else {
                    visuals.colors.primaryText
                }
            ),
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        ProgramChevron(colors = visuals.colors, expanded = model.selected)
    }
}

@Composable
private fun ProgramExpandedSlotEditor(
    model: ProgramSlotCardModel,
    visuals: ProgramScreenVisuals,
    actions: ProgramSlotCardActions
) {
    val slot = model.slot
    Spacer(modifier = Modifier.height(AquaCoolingProgramGeometry.expandedSectionTopGap))
    ProgramDivider(visuals.colors)
    Spacer(modifier = Modifier.height(AquaCoolingProgramGeometry.expandedSectionTopGap))
    ProgramEditorHeader(
        colors = visuals.colors,
        typography = visuals.typography,
        onDeleteClick = actions.onDeleteClick
    )
    ProgramEditorRow(
        label = stringResource(R.string.device_cooling_program_start_time),
        value = formatProgramTime(visuals.context, slot.startMinutes),
        colors = visuals.colors,
        typography = visuals.typography,
        onClick = actions.onStartTimeClick
    )
    ProgramEditorRow(
        label = stringResource(R.string.device_cooling_program_end_time),
        value = formatProgramTime(visuals.context, slot.endMinutes),
        colors = visuals.colors,
        typography = visuals.typography,
        onClick = actions.onEndTimeClick
    )
    ProgramEditorRow(
        label = stringResource(R.string.device_cooling_program_fan_on_temperature),
        value = stringResource(
            R.string.device_cooling_temperature_value_format,
            slot.fanOnTemperatureC
        ),
        colors = visuals.colors,
        typography = visuals.typography,
        onClick = actions.onFanOnTemperatureClick
    )
    ProgramFanSpeedEditor(
        model = model,
        colors = visuals.colors,
        typography = visuals.typography,
        onValueChange = actions.onTargetFanPercentChange
    )
}

@Composable
private fun ProgramEditorHeader(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = stringResource(R.string.device_cooling_program_editor_title),
            style = typography.body.copy(color = colors.primaryText),
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        ProgramEditorActionsMenu(
            colors = colors,
            typography = typography,
            onDeleteClick = onDeleteClick
        )
    }
}

@Composable
private fun ProgramEditorActionsMenu(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val offsetY = with(LocalDensity.current) {
        AquaCoolingProgramGeometry.slotChevronHeight.roundToPx()
    }
    val description = stringResource(R.string.device_cooling_program_more_actions)
    Box {
        Box(
            modifier = Modifier
                .width(AquaCoolingProgramGeometry.slotChevronWidth)
                .height(AquaCoolingProgramGeometry.slotChevronHeight)
                .clip(AquaCoolingProgramGeometry.inlineActionShape)
                .clickable(role = Role.Button, onClick = { expanded = !expanded })
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = "⋮",
                style = typography.title.copy(color = colors.secondaryText),
                maxLines = 1
            )
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, offsetY),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                BasicText(
                    text = stringResource(R.string.device_cooling_program_delete_slot),
                    style = typography.body.copy(color = colors.danger),
                    modifier = Modifier
                        .clip(AquaCoolingProgramGeometry.editorRowShape)
                        .background(colors.surface)
                        .border(
                            width = AquaCoolingDashboardGeometry.chartGridStrokeWidth,
                            color = colors.outline,
                            shape = AquaCoolingProgramGeometry.editorRowShape
                        )
                        .clickable(role = Role.Button) {
                            expanded = false
                            onDeleteClick()
                        }
                        .padding(
                            horizontal = AquaCoolingProgramGeometry.editorRowHorizontalPadding,
                            vertical = AquaCoolingProgramGeometry.editorRowVerticalPadding
                        ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ProgramFanSpeedEditor(
    model: ProgramSlotCardModel,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onValueChange: (Int) -> Unit
) {
    val value = model.slot.targetFanPercent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AquaCoolingProgramGeometry.editorRowShape)
            .background(
                colors.mediaSurface.copy(
                    alpha = AquaCoolingProgramAlpha.editorRowBackground
                )
            )
            .border(
                width = AquaCoolingDashboardGeometry.chartGridStrokeWidth,
                color = colors.mediaOutline.copy(
                    alpha = AquaCoolingProgramAlpha.editorRowOutline
                ),
                shape = AquaCoolingProgramGeometry.editorRowShape
            )
            .padding(
                horizontal = AquaCoolingProgramGeometry.editorRowHorizontalPadding,
                vertical = AquaCoolingProgramGeometry.editorRowVerticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingProgramGeometry.editorRowGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_program_fan_speed),
                style = typography.body.copy(color = colors.primaryText),
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            BasicText(
                text = stringResource(R.string.device_cooling_percent_value_format, value),
                style = typography.body.copy(
                    color = colors.primaryText,
                    textAlign = TextAlign.End
                ),
                maxLines = 1
            )
        }
        AquaCoolingFanPercentSlider(
            state = AquaCoolingFanPercentSliderState(
                percent = value,
                enabled = true,
                stepPercent = model.fanPercentStep,
                minimumPercent = model.minimumFanPercent,
                maximumPercent = model.maximumFanPercent
            ),
            colors = colors,
            onValueChanged = onValueChange
        )
    }
}

@Composable
internal fun programSlotSummary(slot: DeviceCoolingProgramSlot): String = buildString {
    append(stringResource(R.string.device_cooling_program_fan_speed))
    append(" ")
    append(stringResource(R.string.device_cooling_percent_value_format, slot.targetFanPercent))
    append(" • ")
    append(stringResource(R.string.device_cooling_program_fan_on_temperature))
    append(" ")
    append(stringResource(R.string.device_cooling_temperature_value_format, slot.fanOnTemperatureC))
}

@Composable
private fun ProgramEditorRow(
    label: String,
    value: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onClick: () -> Unit
) {
    val shape = AquaCoolingProgramGeometry.editorRowShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                colors.mediaSurface.copy(
                    alpha = AquaCoolingProgramAlpha.editorRowBackground
                )
            )
            .border(
                width = AquaCoolingDashboardGeometry.chartGridStrokeWidth,
                color = colors.mediaOutline.copy(
                    alpha = AquaCoolingProgramAlpha.editorRowOutline
                ),
                shape = shape
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = AquaCoolingProgramGeometry.editorRowHorizontalPadding,
                vertical = AquaCoolingProgramGeometry.editorRowVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = typography.body.copy(color = colors.primaryText),
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        BasicText(
            text = value,
            style = typography.body.copy(
                color = colors.primaryText,
                textAlign = TextAlign.End
            ),
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(AquaCoolingProgramGeometry.editorRowGap))
        ProgramChevron(colors = colors, expanded = false)
    }
}

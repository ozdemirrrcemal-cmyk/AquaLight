package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.preset.DeviceLightBuiltInPreset
import com.aqua.aqualight.application.devices.light.preset.DeviceLightPresetId
import com.aqua.aqualight.application.devices.light.preset.DeviceLightPresetScene
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightManualColors
import com.aqua.aqualight.ui.common.light.aquaLightManualColors
import com.aqua.aqualight.ui.common.light.labelResource

@Composable
internal fun DeviceLightAutomaticPresetScreen(
    state: DeviceLightAutomaticPresetUiState,
    actions: DeviceLightAutomaticPresetActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightManualColors()
    val typography = aquaDeviceCardTypography(colors.card)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color))
    ) {
        PresetIntroduction(colors, typography)
        Spacer(Modifier.height(DeviceLightAutomaticPresetGeometry.sectionGap))
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMN_COUNT),
            modifier = Modifier
                .weight(GRID_WEIGHT)
                .selectableGroup()
                .padding(horizontal = DeviceLightAutomaticPresetGeometry.screenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(DeviceLightAutomaticPresetGeometry.gridGap),
            verticalArrangement = Arrangement.spacedBy(DeviceLightAutomaticPresetGeometry.gridGap)
        ) {
            items(state.presets, key = { preset -> preset.id.name }) { preset ->
                AutomaticPresetCard(
                    preset = preset,
                    selected = preset.id == state.selectedPresetId,
                    onClick = { actions.onPresetClick(preset.id) },
                    colors = colors,
                    typography = typography
                )
            }
        }
        PresetActions(
            state = state,
            actions = actions,
            colors = colors,
            typography = typography
        )
    }
}

@Composable
private fun PresetIntroduction(
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = DeviceLightAutomaticPresetGeometry.screenHorizontalPadding,
                top = DeviceLightAutomaticPresetGeometry.screenTopPadding,
                end = DeviceLightAutomaticPresetGeometry.screenHorizontalPadding
            ),
        contentPadding = DeviceLightAutomaticPresetGeometry.introductionPadding
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Image(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.card.secondaryText),
                modifier = Modifier.size(DeviceLightAutomaticPresetGeometry.introductionIconSize)
            )
            Spacer(Modifier.width(DeviceLightAutomaticPresetGeometry.introductionIconGap))
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    DeviceLightAutomaticPresetGeometry.introductionTextGap
                )
            ) {
                BasicText(
                    text = stringResource(R.string.device_light_auto_preset_intro_title),
                    style = typography.title
                )
                BasicText(
                    text = stringResource(R.string.device_light_auto_preset_intro_summary),
                    style = typography.caption
                )
                BasicText(
                    text = stringResource(R.string.device_light_auto_preset_intro_safety),
                    style = typography.caption.copy(color = colors.card.secondaryText)
                )
            }
        }
    }
}

@Composable
private fun AutomaticPresetCard(
    preset: DeviceLightBuiltInPreset,
    selected: Boolean,
    onClick: () -> Unit,
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography
) {
    val label = stringResource(preset.id.labelResource())
    val summary = stringResource(preset.id.summaryResource())
    val description = stringResource(
        R.string.device_light_auto_preset_card_description,
        label,
        preset.automaticSchedule.durationMinutes / MINUTES_PER_HOUR,
        preset.automaticSchedule.rampMinutes,
        preset.scene.white,
        preset.scene.red,
        preset.scene.green,
        preset.scene.blue
    )
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightAutomaticPresetGeometry.cardHeight)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentPadding = androidx.compose.foundation.layout.PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (selected) {
                        colors.action.copy(alpha = DeviceLightAutomaticPresetAlpha.selectedSurface)
                    } else {
                        Color.Transparent
                    }
                )
                .then(
                    if (selected) {
                        Modifier.border(
                            DeviceLightAutomaticPresetGeometry.actionBorderWidth,
                            colors.action,
                            DeviceLightAutomaticPresetGeometry.cardShape
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(DeviceLightAutomaticPresetGeometry.cardPadding)
        ) {
            Column(Modifier.fillMaxSize()) {
                PresetCardHeading(preset.id, label, summary, selected, colors, typography)
                Spacer(Modifier.height(DeviceLightAutomaticPresetGeometry.dividerVerticalGap))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(DeviceLightAutomaticPresetGeometry.dividerHeight)
                        .background(
                            colors.card.outline.copy(
                                alpha = DeviceLightAutomaticPresetAlpha.divider
                            )
                        )
                )
                Spacer(Modifier.height(DeviceLightAutomaticPresetGeometry.dividerVerticalGap))
                PresetSchedule(preset, colors, typography)
                Spacer(Modifier.height(DeviceLightAutomaticPresetGeometry.channelsTopGap))
                PresetChannels(preset.scene, colors, typography)
            }
        }
    }
}

@Composable
private fun PresetCardHeading(
    id: DeviceLightPresetId,
    label: String,
    summary: String,
    selected: Boolean,
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightAutomaticPresetGeometry.cardHeadingHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    end = if (selected) {
                        DeviceLightAutomaticPresetGeometry.selectedIndicatorReserve
                    } else {
                        0.dp
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id.iconResource()),
                contentDescription = null,
                colorFilter = ColorFilter.tint(id.iconColor(colors)),
                modifier = Modifier.size(DeviceLightAutomaticPresetGeometry.cardIconSize)
            )
            Spacer(Modifier.width(DeviceLightAutomaticPresetGeometry.cardIconGap))
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    DeviceLightAutomaticPresetGeometry.cardTextGap
                )
            ) {
                BasicText(
                    text = label,
                    style = typography.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    text = summary,
                    style = typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(DeviceLightAutomaticPresetGeometry.selectedIndicatorSize)
                    .clip(RoundedCornerShape(percent = ROUND_PERCENT))
                    .background(colors.action)
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.card.primaryText),
                    modifier = Modifier.size(
                        DeviceLightAutomaticPresetGeometry.selectedIndicatorIconSize
                    )
                )
            }
        }
    }
}

@Composable
private fun PresetSchedule(
    preset: DeviceLightBuiltInPreset,
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PresetClockIcon(colors.card.secondaryText)
        Spacer(Modifier.width(DeviceLightAutomaticPresetGeometry.scheduleGap))
        BasicText(
            text = stringResource(
                R.string.device_light_auto_preset_schedule_format,
                preset.automaticSchedule.durationMinutes / MINUTES_PER_HOUR,
                preset.automaticSchedule.rampMinutes
            ),
            style = typography.caption.copy(color = colors.card.primaryText),
            maxLines = 1
        )
    }
}

@Composable
private fun PresetClockIcon(color: Color) {
    Canvas(modifier = Modifier.size(DeviceLightAutomaticPresetGeometry.scheduleIconSize)) {
        val stroke = DeviceLightAutomaticPresetGeometry.scheduleIconStrokeWidth.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.minDimension - stroke) / 2f
        drawCircle(color = color, radius = radius, style = Stroke(width = stroke))
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, center.y - radius * CLOCK_HOUR_HAND_FRACTION),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x + radius * CLOCK_MINUTE_HAND_FRACTION, center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PresetChannels(
    scene: DeviceLightPresetScene,
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography
) {
    val values = listOf(
        PresetChannelValue(R.string.device_light_plan_channel_white, scene.white, colors.white),
        PresetChannelValue(R.string.device_light_plan_channel_red, scene.red, colors.red),
        PresetChannelValue(R.string.device_light_plan_channel_green, scene.green, colors.green),
        PresetChannelValue(R.string.device_light_plan_channel_blue, scene.blue, colors.blue)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        values.forEach { value ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(DeviceLightAutomaticPresetGeometry.channelDotSize)) {
                        drawCircle(color = value.color)
                    }
                    Spacer(Modifier.width(DeviceLightAutomaticPresetGeometry.channelDotGap))
                    BasicText(
                        text = stringResource(value.labelRes),
                        style = typography.micro.copy(color = colors.card.primaryText)
                    )
                }
                BasicText(
                    text = stringResource(
                        R.string.device_light_auto_preset_channel_percent,
                        value.percent
                    ),
                    style = typography.micro
                )
            }
        }
    }
}

@Composable
private fun PresetActions(
    state: DeviceLightAutomaticPresetUiState,
    actions: DeviceLightAutomaticPresetActions,
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = DeviceLightAutomaticPresetGeometry.screenHorizontalPadding,
                top = DeviceLightAutomaticPresetGeometry.actionTopPadding,
                end = DeviceLightAutomaticPresetGeometry.screenHorizontalPadding,
                bottom = DeviceLightAutomaticPresetGeometry.screenBottomPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(DeviceLightAutomaticPresetGeometry.actionGap)
    ) {
        PresetActionButton(
            label = stringResource(R.string.cancel),
            filled = false,
            onClick = actions.onCancelClick,
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(ACTION_WEIGHT)
        )
        PresetActionButton(
            label = stringResource(R.string.device_light_auto_preset_use),
            filled = true,
            onClick = { actions.onUseClick(state.selectedPresetId) },
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(ACTION_WEIGHT)
        )
    }
}

@Composable
private fun PresetActionButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .height(DeviceLightAutomaticPresetGeometry.actionHeight)
            .clip(DeviceLightAutomaticPresetGeometry.actionShape)
            .background(if (filled) colors.action else Color.Transparent)
            .border(
                DeviceLightAutomaticPresetGeometry.actionBorderWidth,
                colors.action,
                DeviceLightAutomaticPresetGeometry.actionShape
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = typography.title.copy(
                color = if (filled) colors.card.primaryText else colors.action,
                textAlign = TextAlign.Center
            )
        )
    }
}

@StringRes
private fun DeviceLightPresetId.summaryResource(): Int = when (this) {
    DeviceLightPresetId.NATURAL_AQUARIUM -> R.string.device_light_auto_preset_natural_summary
    DeviceLightPresetId.PLANTED_AQUARIUM -> R.string.device_light_auto_preset_planted_summary
    DeviceLightPresetId.RED_PLANTS -> R.string.device_light_auto_preset_red_summary
    DeviceLightPresetId.VIVID_COLORS -> R.string.device_light_auto_preset_vivid_summary
    DeviceLightPresetId.LOW_TECH -> R.string.device_light_auto_preset_low_tech_summary
    DeviceLightPresetId.AQUASCAPE -> R.string.device_light_auto_preset_aquascape_summary
    DeviceLightPresetId.NEW_SETUP -> R.string.device_light_auto_preset_new_setup_summary
    DeviceLightPresetId.SHADE_PLANTS -> R.string.device_light_auto_preset_shade_summary
}

@DrawableRes
private fun DeviceLightPresetId.iconResource(): Int = when (this) {
    DeviceLightPresetId.NATURAL_AQUARIUM,
    DeviceLightPresetId.PLANTED_AQUARIUM,
    DeviceLightPresetId.RED_PLANTS,
    DeviceLightPresetId.LOW_TECH,
    DeviceLightPresetId.SHADE_PLANTS -> R.drawable.ic_care_plant_health_24
    DeviceLightPresetId.VIVID_COLORS -> R.drawable.ic_care_custom_24
    DeviceLightPresetId.AQUASCAPE -> R.drawable.ic_care_substrate_24
    DeviceLightPresetId.NEW_SETUP -> R.drawable.ic_settings
}

private fun DeviceLightPresetId.iconColor(colors: AquaLightManualColors): Color = when (this) {
    DeviceLightPresetId.RED_PLANTS -> colors.red
    DeviceLightPresetId.VIVID_COLORS -> colors.card.warning
    DeviceLightPresetId.AQUASCAPE,
    DeviceLightPresetId.NEW_SETUP -> colors.card.secondaryText
    DeviceLightPresetId.NATURAL_AQUARIUM,
    DeviceLightPresetId.PLANTED_AQUARIUM,
    DeviceLightPresetId.LOW_TECH,
    DeviceLightPresetId.SHADE_PLANTS -> colors.green
}

private data class PresetChannelValue(
    @StringRes val labelRes: Int,
    val percent: Int,
    val color: Color
)

private const val GRID_COLUMN_COUNT = 2
private const val GRID_WEIGHT = 1f
private const val ACTION_WEIGHT = 1f
private const val ROUND_PERCENT = 50
private const val MINUTES_PER_HOUR = 60
private const val CLOCK_HOUR_HAND_FRACTION = 0.52f
private const val CLOCK_MINUTE_HAND_FRACTION = 0.64f

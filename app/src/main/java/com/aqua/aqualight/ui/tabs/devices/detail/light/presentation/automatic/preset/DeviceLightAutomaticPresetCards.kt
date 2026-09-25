package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.preset

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightBuiltInPreset
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetId
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetScene
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.labelResource

@Composable
internal fun AutomaticPresetCard(
    preset: DeviceLightBuiltInPreset,
    selected: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightAutomaticPresetVisuals
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
        contentPadding = PaddingValues()
    ) {
        PresetCardContent(preset, label, summary, selected, visuals)
    }
}

@Composable
private fun PresetCardContent(
    preset: DeviceLightBuiltInPreset,
    label: String,
    summary: String,
    selected: Boolean,
    visuals: DeviceLightAutomaticPresetVisuals
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (selected) {
                    visuals.colors.action.copy(
                        alpha = DeviceLightAutomaticPresetAlpha.selectedSurface
                    )
                } else {
                    Color.Transparent
                }
            )
            .then(
                if (selected) {
                    Modifier.border(
                        DeviceLightAutomaticPresetGeometry.actionBorderWidth,
                        visuals.colors.action,
                        DeviceLightAutomaticPresetGeometry.cardShape
                    )
                } else {
                    Modifier
                }
            )
            .padding(DeviceLightAutomaticPresetGeometry.cardPadding)
    ) {
        Column(Modifier.fillMaxSize()) {
            PresetCardHeading(preset.id, label, summary, selected, visuals)
            Spacer(Modifier.height(DeviceLightAutomaticPresetGeometry.dividerVerticalGap))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(DeviceLightAutomaticPresetGeometry.dividerHeight)
                    .background(
                        visuals.colors.card.outline.copy(
                            alpha = DeviceLightAutomaticPresetAlpha.divider
                        )
                    )
            )
            Spacer(Modifier.height(DeviceLightAutomaticPresetGeometry.dividerVerticalGap))
            PresetSchedule(preset, visuals)
            Spacer(Modifier.height(DeviceLightAutomaticPresetGeometry.channelsTopGap))
            PresetChannels(preset.scene, visuals)
        }
    }
}

@Composable
private fun PresetCardHeading(
    id: DeviceLightPresetId,
    label: String,
    summary: String,
    selected: Boolean,
    visuals: DeviceLightAutomaticPresetVisuals
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
                colorFilter = ColorFilter.tint(id.iconColor(visuals.colors)),
                modifier = Modifier.size(DeviceLightAutomaticPresetGeometry.cardIconSize)
            )
            Spacer(Modifier.width(DeviceLightAutomaticPresetGeometry.cardIconGap))
            Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                    DeviceLightAutomaticPresetGeometry.cardTextGap
                )
            ) {
                BasicText(
                    text = label,
                    style = visuals.typography.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    text = summary,
                    style = visuals.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) PresetSelectedIndicator(visuals)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PresetSelectedIndicator(
    visuals: DeviceLightAutomaticPresetVisuals
) {
    Box(
        modifier = Modifier
            .size(DeviceLightAutomaticPresetGeometry.selectedIndicatorSize)
            .clip(RoundedCornerShape(percent = ROUND_PERCENT))
            .background(visuals.colors.action)
            .align(Alignment.TopEnd),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_check_24),
            contentDescription = null,
            colorFilter = ColorFilter.tint(visuals.colors.card.primaryText),
            modifier = Modifier.size(DeviceLightAutomaticPresetGeometry.selectedIndicatorIconSize)
        )
    }
}

@Composable
private fun PresetSchedule(
    preset: DeviceLightBuiltInPreset,
    visuals: DeviceLightAutomaticPresetVisuals
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PresetClockIcon(visuals.colors.card.secondaryText)
        Spacer(Modifier.width(DeviceLightAutomaticPresetGeometry.scheduleGap))
        BasicText(
            text = stringResource(
                R.string.device_light_auto_preset_schedule_format,
                preset.automaticSchedule.durationMinutes / MINUTES_PER_HOUR,
                preset.automaticSchedule.rampMinutes
            ),
            style = visuals.typography.caption.copy(color = visuals.colors.card.primaryText),
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
    visuals: DeviceLightAutomaticPresetVisuals
) {
    val values = listOf(
        PresetChannelValue(
            R.string.device_light_plan_channel_white,
            scene.white,
            visuals.colors.white
        ),
        PresetChannelValue(R.string.device_light_plan_channel_red, scene.red, visuals.colors.red),
        PresetChannelValue(
            R.string.device_light_plan_channel_green,
            scene.green,
            visuals.colors.green
        ),
        PresetChannelValue(R.string.device_light_plan_channel_blue, scene.blue, visuals.colors.blue)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
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
                        style = visuals.typography.micro.copy(
                            color = visuals.colors.card.primaryText
                        )
                    )
                }
                BasicText(
                    text = stringResource(
                        R.string.device_light_auto_preset_channel_percent,
                        value.percent
                    ),
                    style = visuals.typography.micro
                )
            }
        }
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
    DeviceLightPresetId.NATURAL_AQUARIUM -> R.drawable.ic_light_preset_natural
    DeviceLightPresetId.PLANTED_AQUARIUM -> R.drawable.ic_light_preset_planted
    DeviceLightPresetId.RED_PLANTS -> R.drawable.ic_light_preset_red_plants
    DeviceLightPresetId.VIVID_COLORS -> R.drawable.ic_light_preset_vivid
    DeviceLightPresetId.LOW_TECH -> R.drawable.ic_light_preset_low_tech
    DeviceLightPresetId.AQUASCAPE -> R.drawable.ic_light_preset_aquascape
    DeviceLightPresetId.NEW_SETUP -> R.drawable.ic_light_preset_new_setup
    DeviceLightPresetId.SHADE_PLANTS -> R.drawable.ic_light_preset_shade_plants
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

private const val ROUND_PERCENT = 50
private const val MINUTES_PER_HOUR = 60
private const val CLOCK_HOUR_HAND_FRACTION = 0.52f
private const val CLOCK_MINUTE_HAND_FRACTION = 0.64f

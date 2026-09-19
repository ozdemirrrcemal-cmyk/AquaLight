package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.programs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor.AutomaticCycleEventIcon
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor.AutomaticCycleEventKind
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AutomaticCalendarIcon
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AutomaticMoreButton
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AutomaticRampIcon
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.DeviceLightAutomaticAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.DeviceLightAutomaticGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightDashboardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightManualColors

@Composable
internal fun DeviceLightAutomaticProgramCard(
    program: DeviceLightAutomaticProgram,
    channels: List<DeviceLightAutomaticChannel>,
    enabled: Boolean,
    actions: DeviceLightAutomaticProgramsActions
) {
    val colors = aquaLightManualColors()
    val typography = aquaLightDashboardTypography(colors.card)
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button) {
                actions.onProgramClick(program.programId)
            },
        contentPadding = DeviceLightAutomaticGeometry.cardContentPadding
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DeviceLightAutomaticGeometry.cardContentMinimumHeight),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AutomaticProgramHeader(program, enabled, actions, typography, colors.card)
            AutomaticProgramSchedule(program, typography, colors)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(DeviceLightAutomaticGeometry.cardDividerHeight)
                    .background(colors.card.mediaOutline)
            )
            AutomaticProgramChannels(program, channels, typography, colors)
        }
    }
}

@Composable
private fun AutomaticProgramHeader(
    program: DeviceLightAutomaticProgram,
    enabled: Boolean,
    actions: DeviceLightAutomaticProgramsActions,
    typography: AquaDeviceCardTypography,
    colors: AquaDeviceCardColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutomaticCalendarIcon(
            color = colors.secondaryText,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.cardHeaderIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.headerIconGap))
        BasicText(
            text = automaticWeekdaysText(program.weekdaysMask),
            style = typography.title,
            maxLines = 1,
            modifier = Modifier.weight(HEADER_TITLE_WEIGHT)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.headerControlGap))
        AutomaticProgramSwitch(
            checked = program.enabled,
            enabled = enabled,
            colors = colors,
            onCheckedChange = { checked -> actions.onEnabledChanged(program.programId, checked) }
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.headerControlGap))
        AutomaticMoreButton(
            color = colors.secondaryText,
            contentDescriptionText = stringResource(
                R.string.device_light_auto_more_actions_description
            ),
            enabled = enabled,
            onClick = { actions.onMoreClick(program.programId) }
        )
    }
}

@Composable
private fun AutomaticProgramSchedule(
    program: DeviceLightAutomaticProgram,
    typography: AquaDeviceCardTypography,
    colors: AquaLightManualColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutomaticProgramTime(
            kind = AutomaticCycleEventKind.SUNRISE,
            timeMs = program.startTimeMs,
            accent = colors.card.warning,
            typography = typography
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleArrowGap))
        BasicText(
            text = stringResource(R.string.device_light_auto_time_arrow),
            style = typography.title.copy(color = colors.card.secondaryText)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleArrowGap))
        AutomaticProgramTime(
            kind = AutomaticCycleEventKind.SUNSET,
            timeMs = program.endTimeMs,
            accent = colors.shrimp,
            typography = typography
        )
        Spacer(Modifier.weight(SCHEDULE_FLEXIBLE_GAP_WEIGHT))
        Box(
            Modifier
                .width(DeviceLightAutomaticGeometry.scheduleDividerWidth)
                .height(DeviceLightAutomaticGeometry.scheduleDividerHeight)
                .background(colors.card.mediaOutline)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleDividerGap))
        AutomaticRampIcon(
            color = colors.action,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.scheduleEventIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleTextGap))
        BasicText(
            text = stringResource(
                R.string.device_light_auto_transition_format,
                stringResource(
                    R.string.device_light_auto_minutes,
                    (program.rampDurationMs / MILLIS_PER_MINUTE).toInt()
                )
            ),
            style = typography.body.copy(color = colors.card.secondaryText),
            maxLines = 1
        )
    }
}

@Composable
private fun AutomaticProgramTime(
    kind: AutomaticCycleEventKind,
    timeMs: Long,
    accent: Color,
    typography: AquaDeviceCardTypography
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AutomaticCycleEventIcon(
            kind = kind,
            color = accent,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.scheduleEventIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleTextGap))
        BasicText(
            text = automaticTimeText(timeMs),
            style = typography.title,
            maxLines = 1
        )
    }
}

@Composable
private fun AutomaticProgramChannels(
    program: DeviceLightAutomaticProgram,
    channels: List<DeviceLightAutomaticChannel>,
    typography: AquaDeviceCardTypography,
    colors: AquaLightManualColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        channels.forEach { channel ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    DeviceLightAutomaticGeometry.channelTextGap
                )
            ) {
                Canvas(modifier = Modifier.size(DeviceLightAutomaticGeometry.channelDotSize)) {
                    drawCircle(color = channel.automaticCardColor(colors))
                }
                BasicText(
                    text = stringResource(
                        R.string.device_light_auto_channel_value_format,
                        stringResource(channel.shortLabelResource()),
                        program.scene.channels.getValue(channel)
                    ),
                    style = typography.body,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AutomaticProgramSwitch(
    checked: Boolean,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    onCheckedChange: (Boolean) -> Unit
) {
    val description = stringResource(
        if (checked) {
            R.string.device_light_auto_disable_program_description
        } else {
            R.string.device_light_auto_enable_program_description
        }
    )
    val trackColor = if (checked) {
        colors.accent
    } else {
        colors.mediaOutline.copy(alpha = DeviceLightAutomaticAlpha.switchOffTrack)
    }
    Box(
        modifier = Modifier
            .width(DeviceLightAutomaticGeometry.switchWidth)
            .height(DeviceLightAutomaticGeometry.switchHeight)
            .alpha(if (enabled) ENABLED_ALPHA else DeviceLightAutomaticAlpha.disabled)
            .background(trackColor, DeviceLightAutomaticGeometry.switchShape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .semantics { contentDescription = description }
    ) {
        Box(
            modifier = Modifier
                .padding(DeviceLightAutomaticGeometry.switchInset)
                .size(DeviceLightAutomaticGeometry.switchThumbSize)
                .background(colors.primaryText, RoundedCornerShape(percent = THUMB_ROUND_PERCENT))
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
        )
    }
}

@Composable
private fun automaticWeekdaysText(mask: Int): String {
    if (mask == EVERY_DAY_MASK) return stringResource(R.string.device_light_auto_every_day)
    val days = listOf(
        MONDAY_MASK to R.string.device_light_auto_day_monday,
        TUESDAY_MASK to R.string.device_light_auto_day_tuesday,
        WEDNESDAY_MASK to R.string.device_light_auto_day_wednesday,
        THURSDAY_MASK to R.string.device_light_auto_day_thursday,
        FRIDAY_MASK to R.string.device_light_auto_day_friday,
        SATURDAY_MASK to R.string.device_light_auto_day_saturday,
        SUNDAY_MASK to R.string.device_light_auto_day_sunday
    )
    return days.mapNotNull { (bit, labelRes) ->
        stringResource(labelRes).takeIf { mask and bit != 0 }
    }.joinToString(" · ")
}

@Composable
private fun automaticTimeText(timeMs: Long): String {
    val totalMinutes = timeMs / MILLIS_PER_MINUTE
    val hour = (totalMinutes / MINUTES_PER_HOUR).toInt()
    val minute = (totalMinutes % MINUTES_PER_HOUR).toInt()
    return stringResource(R.string.device_light_auto_time_format, hour, minute)
}

private fun DeviceLightAutomaticChannel.shortLabelResource(): Int = when (this) {
    DeviceLightAutomaticChannel.RED -> R.string.device_light_plan_channel_red
    DeviceLightAutomaticChannel.GREEN -> R.string.device_light_plan_channel_green
    DeviceLightAutomaticChannel.BLUE -> R.string.device_light_plan_channel_blue
    DeviceLightAutomaticChannel.WHITE -> R.string.device_light_plan_channel_white
}

private fun DeviceLightAutomaticChannel.automaticCardColor(colors: AquaLightManualColors): Color =
    when (this) {
        DeviceLightAutomaticChannel.RED -> colors.red
        DeviceLightAutomaticChannel.GREEN -> colors.green
        DeviceLightAutomaticChannel.BLUE -> colors.blue
        DeviceLightAutomaticChannel.WHITE -> colors.white
    }

private const val EVERY_DAY_MASK = 0x7f
private const val MONDAY_MASK = 0x40
private const val TUESDAY_MASK = 0x20
private const val WEDNESDAY_MASK = 0x10
private const val THURSDAY_MASK = 0x08
private const val FRIDAY_MASK = 0x04
private const val SATURDAY_MASK = 0x02
private const val SUNDAY_MASK = 0x01
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val HEADER_TITLE_WEIGHT = 1f
private const val SCHEDULE_FLEXIBLE_GAP_WEIGHT = 1f
private const val ENABLED_ALPHA = 1f
private const val THUMB_ROUND_PERCENT = 50

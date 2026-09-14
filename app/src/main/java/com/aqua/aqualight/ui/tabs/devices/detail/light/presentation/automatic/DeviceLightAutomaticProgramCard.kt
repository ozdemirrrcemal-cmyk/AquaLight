package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

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
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightPlanChartColors
import com.aqua.aqualight.ui.common.light.aquaLightDashboardColors
import com.aqua.aqualight.ui.common.light.aquaLightDashboardTypography
import com.aqua.aqualight.ui.common.light.aquaLightPlanChartColors

@Composable
internal fun DeviceLightAutomaticProgramCard(
    program: DeviceLightAutomaticProgram,
    channels: List<DeviceLightAutomaticChannel>,
    enabled: Boolean,
    actions: DeviceLightAutomaticProgramsActions
) {
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)
    val chartColors = aquaLightPlanChartColors(colors)
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button) {
                actions.onProgramClick(program.programId)
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AutomaticProgramHeader(program, enabled, actions, typography, colors.secondaryText)
            Spacer(Modifier.height(DeviceLightAutomaticGeometry.metaTopGap))
            AutomaticProgramMeta(program, typography, colors.secondaryText)
            Spacer(Modifier.height(DeviceLightAutomaticGeometry.chartTopGap))
            DeviceLightAutomaticProgramChart(
                program = program,
                channels = channels,
                colors = colors,
                chartColors = chartColors,
                typography = typography
            )
            Spacer(Modifier.height(DeviceLightAutomaticGeometry.legendTopGap))
            AutomaticProgramLegend(program, channels, typography, chartColors)
        }
    }
}

@Composable
private fun AutomaticProgramHeader(
    program: DeviceLightAutomaticProgram,
    enabled: Boolean,
    actions: DeviceLightAutomaticProgramsActions,
    typography: AquaDeviceCardTypography,
    secondaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutomaticCalendarIcon(
            color = secondaryColor,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.cardHeaderIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.headerIconGap))
        BasicText(
            text = automaticWeekdaysText(program.weekdaysMask),
            style = typography.title,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.headerControlGap))
        AutomaticProgramSwitch(
            checked = program.enabled,
            enabled = enabled,
            onCheckedChange = { checked -> actions.onEnabledChanged(program.programId, checked) }
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.headerControlGap))
        AutomaticMoreButton(
            color = secondaryColor,
            contentDescriptionText = stringResource(
                R.string.device_light_auto_more_actions_description
            ),
            enabled = enabled,
            onClick = { actions.onMoreClick(program.programId) }
        )
    }
}

@Composable
private fun AutomaticProgramMeta(
    program: DeviceLightAutomaticProgram,
    typography: AquaDeviceCardTypography,
    secondaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutomaticClockIcon(
            color = secondaryColor,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.cardMetaIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.metaItemGap))
        BasicText(
            text = automaticTimeRangeText(program),
            style = typography.body.copy(color = secondaryColor),
            maxLines = 1
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.rampItemStartGap))
        AutomaticRampIcon(
            color = secondaryColor,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.cardMetaIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.metaItemGap))
        BasicText(
            text = stringResource(
                R.string.device_light_auto_ramp_format,
                stringResource(
                    R.string.device_light_auto_minutes,
                    (program.rampDurationMs / MILLIS_PER_MINUTE).toInt()
                )
            ),
            style = typography.body.copy(color = secondaryColor),
            maxLines = 1
        )
    }
}

@Composable
private fun AutomaticProgramLegend(
    program: DeviceLightAutomaticProgram,
    channels: List<DeviceLightAutomaticChannel>,
    typography: AquaDeviceCardTypography,
    chartColors: AquaLightPlanChartColors
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
                    DeviceLightAutomaticGeometry.legendTextGap
                )
            ) {
                Canvas(modifier = Modifier.size(DeviceLightAutomaticGeometry.legendDotSize)) {
                    drawCircle(color = channel.automaticColor(chartColors))
                }
                BasicText(
                    text = stringResource(
                        R.string.device_light_auto_channel_value_format,
                        channel.shortLabel(),
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
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = aquaLightDashboardColors()
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
            .alpha(if (enabled) 1f else DeviceLightAutomaticAlpha.disabled)
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
                .background(colors.primaryText, RoundedCornerShape(percent = 50))
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
private fun automaticTimeRangeText(program: DeviceLightAutomaticProgram): String {
    val start = automaticTimeText(program.startTimeMs)
    val end = automaticTimeText(program.endTimeMs)
    return stringResource(R.string.device_light_auto_time_range_format, start, end)
}

@Composable
private fun automaticTimeText(timeMs: Long): String {
    val totalMinutes = timeMs / MILLIS_PER_MINUTE
    val hour = (totalMinutes / MINUTES_PER_HOUR).toInt()
    val minute = (totalMinutes % MINUTES_PER_HOUR).toInt()
    return stringResource(R.string.device_light_auto_time_format, hour, minute)
}

@Composable
private fun DeviceLightAutomaticChannel.shortLabel(): String = stringResource(
    when (this) {
        DeviceLightAutomaticChannel.RED -> R.string.device_light_plan_channel_red
        DeviceLightAutomaticChannel.GREEN -> R.string.device_light_plan_channel_green
        DeviceLightAutomaticChannel.BLUE -> R.string.device_light_plan_channel_blue
        DeviceLightAutomaticChannel.WHITE -> R.string.device_light_plan_channel_white
    }
)

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

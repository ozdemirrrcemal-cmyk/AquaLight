package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanReason
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightPlanChartColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightPlanChartSpec
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightDashboardColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightDashboardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightPlanChartColors
import kotlin.math.roundToInt

@Composable
internal fun DeviceLightPlanCard(
    data: DeviceLightPlanCardData,
    enabled: Boolean,
    onActionClick: () -> Unit,
    onSwitchToAutomatic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)
    val chartColors = aquaLightPlanChartColors(colors)
    val state = data.toPlanCardState(enabled)
    val description = state.planContentDescription()

    AquaDeviceCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AquaLightDashboardGeometry.planCardMinimumHeight)
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(
            start = AquaDeviceCardGeometry.contentHorizontalPadding,
            top = AquaDeviceCardGeometry.contentVerticalPadding,
            end = AquaDeviceCardGeometry.contentHorizontalPadding,
            bottom = AquaLightDashboardGeometry.planCardBottomPadding
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DeviceLightPlanHeader(
                state = state,
                colors = colors,
                typography = typography,
                onActionClick = onActionClick
            )
            Spacer(modifier = Modifier.height(AquaLightDashboardGeometry.planContentTopGap))
            DeviceLightPlanChart(
                state = state,
                colors = colors,
                chartColors = chartColors,
                typography = typography,
                onActionClick = if (state.manualMode) {
                    onSwitchToAutomatic
                } else {
                    onActionClick
                }
            )
        }
    }
}

@Composable
private fun DeviceLightPlanHeader(
    state: DeviceLightPlanCardState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightDashboardGeometry.planActionHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = stringResource(
                    if (state.manualMode) {
                        R.string.device_light_plan_manual_title
                    } else {
                        R.string.device_light_plan_title
                    }
                ),
                style = typography.title.copy(color = colors.primaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.showProgramAction) {
            Row(
                modifier = Modifier
                    .height(AquaLightDashboardGeometry.planActionHeight)
                    .alpha(if (state.enabled) 1f else AquaLightDashboardAlpha.disabledControl)
                    .clip(AquaLightDashboardGeometry.planActionShape)
                    .clickable(
                        enabled = state.enabled,
                        role = Role.Button,
                        onClick = onActionClick
                    )
                    .padding(horizontal = AquaLightDashboardGeometry.planHeaderGap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    AquaLightDashboardGeometry.planHeaderGap
                )
            ) {
                BasicText(
                    text = stringResource(R.string.device_light_plan_view_program),
                    style = typography.caption.copy(color = colors.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LightPlanChevron(
                    color = colors.primaryText,
                    modifier = Modifier.size(AquaLightDashboardGeometry.planChevronSize)
                )
            }
        }
    }
}

@Composable
private fun DeviceLightPlanChart(
    state: DeviceLightPlanCardState,
    colors: AquaDeviceCardColors,
    chartColors: AquaLightPlanChartColors,
    typography: AquaDeviceCardTypography,
    onActionClick: () -> Unit
) {
    val nowTimeMs = state.presentation?.nowTimeMs
    Row(modifier = Modifier.fillMaxWidth()) {
        DeviceLightPlanYAxis(colors = colors, typography = typography)
        Spacer(modifier = Modifier.width(AquaLightDashboardGeometry.planYAxisGap))
        Column(modifier = Modifier.weight(1f)) {
            if (state.manualMode || nowTimeMs == null) {
                Spacer(modifier = Modifier.height(AquaLightDashboardGeometry.planMarkerLabelHeight))
            } else {
                DeviceLightCurrentTimeLabel(
                    currentTime = checkNotNull(state.currentTime),
                    colors = colors,
                    typography = typography,
                    nowTimeMs = nowTimeMs
                )
            }
            DeviceLightPlanPlot(
                state = state,
                colors = colors,
                chartColors = chartColors,
                typography = typography,
                onActionClick = onActionClick
            )
            DeviceLightPlanXAxis(colors = colors, typography = typography)
        }
    }
    if (!state.hasRenderableSchedule) {
        Spacer(
            modifier = Modifier.height(
                AquaLightDashboardGeometry.planLegendTopGap +
                    AquaLightDashboardGeometry.planLegendHeight
            )
        )
    } else {
        Spacer(modifier = Modifier.height(AquaLightDashboardGeometry.planLegendTopGap))
        DeviceLightPlanLegend(
            state = state,
            colors = colors,
            typography = typography
        )
    }
}

@Composable
private fun DeviceLightPlanPlot(
    state: DeviceLightPlanCardState,
    colors: AquaDeviceCardColors,
    chartColors: AquaLightPlanChartColors,
    typography: AquaDeviceCardTypography,
    onActionClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightDashboardGeometry.planPlotHeight)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(AquaLightDashboardGeometry.planPlotHeight)
        ) {
            drawLightPlanGrid(chartColors)
            if (state.hasRenderableSchedule) {
                val presentation = checkNotNull(state.presentation)
                presentation.nowTimeMs?.let { nowTimeMs ->
                    drawCurrentTimeGuide(nowTimeMs, chartColors)
                }
                presentation.series.forEach { series ->
                    drawLightPlanSeries(
                        series = series,
                        color = series.channel.toComposeColor(),
                        channelScale = presentation.channelScale
                    )
                }
            }
        }
        if (state.manualMode) {
            DeviceLightManualPlanNotice(
                enabled = state.enabled,
                colors = colors,
                typography = typography,
                onActionClick = onActionClick,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (!state.hasRenderableSchedule) {
            DeviceLightEmptyPlanNotice(
                reason = state.plan?.reason,
                colors = colors,
                typography = typography,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun DeviceLightEmptyPlanNotice(
    reason: DeviceLightPlanReason?,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val messageRes = when (reason) {
        DeviceLightPlanReason.RTC_NOT_READY -> R.string.device_light_plan_rtc_not_ready
        DeviceLightPlanReason.NO_ENABLED_AUTO_PROGRAM_TODAY ->
            R.string.device_light_plan_no_automatic_program_today
        DeviceLightPlanReason.CUSTOM_NOT_INSTALLED ->
            R.string.device_light_plan_custom_not_installed
        DeviceLightPlanReason.CUSTOM_NOT_SCHEDULED_TODAY ->
            R.string.device_light_plan_custom_not_scheduled_today
        DeviceLightPlanReason.OK,
        DeviceLightPlanReason.MODE_HAS_NO_SCHEDULE,
        null -> R.string.device_light_plan_unavailable
    }
    BasicText(
        text = stringResource(messageRes),
        style = typography.body.copy(
            color = colors.primaryText,
            textAlign = TextAlign.Center
        ),
        modifier = modifier
    )
}

@Composable
private fun DeviceLightManualPlanNotice(
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = stringResource(R.string.device_light_plan_manual_message),
            style = typography.body.copy(
                color = colors.primaryText,
                textAlign = TextAlign.Center
            ),
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(AquaLightDashboardGeometry.planManualMessageActionGap))
        Row(
            modifier = Modifier
                .height(AquaLightDashboardGeometry.planActionHeight)
                .alpha(if (enabled) 1f else AquaLightDashboardAlpha.disabledControl)
                .clip(AquaLightDashboardGeometry.planActionShape)
                .border(
                    width = AquaLightDashboardGeometry.planActionOutlineWidth,
                    color = colors.accent,
                    shape = AquaLightDashboardGeometry.planActionShape
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onActionClick)
                .padding(horizontal = AquaLightDashboardGeometry.planActionHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            BasicText(
                text = stringResource(R.string.device_light_plan_switch_to_automatic),
                style = typography.body.copy(color = colors.accent),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DeviceLightCurrentTimeLabel(
    currentTime: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    nowTimeMs: Long
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightDashboardGeometry.planMarkerLabelHeight)
    ) {
        val plotFraction = nowTimeMs.toFloat() / MILLIS_IN_DAY
        val labelWidth = AquaLightDashboardGeometry.planMarkerLabelWidth
        BasicText(
            text = stringResource(R.string.device_light_plan_current_time_format, currentTime),
            style = typography.micro.copy(
                color = colors.secondaryText,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            modifier = Modifier
                .absoluteOffset(x = maxWidth * plotFraction - labelWidth / 2)
                .width(labelWidth)
        )
    }
}

@Composable
private fun DeviceLightPlanYAxis(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val values = (AquaLightPlanChartSpec.maximumPercent downTo
        AquaLightPlanChartSpec.minimumPercent step AquaLightPlanChartSpec.percentStep)
    Column(
        modifier = Modifier
            .padding(top = AquaLightDashboardGeometry.planMarkerLabelHeight)
            .absoluteOffset(x = AquaLightDashboardGeometry.planYAxisStartOffset)
            .width(AquaLightDashboardGeometry.planYAxisWidth)
            .height(AquaLightDashboardGeometry.planPlotHeight),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        values.forEach { value ->
            BasicText(
                text = stringResource(R.string.device_light_plan_percent_axis_format, value),
                style = typography.micro.copy(
                    color = colors.secondaryText,
                    textAlign = TextAlign.End
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DeviceLightPlanXAxis(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val hours = (AquaLightPlanChartSpec.minimumHour..AquaLightPlanChartSpec.maximumHour step
        AquaLightPlanChartSpec.hourStep).toList()
    val labels = hours.map { hour ->
        stringResource(R.string.device_light_plan_hour_format, hour)
    }
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightDashboardGeometry.planXAxisHeight),
        content = {
            labels.forEach { label ->
                BasicText(
                    text = label,
                    style = typography.micro.copy(
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { measurable -> measurable.measure(childConstraints) }
        val width = constraints.maxWidth
        layout(width, constraints.maxHeight) {
            val intervals = (placeables.size - 1).coerceAtLeast(1)
            placeables.forEachIndexed { index, placeable ->
                val tickCenter = width.toFloat() * index / intervals
                val x = (tickCenter - placeable.width / 2f)
                    .roundToInt()
                placeable.place(x, 0)
            }
        }
    }
}

@Composable
private fun DeviceLightPlanLegend(
    state: DeviceLightPlanCardState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val items = state.presentation?.series.orEmpty().map { series ->
        DeviceLightLegendItem(
            label = series.channel.shortLabel(),
            color = series.channel.toComposeColor()
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightDashboardGeometry.planLegendHeight),
        horizontalArrangement = Arrangement.spacedBy(
            AquaLightDashboardGeometry.planLegendItemGap,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    AquaLightDashboardGeometry.planLegendTextGap
                )
            ) {
                Canvas(modifier = Modifier.size(AquaLightDashboardGeometry.planLegendDotSize)) {
                    drawCircle(color = item.color)
                }
                BasicText(
                    text = item.label,
                    style = typography.micro.copy(color = colors.secondaryText),
                    maxLines = 1
                )
            }
        }
    }
}

private data class DeviceLightLegendItem(
    val label: String,
    val color: Color
)


package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.common.light.AquaLightPlanChartColors
import com.aqua.aqualight.ui.common.light.AquaLightPlanChartSpec
import com.aqua.aqualight.ui.common.light.aquaLightDashboardColors
import com.aqua.aqualight.ui.common.light.aquaLightDashboardTypography
import com.aqua.aqualight.ui.common.light.aquaLightPlanChartColors
import kotlin.math.roundToInt

@Composable
internal fun DeviceLightPlanCard(modifier: Modifier = Modifier) {
    val preview = remember { deviceLightPlanPreview() }
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)
    val chartColors = aquaLightPlanChartColors(colors)
    val currentTime = stringResource(
        R.string.device_light_plan_time_format,
        preview.currentHour,
        preview.currentMinute
    )
    val description = stringResource(
        R.string.device_light_plan_content_description,
        currentTime
    )

    AquaDeviceCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AquaLightDashboardGeometry.planCardMinimumHeight)
            .semantics { contentDescription = description }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DeviceLightPlanHeader(colors = colors, typography = typography)
            Spacer(modifier = Modifier.height(AquaLightDashboardGeometry.planContentTopGap))
            DeviceLightPlanChart(
                preview = preview,
                currentTime = currentTime,
                colors = colors,
                chartColors = chartColors,
                typography = typography
            )
        }
    }
}

@Composable
private fun DeviceLightPlanHeader(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = stringResource(R.string.device_light_plan_title),
                style = typography.title.copy(color = colors.primaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = stringResource(R.string.device_light_plan_subtitle),
                style = typography.caption.copy(color = colors.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.planHeaderGap)
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

@Composable
private fun DeviceLightPlanChart(
    preview: DeviceLightPlanPreview,
    currentTime: String,
    colors: AquaDeviceCardColors,
    chartColors: AquaLightPlanChartColors,
    typography: AquaDeviceCardTypography
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        DeviceLightPlanYAxis(colors = colors, typography = typography)
        Spacer(modifier = Modifier.width(AquaLightDashboardGeometry.planYAxisGap))
        Column(modifier = Modifier.weight(1f)) {
            DeviceLightCurrentTimeLabel(
                currentTime = currentTime,
                colors = colors,
                typography = typography,
                currentHour = preview.currentHour
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AquaLightDashboardGeometry.planPlotHeight)
            ) {
                drawLightPlanGrid(chartColors)
                drawCurrentTimeGuide(preview.currentHour, chartColors)
                preview.series.forEach { series ->
                    drawLightPlanSeries(series, chartColors.colorFor(series.channel))
                }
            }
            DeviceLightPlanXAxis(colors = colors, typography = typography)
        }
    }
    Spacer(modifier = Modifier.height(AquaLightDashboardGeometry.planLegendTopGap))
    DeviceLightPlanLegend(
        colors = colors,
        chartColors = chartColors,
        typography = typography
    )
}

@Composable
private fun DeviceLightCurrentTimeLabel(
    currentTime: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    currentHour: Int
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightDashboardGeometry.planMarkerLabelHeight)
    ) {
        val plotFraction = currentHour.toFloat() / AquaLightPlanChartSpec.maximumHour
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
                    .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

@Composable
private fun DeviceLightPlanLegend(
    colors: AquaDeviceCardColors,
    chartColors: AquaLightPlanChartColors,
    typography: AquaDeviceCardTypography
) {
    val items = DeviceLightPlanChannel.entries.map { channel ->
        DeviceLightLegendItem(
            label = stringResource(channel.labelRes()),
            color = chartColors.colorFor(channel)
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

@StringRes
private fun DeviceLightPlanChannel.labelRes(): Int = when (this) {
    DeviceLightPlanChannel.RED -> R.string.device_light_plan_channel_red
    DeviceLightPlanChannel.GREEN -> R.string.device_light_plan_channel_green
    DeviceLightPlanChannel.BLUE -> R.string.device_light_plan_channel_blue
    DeviceLightPlanChannel.WHITE -> R.string.device_light_plan_channel_white
}

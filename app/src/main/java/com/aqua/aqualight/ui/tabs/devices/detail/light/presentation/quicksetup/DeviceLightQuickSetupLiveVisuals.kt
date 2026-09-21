package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightManualColors
import java.time.LocalDate

@Composable
internal fun QuickSetupManagedPlanCard(
    productName: String,
    managed: DeviceLightManagedPlanSnapshot
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(text = productName, style = typography.title)
            ReviewMetricRow(
                stringResource(R.string.device_light_quick_setup_source_label),
                stringResource(R.string.device_light_quick_setup_managed_plan_source)
            )
            ReviewMetricRow(
                stringResource(R.string.device_light_quick_setup_current_phase_label),
                managed.activePhaseIndex?.let { index ->
                    stringResource(R.string.device_light_quick_setup_phase_short, index + 1)
                } ?: stringResource(R.string.device_light_quick_setup_not_available)
            )
            ReviewMetricRow(
                stringResource(R.string.device_light_quick_setup_transition_label),
                managed.transitionPermille?.let { value ->
                    stringResource(
                        R.string.device_light_quick_setup_transition_percent,
                        value / DeviceLightQuickSetupGeometry.transitionPermillePerPercent
                    )
                } ?: stringResource(R.string.device_light_quick_setup_not_available)
            )
            ReviewMetricRow(
                stringResource(R.string.device_light_quick_setup_next_phase_date_label),
                managed.nextTransitionEpochDay?.let { day ->
                    LocalDate.ofEpochDay(day.toLong()).toString()
                } ?: stringResource(R.string.device_light_quick_setup_not_available)
            )
            ReviewMetricRow(
                stringResource(R.string.device_light_quick_setup_plan_id_label),
                managed.planId ?: stringResource(R.string.device_light_quick_setup_not_available)
            )
        }
    }
}

@Composable
internal fun QuickSetupLiveChart(
    plan: DeviceLightPlanSnapshot,
    productKey: String
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    val lightColors = aquaLightManualColors()
    val seriesColors = if (productKey == WRGB_PRODUCT) {
        listOf(lightColors.red, lightColors.green, lightColors.blue, lightColors.white)
    } else {
        listOf(lightColors.red, lightColors.green, lightColors.blue)
    }
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_live_graph_title),
                style = typography.title
            )
            if (!plan.available || plan.points.isEmpty()) {
                BasicText(
                    text = stringResource(R.string.device_light_quick_setup_live_graph_empty),
                    style = typography.body
                )
            } else {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(DeviceLightQuickSetupGeometry.chartHeight)
                ) {
                    drawLivePlan(
                        plan = plan,
                        seriesColors = seriesColors,
                        gridColor = colors.outline.copy(alpha = DeviceLightQuickSetupAlpha.grid)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLivePlan(
    plan: DeviceLightPlanSnapshot,
    seriesColors: List<Color>,
    gridColor: Color
) {
    drawLiveGrid(gridColor)
    drawLiveSeries(plan, seriesColors)
}

private fun DrawScope.drawLiveGrid(gridColor: Color) {
    repeat(DeviceLightQuickSetupGeometry.chartGridLineCount) { index ->
        val y = size.height * index / DeviceLightQuickSetupGeometry.chartGridIntervalCount
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = DeviceLightQuickSetupGeometry.chartGridStroke.toPx()
        )
    }
}

private fun DrawScope.drawLiveSeries(
    plan: DeviceLightPlanSnapshot,
    seriesColors: List<Color>
) {
    plan.points.firstOrNull()?.channelLevels?.indices?.forEach { channelIndex ->
        if (channelIndex < seriesColors.size) {
            drawPath(
                path = buildLiveSeriesPath(plan, channelIndex),
                color = seriesColors[channelIndex],
                style = Stroke(
                    width = DeviceLightQuickSetupGeometry.chartLineStroke.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

private fun DrawScope.buildLiveSeriesPath(
    plan: DeviceLightPlanSnapshot,
    channelIndex: Int
): Path = Path().apply {
    plan.points.forEachIndexed { pointIndex, point ->
        val x = (point.timeMs.toFloat() / MILLIS_PER_DAY) * size.width
        val value = point.channelLevels[channelIndex].toFloat() / plan.channelScale.toFloat()
        val y = size.height * (1f - value.coerceIn(0f, 1f))
        if (pointIndex == 0) moveTo(x, y) else lineTo(x, y)
    }
}

@Composable
internal fun ReviewMetricRow(label: String, value: String) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(text = label, style = typography.caption, modifier = Modifier.weight(1f))
        BasicText(
            text = value,
            style = typography.body,
            modifier = Modifier.weight(1f),
            maxLines = 2
        )
    }
}


private const val WRGB_PRODUCT = "LIGHT_WRGB_PRO_ELITE"
private const val MILLIS_PER_DAY = 86_400_000f

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPhase
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupRecommendation
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightManualColors

@Composable
internal fun QuickSetupCalculatingScreen() {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(44.dp))
        Canvas(Modifier.size(DeviceLightQuickSetupGeometry.spinnerSize)) {
            drawCircle(
                color = colors.outline.copy(alpha = 0.35f),
                style = Stroke(DeviceLightQuickSetupGeometry.spinnerStroke.toPx())
            )
            drawArc(
                color = colors.accent,
                startAngle = -90f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(
                    width = DeviceLightQuickSetupGeometry.spinnerStroke.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
        BasicText(
            text = stringResource(R.string.device_light_quick_setup_calculating_title),
            style = typography.title.copy(textAlign = TextAlign.Center)
        )
        BasicText(
            text = stringResource(R.string.device_light_quick_setup_calculating_body),
            style = typography.body.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun QuickSetupReviewScreen(state: DeviceLightQuickSetupUiState) {
    val recommendation = checkNotNull(state.recommendation)
    val currentPhase = recommendation.currentPhaseIndex()
    Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.sectionGap)) {
        QuickSetupHeading(
            title = stringResource(R.string.device_light_quick_setup_review_title),
            description = stringResource(R.string.device_light_quick_setup_review_description)
        )
        QuickSetupRecommendationSummary(recommendation, currentPhase)
        QuickSetupPhaseTimeline(recommendation.phases, currentPhase)
        QuickSetupChannelScene(recommendation)
        if (recommendation.co2Limited) {
            QuickSetupInfoCard(stringResource(R.string.device_light_quick_setup_co2_limited_warning))
        }
        if (state.reviewRequiredAfterStale) {
            QuickSetupInfoCard(stringResource(R.string.device_light_quick_setup_review_again))
        }
    }
}

@Composable
internal fun QuickSetupApplyingScreen() {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(38.dp))
        Canvas(Modifier.size(DeviceLightQuickSetupGeometry.spinnerSize)) {
            drawCircle(
                color = colors.outline.copy(alpha = 0.35f),
                style = Stroke(DeviceLightQuickSetupGeometry.spinnerStroke.toPx())
            )
            drawArc(
                color = colors.accent,
                startAngle = -90f,
                sweepAngle = 285f,
                useCenter = false,
                style = Stroke(
                    width = DeviceLightQuickSetupGeometry.spinnerStroke.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
        BasicText(
            text = stringResource(R.string.device_light_quick_setup_applying_title),
            style = typography.title.copy(textAlign = TextAlign.Center)
        )
        BasicText(
            text = stringResource(R.string.device_light_quick_setup_applying_body),
            style = typography.body.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
        )
        QuickSetupInfoCard(stringResource(R.string.device_light_quick_setup_applying_authority_info))
    }
}

@Composable
internal fun QuickSetupLiveScreen(
    state: DeviceLightQuickSetupUiState,
    onAction: (DeviceLightQuickSetupAction) -> Unit
) {
    val context = checkNotNull(state.context)
    val managed = checkNotNull(state.managedPlan)
    Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.sectionGap)) {
        QuickSetupHeading(
            title = stringResource(R.string.device_light_quick_setup_live_title),
            description = stringResource(R.string.device_light_quick_setup_live_description)
        )
        QuickSetupManagedPlanCard(context.productDisplayName, managed)
        state.livePlan?.let { plan -> QuickSetupLiveChart(plan, context.productKey) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AquaGuidedFlowButton(
                text = stringResource(R.string.device_light_quick_setup_edit_plan),
                onClick = { onAction(DeviceLightQuickSetupAction.Edit) },
                modifier = Modifier.weight(1f),
                secondary = true,
                singleLineCompact = true
            )
            AquaGuidedFlowButton(
                text = stringResource(R.string.device_light_quick_setup_disable_plan),
                onClick = { onAction(DeviceLightQuickSetupAction.DisablePlan) },
                modifier = Modifier.weight(1f),
                secondary = true,
                singleLineCompact = true
            )
        }
    }
}

@Composable
private fun QuickSetupRecommendationSummary(
    recommendation: DeviceLightQuickSetupRecommendation,
    currentPhase: Int
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_plan_summary_title),
                style = typography.title
            )
            QuickSetupTimingMetrics(recommendation, currentPhase)
            QuickSetupTargetMetrics(recommendation)
        }
    }
}

@Composable
private fun QuickSetupTimingMetrics(
    recommendation: DeviceLightQuickSetupRecommendation,
    currentPhase: Int
) {
    val phase = recommendation.phases[currentPhase]
    ReviewMetricRow(
        stringResource(R.string.device_light_quick_setup_current_phase_label),
        stringResource(
            R.string.device_light_quick_setup_phase_value,
            currentPhase + 1,
            recommendation.phases.size
        )
    )
    ReviewMetricRow(
        stringResource(R.string.device_light_quick_setup_today_window_label),
        stringResource(
            R.string.device_light_quick_setup_time_range,
            phase.startMinuteOfDay.toClockText(),
            phase.endMinuteOfDay.toClockText()
        )
    )
    ReviewMetricRow(
        stringResource(R.string.device_light_quick_setup_photoperiod_label),
        stringResource(
            R.string.device_light_quick_setup_hours_decimal,
            (phase.endMinuteOfDay - phase.startMinuteOfDay) / 60f
        )
    )
    ReviewMetricRow(
        stringResource(R.string.device_light_quick_setup_mature_duration_label),
        stringResource(R.string.device_light_quick_setup_hours_decimal, 8f)
    )
}

@Composable
private fun QuickSetupTargetMetrics(recommendation: DeviceLightQuickSetupRecommendation) {
    ReviewMetricRow(
        stringResource(R.string.device_light_quick_setup_target_ppfd_label),
        stringResource(
            R.string.device_light_quick_setup_ppfd_value,
            recommendation.requestedTargetPpfd
        )
    )
    ReviewMetricRow(
        stringResource(R.string.device_light_quick_setup_effective_ppfd_label),
        stringResource(
            R.string.device_light_quick_setup_ppfd_value,
            recommendation.effectiveTargetPpfd
        )
    )
    ReviewMetricRow(
        stringResource(R.string.device_light_quick_setup_plant_demand_label),
        stringResource(recommendation.plantProfile.highestDemand.demandResource())
    )
    ReviewMetricRow(
        stringResource(R.string.device_light_quick_setup_calibration_label),
        stringResource(R.string.device_light_quick_setup_calibrated)
    )
}

@Composable
private fun QuickSetupPhaseTimeline(
    phases: List<DeviceLightQuickSetupPhase>,
    currentPhase: Int
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_five_phase_title),
                style = typography.title
            )
            phases.forEachIndexed { index, phase ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(DeviceLightQuickSetupGeometry.timelineDotSize)
                            .background(
                                if (index == currentPhase) colors.accent else colors.outline,
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(9.dp))
                    BasicText(
                        text = stringResource(
                            R.string.device_light_quick_setup_phase_name,
                            index + 1
                        ),
                        style = typography.body,
                        modifier = Modifier.weight(1f)
                    )
                    BasicText(
                        text = stringResource(
                            R.string.device_light_quick_setup_time_range,
                            phase.startMinuteOfDay.toClockText(),
                            phase.endMinuteOfDay.toClockText()
                        ),
                        style = typography.caption
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSetupChannelScene(recommendation: DeviceLightQuickSetupRecommendation) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    val lightColors = aquaLightManualColors()
    val scene = recommendation.calibration.channelScenePercent
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_scene_title),
                style = typography.title
            )
            scene.forEach { (channel, value) ->
                val channelColor = when (channel) {
                    "red" -> lightColors.red
                    "green" -> lightColors.green
                    "blue" -> lightColors.blue
                    "white" -> lightColors.white
                    else -> colors.accent
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        text = channel.uppercase(),
                        style = typography.micro,
                        modifier = Modifier.width(44.dp)
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(DeviceLightQuickSetupGeometry.channelTrackHeight)
                            .background(colors.outline, DeviceLightQuickSetupGeometry.progressRadius)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(value / 100f)
                                .height(DeviceLightQuickSetupGeometry.channelTrackHeight)
                                .background(
                                    channelColor,
                                    DeviceLightQuickSetupGeometry.progressRadius
                                )
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        text = stringResource(R.string.device_light_quick_setup_percent, value),
                        style = typography.body,
                        modifier = Modifier.width(42.dp)
                    )
                }
            }
        }
    }
}


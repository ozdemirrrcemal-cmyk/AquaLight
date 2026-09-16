@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions", "LongParameterList")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightAmbientLevel
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightLifecycleStage
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanConfidence
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanWarning
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDensity
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPhase
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowGeometry
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowSurface
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowTypography
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowTypography
import com.aqua.aqualight.ui.common.light.AquaLightQuickSetupAlpha
import com.aqua.aqualight.ui.common.light.AquaLightQuickSetupGeometry

@Composable
internal fun DeviceLightQuickSetupScreen(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        QuickSetupProgress(state.step, colors)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = AquaLightQuickSetupGeometry.screenHorizontalPadding,
                end = AquaLightQuickSetupGeometry.screenHorizontalPadding,
                bottom = AquaLightQuickSetupGeometry.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.sectionGap)
        ) {
            item("heading") { StepHeading(state.step, typography) }
            when (state.step) {
                DeviceLightQuickSetupStep.TANK_DATA -> tankDataItems(state, actions, colors, typography)
                DeviceLightQuickSetupStep.PREFERENCES -> preferenceItems(state, actions, colors, typography)
                DeviceLightQuickSetupStep.PLAN -> planItems(state, colors, typography)
            }
        }
        BottomActions(state, actions)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.tankDataItems(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    item("tank-summary") { TankSummaryCard(state, colors, typography) }
    item("required-data") { RequiredDataCard(state, actions, colors, typography) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.preferenceItems(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    item("viewing-window") { ViewingWindowCard(state, actions, colors, typography) }
    item("fixture-profile") { FixtureProfileCard(state, actions, colors, typography) }
    item("ambient") { AmbientCard(state, actions, colors, typography) }
    item("co2") {
        ToggleCard(
            title = stringResource(R.string.device_light_quick_setup_co2_ready),
            body = stringResource(R.string.device_light_quick_setup_co2_ready_body),
            checked = state.co2Ready,
            onCheckedChange = actions.onCo2ReadyChanged,
            colors = colors,
            typography = typography
        )
    }
    item("soil") {
        ToggleCard(
            title = stringResource(R.string.device_light_quick_setup_soil_confirm),
            body = stringResource(R.string.device_light_quick_setup_soil_confirm_body),
            checked = state.activeSoil,
            onCheckedChange = actions.onActiveSoilChanged,
            colors = colors,
            typography = typography
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.planItems(
    state: DeviceLightQuickSetupUiState,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val plan = state.plan ?: return
    item("schedule") { ScheduleCard(plan.currentPhase, plan.confidence, colors, typography) }
    item("phases") { PhasePlanCard(plan.phases, colors, typography) }
    item("metrics") { MetricCards(plan.currentTargetPpfd, plan.currentEstimatedDliMolPerM2Day, colors, typography) }
    if (DeviceLightPlanWarning.PAR_NOT_MEASURED in plan.warnings) {
        item("estimate-warning") {
            InformationCard(
                stringResource(R.string.device_light_quick_setup_estimate_warning),
                colors,
                typography
            )
        }
    }
    item("reasons") { ReasonCard(plan.reasons.toList(), colors, typography) }
}

@Composable
private fun QuickSetupProgress(step: DeviceLightQuickSetupStep, colors: AquaGuidedFlowColors) {
    val selectedIndex = step.ordinal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AquaLightQuickSetupGeometry.screenHorizontalPadding,
                top = AquaLightQuickSetupGeometry.screenTopPadding,
                end = AquaLightQuickSetupGeometry.screenHorizontalPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.progressGap)
    ) {
        DeviceLightQuickSetupStep.entries.forEachIndexed { index, _ ->
            Box(
                Modifier
                    .weight(1f)
                    .height(AquaLightQuickSetupGeometry.progressHeight)
                    .clip(CircleShape)
                    .background(if (index <= selectedIndex) colors.accent else colors.outline)
            )
        }
    }
}

@Composable
private fun StepHeading(step: DeviceLightQuickSetupStep, typography: AquaGuidedFlowTypography) {
    val number = step.ordinal + 1
    val stepLabel = when (step) {
        DeviceLightQuickSetupStep.TANK_DATA -> R.string.device_light_quick_setup_step_tank
        DeviceLightQuickSetupStep.PREFERENCES -> R.string.device_light_quick_setup_step_preferences
        DeviceLightQuickSetupStep.PLAN -> R.string.device_light_quick_setup_step_plan
    }
    val title = when (step) {
        DeviceLightQuickSetupStep.TANK_DATA -> R.string.device_light_quick_setup_tank_title
        DeviceLightQuickSetupStep.PREFERENCES -> R.string.device_light_quick_setup_preferences_title
        DeviceLightQuickSetupStep.PLAN -> R.string.device_light_quick_setup_plan_title
    }
    val subtitle = when (step) {
        DeviceLightQuickSetupStep.TANK_DATA -> R.string.device_light_quick_setup_tank_subtitle
        DeviceLightQuickSetupStep.PREFERENCES -> R.string.device_light_quick_setup_preferences_subtitle
        DeviceLightQuickSetupStep.PLAN -> R.string.device_light_quick_setup_plan_subtitle
    }
    Column(
        modifier = Modifier.padding(
            top = AquaLightQuickSetupGeometry.titleTopPadding,
            bottom = AquaLightQuickSetupGeometry.tinyGap
        ),
        verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
    ) {
        BasicText(
            text = stringResource(
                R.string.device_light_quick_setup_step_heading,
                number,
                stringResource(stepLabel)
            ),
            style = typography.eyebrow
        )
        BasicText(text = stringResource(title), style = typography.title)
        BasicText(text = stringResource(subtitle), style = typography.body)
    }
}

@Composable
private fun TankSummaryCard(
    state: DeviceLightQuickSetupUiState,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val tank = state.tank ?: return
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
            BasicText(text = tank.tankName, style = typography.title.copy(fontSize = typography.body.fontSize))
            Row(
                horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.chipGap)
            ) {
                state.tankDay?.let { day ->
                    Tag(stringResource(R.string.device_light_quick_setup_day_badge, day), colors, typography)
                }
                Tag(plantDemandLabel(state.plantDemand), colors, typography)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.chipGap)
            ) {
                if (tank.inferredCo2Installed) {
                    Tag(stringResource(R.string.device_light_quick_setup_co2_detected), colors, typography)
                }
                if (tank.inferredActiveSoil) {
                    Tag(stringResource(R.string.device_light_quick_setup_active_soil), colors, typography)
                }
            }
        }
    }
}

@Composable
private fun RequiredDataCard(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val tank = state.tank ?: return
    val context = LocalContext.current
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.sectionGap)) {
            SectionTitle(
                stringResource(R.string.device_light_quick_setup_plan_inputs),
                stringResource(R.string.device_light_quick_setup_plan_inputs_body),
                typography
            )
            DataValueRow(
                stringResource(R.string.device_light_quick_setup_setup_date),
                tank.setupDateEpochDay?.let { LocaleFormatter.formatDateEpochDay(context, it) }
                    ?: stringResource(R.string.device_light_quick_setup_date_missing),
                colors,
                typography
            )
            LabeledSegmentedControl(
                title = stringResource(R.string.device_light_quick_setup_plant_demand),
                options = DeviceLightPlantDemand.entries,
                selected = state.plantDemand,
                label = { plantDemandLabel(it) },
                onSelected = actions.onPlantDemandChanged,
                colors = colors,
                typography = typography
            )
            LabeledSegmentedControl(
                title = stringResource(R.string.device_light_quick_setup_plant_density),
                options = DeviceLightPlantDensity.entries,
                selected = state.plantDensity,
                label = { plantDensityLabel(it) },
                onSelected = actions.onPlantDensityChanged,
                colors = colors,
                typography = typography
            )
            ValueStepper(
                title = stringResource(R.string.device_light_quick_setup_water_depth),
                value = stringResource(R.string.device_light_quick_setup_cm_value, state.waterDepthCm),
                onDecrease = { actions.onWaterDepthChanged(state.waterDepthCm - 1) },
                onIncrease = { actions.onWaterDepthChanged(state.waterDepthCm + 1) },
                colors = colors,
                typography = typography
            )
            val fixtureHeight = state.fixtureHeightCm
            ValueStepper(
                title = stringResource(R.string.device_light_quick_setup_fixture_height),
                value = fixtureHeight?.let {
                    stringResource(R.string.device_light_quick_setup_cm_value, it)
                } ?: stringResource(R.string.device_light_quick_setup_fixture_height_missing),
                onDecrease = { actions.onFixtureHeightChanged((fixtureHeight ?: 15) - 1) },
                onIncrease = { actions.onFixtureHeightChanged((fixtureHeight ?: 14) + 1) },
                colors = colors,
                typography = typography,
                warning = fixtureHeight == null
            )
        }
    }
}

@Composable
private fun ViewingWindowCard(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val context = LocalContext.current
    val durationMinutes = lifecycleDuration(state.tankDay ?: 1L)
    val end = state.preferredLightsOffMinute
    val range = stringResource(
        R.string.device_light_quick_setup_time_range,
        LocaleFormatter.formatTimeOfDay24Hour(context, end - durationMinutes),
        LocaleFormatter.formatTimeOfDay24Hour(context, end)
    )
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
            SectionTitle(
                stringResource(R.string.device_light_quick_setup_viewing_window),
                stringResource(R.string.device_light_quick_setup_viewing_window_body),
                typography
            )
            ValueStepper(
                title = stringResource(R.string.device_light_quick_setup_today),
                value = range,
                onDecrease = { actions.onLightsOffMinuteChanged(end - 30) },
                onIncrease = { actions.onLightsOffMinuteChanged(end + 30) },
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun FixtureProfileCard(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val tank = state.tank ?: return
    val fixtureHeight = state.fixtureHeightCm ?: return
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
            SectionTitle(
                stringResource(R.string.device_light_quick_setup_light_profile),
                tank.productDisplayName,
                typography
            )
            BasicText(
                text = stringResource(
                    R.string.device_light_quick_setup_profile_geometry,
                    state.waterDepthCm,
                    fixtureHeight
                ),
                style = typography.body
            )
            Tag(
                text = stringResource(
                    if (state.measuredFullProfilePpfd == null) {
                        R.string.device_light_quick_setup_par_estimate
                    } else {
                        R.string.device_light_quick_setup_par_measured
                    }
                ),
                colors = colors,
                typography = typography
            )
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_par_help),
                style = typography.body
            )
            state.measuredFullProfilePpfd?.let { ppfd ->
                ValueStepper(
                    title = stringResource(R.string.device_light_quick_setup_par_measured),
                    value = stringResource(R.string.device_light_quick_setup_ppfd_value, ppfd),
                    onDecrease = { actions.onMeasuredPpfdChanged(ppfd - 5) },
                    onIncrease = { actions.onMeasuredPpfdChanged(ppfd + 5) },
                    colors = colors,
                    typography = typography
                )
            }
            TextAction(
                text = stringResource(
                    if (state.measuredFullProfilePpfd == null) {
                        R.string.device_light_quick_setup_use_measurement
                    } else {
                        R.string.device_light_quick_setup_use_estimate
                    }
                ),
                onClick = {
                    actions.onMeasuredPpfdChanged(
                        if (state.measuredFullProfilePpfd == null) 100 else null
                    )
                },
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun AmbientCard(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
            SectionTitle(
                stringResource(R.string.device_light_quick_setup_ambient_light),
                stringResource(R.string.device_light_quick_setup_ambient_light_body),
                typography
            )
            SegmentedControl(
                options = DeviceLightAmbientLevel.entries,
                selected = state.ambientLevel,
                label = { ambientLabel(it) },
                onSelected = actions.onAmbientLevelChanged,
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    AquaGuidedFlowSurface(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
            ) {
                BasicText(text = title, style = typography.label)
                BasicText(text = body, style = typography.body)
            }
            Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
            AquaSwitch(checked, colors)
        }
    }
}

@Composable
private fun ScheduleCard(
    phase: DeviceLightQuickSetupPhase,
    confidence: DeviceLightPlanConfidence,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val context = LocalContext.current
    val startMinute = (phase.draft.startTimeMs / 60_000L).toInt()
    val endMinute = (phase.draft.endTimeMs / 60_000L).toInt()
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = stringResource(R.string.device_light_quick_setup_today),
                    style = typography.label,
                    modifier = Modifier.weight(1f)
                )
                Tag(
                    text = stringResource(
                        if (confidence == DeviceLightPlanConfidence.CALIBRATED) {
                            R.string.device_light_quick_setup_confidence_calibrated
                        } else {
                            R.string.device_light_quick_setup_confidence_estimated
                        }
                    ),
                    colors = colors,
                    typography = typography
                )
            }
            BasicText(
                text = stringResource(
                    R.string.device_light_quick_setup_time_range,
                    LocaleFormatter.formatTimeOfDay24Hour(context, startMinute),
                    LocaleFormatter.formatTimeOfDay24Hour(context, endMinute)
                ),
                style = typography.metric.copy(textAlign = TextAlign.Start)
            )
            BasicText(
                text = stringResource(
                    R.string.device_light_quick_setup_hours,
                    LocaleFormatter.formatDecimal(context, (endMinute - startMinute) / 60.0, 1)
                ),
                style = typography.label
            )
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_ramp_summary),
                style = typography.body
            )
            ScheduleGraph(colors)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(
                    text = LocaleFormatter.formatTimeOfDay24Hour(context, startMinute),
                    style = typography.body
                )
                BasicText(
                    text = LocaleFormatter.formatTimeOfDay24Hour(context, startMinute + 60),
                    style = typography.body
                )
                BasicText(
                    text = LocaleFormatter.formatTimeOfDay24Hour(context, endMinute - 60),
                    style = typography.body
                )
                BasicText(
                    text = LocaleFormatter.formatTimeOfDay24Hour(context, endMinute),
                    style = typography.body
                )
            }
        }
    }
}

@Composable
private fun ScheduleGraph(colors: AquaGuidedFlowColors) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(AquaLightQuickSetupGeometry.graphHeight)
    ) {
        val bottom = size.height
        val top = size.height * 0.18f
        val rampEnd = size.width * 0.22f
        val rampStart = size.width * 0.78f
        val fillPath = Path().apply {
            moveTo(0f, bottom)
            lineTo(rampEnd, top)
            lineTo(rampStart, top)
            lineTo(size.width, bottom)
            close()
        }
        drawPath(fillPath, colors.accent.copy(alpha = AquaLightQuickSetupAlpha.GRAPH_FILL))
        val linePath = Path().apply {
            moveTo(0f, bottom)
            lineTo(rampEnd, top)
            lineTo(rampStart, top)
            lineTo(size.width, bottom)
        }
        drawPath(
            linePath,
            colors.accent,
            style = Stroke(width = AquaLightQuickSetupGeometry.graphLineWidth.toPx())
        )
        listOf(0f, rampEnd, rampStart, size.width).forEach { x ->
            drawLine(
                colors.outline.copy(alpha = AquaLightQuickSetupAlpha.GUIDE),
                Offset(x, 0f),
                Offset(x, bottom),
                AquaLightQuickSetupGeometry.graphGuideLineWidth.toPx()
            )
        }
    }
}

@Composable
private fun PhasePlanCard(
    phases: List<DeviceLightQuickSetupPhase>,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val context = LocalContext.current
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_gradual_plan),
                style = typography.label
            )
            phases.forEach { phase ->
                val next = DeviceLightLifecycleStage.entries.getOrNull(phase.lifecycleStage.ordinal + 1)
                val days = if (next == null) {
                    stringResource(
                        R.string.device_light_quick_setup_phase_days_open,
                        phase.lifecycleStage.dayStart
                    )
                } else {
                    stringResource(
                        R.string.device_light_quick_setup_phase_days,
                        phase.lifecycleStage.dayStart,
                        next.dayStart - 1
                    )
                }
                val hours = phase.lifecycleStage.durationMinutes / 60.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = days,
                        modifier = Modifier.width(AquaGuidedFlowGeometry.minimumTouchTarget * 2),
                        style = typography.body
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(AquaLightQuickSetupGeometry.phaseBarHeight)
                            .clip(CircleShape)
                            .background(colors.outline)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(phase.lifecycleStage.durationMinutes / 480f)
                                .height(AquaLightQuickSetupGeometry.phaseBarHeight)
                                .background(colors.accent)
                        )
                    }
                    Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
                    BasicText(
                        text = stringResource(
                            R.string.device_light_quick_setup_hours,
                            LocaleFormatter.formatDecimal(context, hours, 1)
                        ),
                        style = typography.label
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCards(
    targetPpfd: Int,
    dli: Double,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
        MetricCard(
            title = stringResource(R.string.device_light_quick_setup_metric_target),
            value = stringResource(R.string.device_light_quick_setup_ppfd_value, targetPpfd),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = stringResource(R.string.device_light_quick_setup_metric_dli),
            value = stringResource(
                R.string.device_light_quick_setup_dli_value,
                LocaleFormatter.formatDecimal(context, dli, 2)
            ),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(AquaGuidedFlowGeometry.cardRadius)
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = AquaLightQuickSetupGeometry.metricCardMinHeight)
            .clip(shape)
            .background(colors.surface)
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.outline, shape)
            .padding(AquaLightQuickSetupGeometry.cardPadding),
        verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
    ) {
        BasicText(text = title, style = typography.body)
        BasicText(text = value, style = typography.label.copy(color = colors.accent))
    }
}

@Composable
private fun ReasonCard(
    reasons: List<DeviceLightPlanReason>,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_why),
                style = typography.label
            )
            reasons.forEach { reason ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(AquaLightQuickSetupGeometry.badgeSize)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE)),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = stringResource(R.string.device_light_quick_setup_check_mark),
                            style = typography.label.copy(color = colors.accent)
                        )
                    }
                    Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
                    BasicText(text = stringResource(reason.labelRes()), style = typography.body)
                }
            }
        }
    }
}

@Composable
private fun InformationCard(
    text: String,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val shape = RoundedCornerShape(AquaGuidedFlowGeometry.controlRadius)
    BasicText(
        text = text,
        style = typography.body.copy(color = colors.textPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.accent.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE))
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.accent, shape)
            .padding(AquaLightQuickSetupGeometry.cardPadding)
    )
}

@Composable
private fun BottomActions(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AquaLightQuickSetupGeometry.screenHorizontalPadding,
                top = AquaLightQuickSetupGeometry.actionTopPadding,
                end = AquaLightQuickSetupGeometry.screenHorizontalPadding,
                bottom = AquaLightQuickSetupGeometry.screenBottomPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
    ) {
        if (state.step != DeviceLightQuickSetupStep.TANK_DATA) {
            AquaGuidedFlowButton(
                text = stringResource(R.string.device_light_quick_setup_back),
                onClick = actions.onBackStep,
                modifier = Modifier.weight(1f),
                enabled = !state.applying,
                secondary = true,
                singleLineCompact = true
            )
        }
        val label = when (state.step) {
            DeviceLightQuickSetupStep.TANK_DATA -> R.string.device_light_quick_setup_continue
            DeviceLightQuickSetupStep.PREFERENCES -> R.string.device_light_quick_setup_calculate
            DeviceLightQuickSetupStep.PLAN -> R.string.device_light_quick_setup_apply
        }
        val enabled = when (state.step) {
            DeviceLightQuickSetupStep.TANK_DATA -> state.canContinueTankData
            DeviceLightQuickSetupStep.PREFERENCES -> state.canCalculate
            DeviceLightQuickSetupStep.PLAN -> state.canApply
        }
        val action = when (state.step) {
            DeviceLightQuickSetupStep.TANK_DATA -> actions.onContinue
            DeviceLightQuickSetupStep.PREFERENCES -> actions.onCalculate
            DeviceLightQuickSetupStep.PLAN -> actions.onApply
        }
        AquaGuidedFlowButton(
            text = stringResource(label),
            onClick = action,
            modifier = Modifier.weight(if (state.step == DeviceLightQuickSetupStep.TANK_DATA) 1f else 2f),
            enabled = enabled,
            singleLineCompact = true
        )
    }
}

@Composable
private fun SectionTitle(title: String, body: String, typography: AquaGuidedFlowTypography) {
    Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)) {
        BasicText(text = title, style = typography.label)
        BasicText(text = body, style = typography.body)
    }
}

@Composable
private fun DataValueRow(
    title: String,
    value: String,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AquaLightQuickSetupGeometry.tinyGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(text = title, modifier = Modifier.weight(1f), style = typography.body)
        BasicText(text = value, style = typography.label.copy(color = colors.accent))
    }
}

@Composable
private fun <T> LabeledSegmentedControl(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
        BasicText(text = title, style = typography.label)
        SegmentedControl(options, selected, label, onSelected, colors, typography)
    }
}

@Composable
private fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AquaLightQuickSetupGeometry.segmentedShape)
            .background(colors.surfaceRaised)
            .border(
                AquaGuidedFlowGeometry.outlineWidth,
                colors.outline,
                AquaLightQuickSetupGeometry.segmentedShape
            )
            .padding(AquaLightQuickSetupGeometry.segmentedPadding)
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(AquaLightQuickSetupGeometry.chipShape)
                    .background(if (active) colors.accent else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelected(option) }
                    .padding(vertical = AquaLightQuickSetupGeometry.segmentedItemPadding),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = label(option),
                    style = typography.label.copy(
                        color = if (active) colors.onAccent else colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

@Composable
private fun ValueStepper(
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography,
    warning: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = title, style = typography.body)
            BasicText(
                text = value,
                style = typography.label.copy(
                    color = if (warning) colors.danger else colors.textPrimary
                )
            )
        }
        StepButton(
            stringResource(R.string.device_light_quick_setup_minus),
            stringResource(R.string.device_light_quick_setup_decrease, title),
            onDecrease,
            colors,
            typography
        )
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.tinyGap))
        StepButton(
            stringResource(R.string.device_light_quick_setup_plus),
            stringResource(R.string.device_light_quick_setup_increase, title),
            onIncrease,
            colors,
            typography
        )
    }
}

@Composable
private fun StepButton(
    text: String,
    description: String,
    onClick: () -> Unit,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    Box(
        modifier = Modifier
            .size(AquaLightQuickSetupGeometry.controlButtonSize)
            .semantics { contentDescription = description }
            .clip(AquaLightQuickSetupGeometry.chipShape)
            .background(colors.surfaceRaised)
            .border(
                AquaGuidedFlowGeometry.outlineWidth,
                colors.outline,
                AquaLightQuickSetupGeometry.chipShape
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text = text, style = typography.title.copy(fontSize = typography.body.fontSize))
    }
}

@Composable
private fun TextAction(
    text: String,
    onClick: () -> Unit,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    BasicText(
        text = text,
        style = typography.label.copy(color = colors.accent),
        modifier = Modifier
            .clip(AquaLightQuickSetupGeometry.chipShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = AquaLightQuickSetupGeometry.chipHorizontalPadding,
                vertical = AquaLightQuickSetupGeometry.chipVerticalPadding
            )
    )
}

@Composable
private fun Tag(
    text: String,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    BasicText(
        text = text,
        style = typography.eyebrow.copy(color = colors.accent),
        modifier = Modifier
            .clip(AquaLightQuickSetupGeometry.chipShape)
            .background(colors.accent.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE))
            .padding(
                horizontal = AquaLightQuickSetupGeometry.chipHorizontalPadding,
                vertical = AquaLightQuickSetupGeometry.chipVerticalPadding
            )
    )
}

@Composable
private fun AquaSwitch(checked: Boolean, colors: AquaGuidedFlowColors) {
    Box(
        Modifier
            .width(AquaLightQuickSetupGeometry.switchWidth)
            .height(AquaLightQuickSetupGeometry.switchHeight)
            .clip(CircleShape)
            .background(if (checked) colors.accent else colors.outline)
            .padding(AquaLightQuickSetupGeometry.switchInset)
    ) {
        Box(
            Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(AquaLightQuickSetupGeometry.switchThumb)
                .clip(CircleShape)
                .background(if (checked) colors.onAccent else colors.textSecondary)
        )
    }
}

@Composable
private fun plantDemandLabel(value: DeviceLightPlantDemand): String = stringResource(
    when (value) {
        DeviceLightPlantDemand.LOW -> R.string.device_light_quick_setup_low
        DeviceLightPlantDemand.MEDIUM -> R.string.device_light_quick_setup_medium
        DeviceLightPlantDemand.HIGH -> R.string.device_light_quick_setup_high
    }
)

@Composable
private fun plantDensityLabel(value: DeviceLightPlantDensity): String = stringResource(
    when (value) {
        DeviceLightPlantDensity.SPARSE -> R.string.device_light_quick_setup_sparse
        DeviceLightPlantDensity.MEDIUM -> R.string.device_light_quick_setup_medium
        DeviceLightPlantDensity.DENSE -> R.string.device_light_quick_setup_dense
    }
)

@Composable
private fun ambientLabel(value: DeviceLightAmbientLevel): String = stringResource(
    when (value) {
        DeviceLightAmbientLevel.LOW -> R.string.device_light_quick_setup_low
        DeviceLightAmbientLevel.MEDIUM -> R.string.device_light_quick_setup_medium
        DeviceLightAmbientLevel.HIGH -> R.string.device_light_quick_setup_high
    }
)

private fun lifecycleDuration(tankDay: Long): Int =
    DeviceLightLifecycleStage.entries.last { tankDay >= it.dayStart }.durationMinutes

private fun DeviceLightPlanReason.labelRes(): Int = when (this) {
    DeviceLightPlanReason.NEW_TANK -> R.string.device_light_quick_setup_reason_new_tank
    DeviceLightPlanReason.ESTABLISHED_TANK -> R.string.device_light_quick_setup_reason_established
    DeviceLightPlanReason.LOW_LIGHT_PLANTS -> R.string.device_light_quick_setup_reason_low_plants
    DeviceLightPlanReason.MEDIUM_LIGHT_PLANTS -> R.string.device_light_quick_setup_reason_medium_plants
    DeviceLightPlanReason.HIGH_LIGHT_PLANTS -> R.string.device_light_quick_setup_reason_high_plants
    DeviceLightPlanReason.CO2_CONFIRMED -> R.string.device_light_quick_setup_reason_co2
    DeviceLightPlanReason.NO_CO2_SAFETY_CAP -> R.string.device_light_quick_setup_reason_no_co2
    DeviceLightPlanReason.ACTIVE_SOIL_STARTUP -> R.string.device_light_quick_setup_reason_soil
    DeviceLightPlanReason.AMBIENT_LIGHT_COMPENSATION -> R.string.device_light_quick_setup_reason_ambient
    DeviceLightPlanReason.MEASURED_PAR -> R.string.device_light_quick_setup_reason_measured_par
    DeviceLightPlanReason.ESTIMATED_PAR -> R.string.device_light_quick_setup_reason_estimated_par
}

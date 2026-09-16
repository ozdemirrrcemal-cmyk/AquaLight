@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions", "LongParameterList")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightLifecycleStage
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanWarning
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDensity
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPhase
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlan
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowGeometry
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowSurface
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowTypography
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowTypography
import com.aqua.aqualight.ui.common.light.AquaLightManualColors
import com.aqua.aqualight.ui.common.light.AquaLightQuickSetupAlpha
import com.aqua.aqualight.ui.common.light.AquaLightQuickSetupGeometry
import com.aqua.aqualight.ui.common.light.aquaLightManualColors

@Composable
internal fun DeviceLightQuickSetupScreen(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaGuidedFlowColors()
    val typography = aquaGuidedFlowTypography(colors)
    val channelColors = aquaLightManualColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = AquaLightQuickSetupGeometry.screenHorizontalPadding,
                top = AquaLightQuickSetupGeometry.screenTopPadding,
                end = AquaLightQuickSetupGeometry.screenHorizontalPadding,
                bottom = AquaLightQuickSetupGeometry.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.sectionGap)
        ) {
            val tank = state.tank
            val plan = state.plan
            if (tank != null && plan != null) {
                item("hero") { AutomationHero(state, colors, typography) }
                item("profile") { AquariumProfileCard(state, colors, typography) }
                item("program") {
                    RecommendedProgramCard(
                        plan = plan,
                        colors = colors,
                        channelColors = channelColors,
                        typography = typography
                    )
                }
                item("safe-output") {
                    SafeOutputCard(plan, colors, channelColors, typography)
                }
                item("reasons") { ReasonCard(plan, colors, typography) }
                item("adaptation") { AdaptationCard(plan, colors, typography) }
                if (state.hasBlockingWarning) {
                    item("blocked") { BlockingWarningCard(colors, typography) }
                }
                if (state.detailsExpanded) {
                    item("details-title") {
                        BasicText(
                            text = stringResource(R.string.device_light_quick_setup_technical_details),
                            style = typography.label
                        )
                    }
                    item("phases") { PhasePlanCard(plan.phases, colors, typography) }
                    item("metrics") {
                        MetricCards(
                            targetPpfd = plan.currentTargetPpfd,
                            dli = plan.currentEstimatedDliMolPerM2Day,
                            colors = colors,
                            typography = typography
                        )
                    }
                    if (DeviceLightPlanWarning.PAR_NOT_MEASURED in plan.warnings) {
                        item("estimate") {
                            InformationCard(
                                text = stringResource(
                                    R.string.device_light_quick_setup_estimate_warning
                                ),
                                colors = colors,
                                typography = typography
                            )
                        }
                    }
                }
            }
        }
        BottomActions(state, actions)
    }
}

@Composable
private fun AutomationHero(
    state: DeviceLightQuickSetupUiState,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val tank = state.tank ?: return
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(AquaLightQuickSetupGeometry.automationHeroIconSize)
                    .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.automationHeroIconRadius))
                    .background(colors.accent.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_light_quick_setup),
                    contentDescription = null,
                    modifier = Modifier.size(AquaLightQuickSetupGeometry.automationHeroGlyphSize),
                    colorFilter = ColorFilter.tint(colors.accent)
                )
            }
            Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
            ) {
                BasicText(
                    text = stringResource(R.string.device_light_quick_setup_smart_ready_title),
                    style = typography.label
                )
                BasicText(
                    text = heroSubtitle(state, tank.tankName),
                    style = typography.body
                )
            }
            Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
            StatusTag(
                text = stringResource(
                    if (state.hasBlockingWarning) {
                        R.string.device_light_quick_setup_profile_incomplete
                    } else {
                        R.string.device_light_quick_setup_profile_ready
                    }
                ),
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun heroSubtitle(state: DeviceLightQuickSetupUiState, tankName: String): String =
    state.tankDay?.let { day ->
        stringResource(R.string.device_light_quick_setup_tank_day_summary, tankName, day)
    } ?: tankName

@Composable
private fun AquariumProfileCard(
    state: DeviceLightQuickSetupUiState,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val tank = state.tank ?: return
    val plantCount = pluralStringResource(
        R.plurals.device_light_quick_setup_plant_count,
        tank.plantCount,
        tank.plantCount
    )
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_aquarium_profile),
                style = typography.label
            )
            Spacer(Modifier.height(AquaLightQuickSetupGeometry.compactGap))
            ProfileRow(
                iconRes = R.drawable.ic_care_plant_health_24,
                label = stringResource(R.string.device_light_quick_setup_profile_planting),
                value = "$plantCount · ${plantDensityLabel(tank.inferredPlantDensity)}",
                colors = colors,
                typography = typography
            )
            ProfileDivider(colors)
            ProfileRow(
                iconRes = R.drawable.ic_light_preset_planted,
                label = stringResource(R.string.device_light_quick_setup_plant_demand),
                value = plantDemandLabel(tank.inferredPlantDemand),
                colors = colors,
                typography = typography
            )
            ProfileDivider(colors)
            ProfileRow(
                iconRes = R.drawable.ic_care_co2_24,
                label = stringResource(R.string.device_light_quick_setup_profile_co2),
                value = automaticProfileValue(tank.inferredCo2Installed),
                colors = colors,
                typography = typography
            )
            ProfileDivider(colors)
            ProfileRow(
                iconRes = R.drawable.ic_care_substrate_24,
                label = stringResource(R.string.device_light_quick_setup_profile_substrate),
                value = if (tank.inferredActiveSoil) {
                    stringResource(R.string.device_light_quick_setup_active_soil)
                } else {
                    stringResource(R.string.device_light_quick_setup_profile_not_configured)
                },
                colors = colors,
                typography = typography
            )
            ProfileDivider(colors)
            ProfileRow(
                iconRes = R.drawable.ic_care_light_24,
                label = stringResource(R.string.device_light_quick_setup_profile_light),
                value = tank.productDisplayName,
                colors = colors,
                typography = typography
            )
            ProfileDivider(colors)
            ProfileRow(
                iconRes = R.drawable.ic_care_water_test_24,
                label = stringResource(R.string.device_light_quick_setup_aquarium_height),
                value = stringResource(R.string.device_light_quick_setup_cm_value, tank.heightCm),
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun automaticProfileValue(active: Boolean): String = stringResource(
    if (active) {
        R.string.device_light_quick_setup_profile_active
    } else {
        R.string.device_light_quick_setup_profile_not_configured
    }
)

@Composable
private fun ProfileRow(
    @DrawableRes iconRes: Int,
    label: String,
    value: String,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AquaLightQuickSetupGeometry.profileRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(AquaLightQuickSetupGeometry.profileIconSize),
            colorFilter = ColorFilter.tint(colors.accent)
        )
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        BasicText(text = label, style = typography.body, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        BasicText(
            text = value,
            style = typography.label,
            modifier = Modifier.weight(PROFILE_VALUE_WEIGHT),
            maxLines = PROFILE_VALUE_MAX_LINES
        )
    }
}

@Composable
private fun ProfileDivider(colors: AquaGuidedFlowColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(AquaLightQuickSetupGeometry.dividerHeight)
            .background(colors.outline.copy(alpha = AquaLightQuickSetupAlpha.OUTLINE))
    )
}

@Composable
private fun RecommendedProgramCard(
    plan: DeviceLightQuickSetupPlan,
    colors: AquaGuidedFlowColors,
    channelColors: AquaLightManualColors,
    typography: AquaGuidedFlowTypography
) {
    val phase = plan.currentPhase
    val context = LocalContext.current
    val startMinute = (phase.draft.startTimeMs / MINUTE_MS).toInt()
    val endMinute = (phase.draft.endTimeMs / MINUTE_MS).toInt()
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_care_light_24),
                    contentDescription = null,
                    modifier = Modifier.size(AquaLightQuickSetupGeometry.profileIconSize),
                    colorFilter = ColorFilter.tint(colors.accent)
                )
                Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
                BasicText(
                    text = stringResource(R.string.device_light_quick_setup_recommended_program),
                    style = typography.label,
                    modifier = Modifier.weight(1f)
                )
                StatusTag(
                    text = stringResource(R.string.device_light_quick_setup_automatic_plan),
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
            ProgramChart(phase, colors, channelColors, typography)
            ProgramSummary(phase, colors, typography)
        }
    }
}

@Composable
private fun ProgramChart(
    phase: DeviceLightQuickSetupPhase,
    colors: AquaGuidedFlowColors,
    channelColors: AquaLightManualColors,
    typography: AquaGuidedFlowTypography
) {
    val description = stringResource(R.string.device_light_quick_setup_chart_description)
    val shape = RoundedCornerShape(AquaGuidedFlowGeometry.controlRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.outline, shape)
            .padding(AquaLightQuickSetupGeometry.chartPadding)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(AquaLightQuickSetupGeometry.programChartHeight)
                .semantics { contentDescription = description }
        ) {
            val bottom = size.height
            repeat(CHART_GRID_DIVISIONS + 1) { index ->
                val fraction = index.toFloat() / CHART_GRID_DIVISIONS
                drawLine(
                    color = colors.outline.copy(alpha = AquaLightQuickSetupAlpha.GUIDE),
                    start = Offset(size.width * fraction, 0f),
                    end = Offset(size.width * fraction, bottom),
                    strokeWidth = AquaLightQuickSetupGeometry.chartGridWidth.toPx()
                )
                drawLine(
                    color = colors.outline.copy(alpha = AquaLightQuickSetupAlpha.GUIDE),
                    start = Offset(0f, bottom * fraction),
                    end = Offset(size.width, bottom * fraction),
                    strokeWidth = AquaLightQuickSetupGeometry.chartGridWidth.toPx()
                )
            }
            phase.draft.scene.channels.forEach { (channel, percent) ->
                val startMinute = (phase.draft.startTimeMs / MINUTE_MS).toFloat()
                val endMinute = (phase.draft.endTimeMs / MINUTE_MS).toFloat()
                val rampMinutes = (phase.draft.rampDurationMs / MINUTE_MS).toFloat()
                val startX = size.width * startMinute / MINUTES_PER_DAY
                val fullStartX = size.width * (startMinute + rampMinutes) / MINUTES_PER_DAY
                val fullEndX = size.width * (endMinute - rampMinutes) / MINUTES_PER_DAY
                val endX = size.width * endMinute / MINUTES_PER_DAY
                val outputY = bottom * (1f - percent / PERCENT_SCALE)
                val path = Path().apply {
                    moveTo(0f, bottom)
                    lineTo(startX, bottom)
                    lineTo(fullStartX, outputY)
                    lineTo(fullEndX, outputY)
                    lineTo(endX, bottom)
                    lineTo(size.width, bottom)
                }
                drawPath(
                    path = path,
                    color = channel.chartColor(channelColors),
                    style = Stroke(
                        width = AquaLightQuickSetupGeometry.chartLineWidth.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
        }
        Spacer(Modifier.height(AquaLightQuickSetupGeometry.tinyGap))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CHART_HOUR_LABELS.forEach { hour ->
                BasicText(text = hour, style = typography.body)
            }
        }
        Spacer(Modifier.height(AquaLightQuickSetupGeometry.compactGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            phase.draft.scene.channels.keys.forEach { channel ->
                ChartLegendItem(channel, channelColors, typography)
            }
        }
    }
}

@Composable
private fun ChartLegendItem(
    channel: DeviceLightAutomaticChannel,
    channelColors: AquaLightManualColors,
    typography: AquaGuidedFlowTypography
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(AquaLightQuickSetupGeometry.channelDotSize)
                .clip(CircleShape)
                .background(channel.chartColor(channelColors))
        )
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.tinyGap))
        BasicText(text = stringResource(channel.shortLabelRes()), style = typography.body)
    }
}

@Composable
private fun ProgramSummary(
    phase: DeviceLightQuickSetupPhase,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val context = LocalContext.current
    val durationMinutes = ((phase.draft.endTimeMs - phase.draft.startTimeMs) / MINUTE_MS).toInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
    ) {
        SummaryMetric(
            value = stringResource(
                R.string.device_light_quick_setup_hours_short,
                LocaleFormatter.formatDecimal(context, durationMinutes / 60.0, 1)
            ),
            label = stringResource(R.string.device_light_quick_setup_total_time),
            colors = colors,
            typography = typography
        )
        SummaryMetric(
            value = stringResource(
                R.string.device_light_quick_setup_minutes_short,
                phase.draft.rampDurationMs / MINUTE_MS
            ),
            label = stringResource(R.string.device_light_quick_setup_soft_transition),
            colors = colors,
            typography = typography
        )
        SummaryMetric(
            value = stringResource(R.string.device_light_quick_setup_every_day),
            label = stringResource(R.string.device_light_quick_setup_automatic_repeat),
            colors = colors,
            typography = typography
        )
    }
}

@Composable
private fun RowScope.SummaryMetric(
    value: String,
    label: String,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(AquaGuidedFlowGeometry.controlRadius))
            .background(colors.surfaceRaised)
            .padding(AquaLightQuickSetupGeometry.summaryMetricPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
    ) {
        BasicText(text = value, style = typography.label, maxLines = 1)
        BasicText(
            text = label,
            style = typography.body.copy(textAlign = TextAlign.Center),
            maxLines = SUMMARY_LABEL_MAX_LINES
        )
    }
}

@Composable
private fun SafeOutputCard(
    plan: DeviceLightQuickSetupPlan,
    colors: AquaGuidedFlowColors,
    channelColors: AquaLightManualColors,
    typography: AquaGuidedFlowTypography
) {
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_safe_output),
                style = typography.label
            )
            Row(Modifier.fillMaxWidth()) {
                plan.scene.channels.forEach { (channel, value) ->
                    ChannelOutput(channel, value, channelColors, typography)
                }
            }
            ProfileDivider(colors)
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_safe_output_body),
                style = typography.body
            )
        }
    }
}

@Composable
private fun RowScope.ChannelOutput(
    channel: DeviceLightAutomaticChannel,
    value: Int,
    channelColors: AquaLightManualColors,
    typography: AquaGuidedFlowTypography
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(AquaLightQuickSetupGeometry.channelOutputDotSize)
                    .clip(CircleShape)
                    .background(channel.chartColor(channelColors))
            )
            Spacer(Modifier.width(AquaLightQuickSetupGeometry.tinyGap))
            BasicText(text = stringResource(channel.shortLabelRes()), style = typography.body)
        }
        BasicText(
            text = stringResource(R.string.device_light_quick_setup_percent_value, value),
            style = typography.label
        )
    }
}

@Composable
private fun ReasonCard(
    plan: DeviceLightQuickSetupPlan,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val visibleReasons = plan.reasons.filterNot { it == DeviceLightPlanReason.ESTIMATED_PAR }
    AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_why),
                style = typography.label
            )
            visibleReasons.forEach { reason ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(AquaLightQuickSetupGeometry.reasonBadgeSize)
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
private fun AdaptationCard(
    plan: DeviceLightQuickSetupPlan,
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val shape = RoundedCornerShape(AquaGuidedFlowGeometry.controlRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.accent.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE))
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.accent, shape)
            .padding(AquaLightQuickSetupGeometry.cardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_check_24),
            contentDescription = null,
            modifier = Modifier.size(AquaLightQuickSetupGeometry.adaptationIconSize),
            colorFilter = ColorFilter.tint(colors.accent)
        )
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_adaptation_title),
                style = typography.label.copy(color = colors.accent)
            )
            BasicText(
                text = stringResource(
                    if (plan.phases.size > 1) {
                        R.string.device_light_quick_setup_adaptation_gradual_body
                    } else {
                        R.string.device_light_quick_setup_adaptation_mature_body
                    }
                ),
                style = typography.body.copy(color = colors.textPrimary)
            )
        }
    }
}

@Composable
private fun BlockingWarningCard(
    colors: AquaGuidedFlowColors,
    typography: AquaGuidedFlowTypography
) {
    val shape = RoundedCornerShape(AquaGuidedFlowGeometry.controlRadius)
    BasicText(
        text = stringResource(R.string.device_light_quick_setup_profile_blocked),
        style = typography.body.copy(color = colors.textPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.danger.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE))
            .border(AquaGuidedFlowGeometry.outlineWidth, colors.danger, shape)
            .padding(AquaLightQuickSetupGeometry.cardPadding)
    )
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
                val next = DeviceLightLifecycleStage.entries.getOrNull(
                    phase.lifecycleStage.ordinal + 1
                )
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
                        modifier = Modifier.width(AquaLightQuickSetupGeometry.phaseLabelWidth),
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
            .background(aquaGuidedFlowColors().background)
            .padding(
                start = AquaLightQuickSetupGeometry.screenHorizontalPadding,
                top = AquaLightQuickSetupGeometry.actionTopPadding,
                end = AquaLightQuickSetupGeometry.screenHorizontalPadding,
                bottom = AquaLightQuickSetupGeometry.screenBottomPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
    ) {
        AquaGuidedFlowButton(
            text = stringResource(
                if (state.detailsExpanded) {
                    R.string.device_light_quick_setup_hide_details
                } else {
                    R.string.device_light_quick_setup_review_details
                }
            ),
            onClick = actions.onToggleDetails,
            modifier = Modifier.weight(1f),
            enabled = state.plan != null && !state.applying,
            secondary = true,
            singleLineCompact = true
        )
        AquaGuidedFlowButton(
            text = stringResource(R.string.device_light_quick_setup_create_program),
            onClick = actions.onApply,
            modifier = Modifier.weight(PRIMARY_ACTION_WEIGHT),
            enabled = state.canApply,
            singleLineCompact = true
        )
    }
}

@Composable
private fun StatusTag(
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
            ),
        maxLines = 1
    )
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

@StringRes
private fun DeviceLightPlanReason.labelRes(): Int = when (this) {
    DeviceLightPlanReason.NEW_TANK -> R.string.device_light_quick_setup_reason_new_tank
    DeviceLightPlanReason.ESTABLISHED_TANK -> R.string.device_light_quick_setup_reason_established
    DeviceLightPlanReason.LOW_LIGHT_PLANTS -> R.string.device_light_quick_setup_reason_low_plants
    DeviceLightPlanReason.MEDIUM_LIGHT_PLANTS -> R.string.device_light_quick_setup_reason_medium_plants
    DeviceLightPlanReason.HIGH_LIGHT_PLANTS -> R.string.device_light_quick_setup_reason_high_plants
    DeviceLightPlanReason.CO2_ACTIVE -> R.string.device_light_quick_setup_reason_co2
    DeviceLightPlanReason.NO_CO2_SAFETY_CAP -> R.string.device_light_quick_setup_reason_no_co2
    DeviceLightPlanReason.ACTIVE_SOIL_STARTUP -> R.string.device_light_quick_setup_reason_soil
    DeviceLightPlanReason.ESTIMATED_PAR -> R.string.device_light_quick_setup_reason_estimated_par
}

private fun DeviceLightAutomaticChannel.chartColor(colors: AquaLightManualColors): Color =
    when (this) {
        DeviceLightAutomaticChannel.RED -> colors.red
        DeviceLightAutomaticChannel.GREEN -> colors.green
        DeviceLightAutomaticChannel.BLUE -> colors.blue
        DeviceLightAutomaticChannel.WHITE -> colors.white
    }

@StringRes
private fun DeviceLightAutomaticChannel.shortLabelRes(): Int = when (this) {
    DeviceLightAutomaticChannel.RED -> R.string.device_light_plan_channel_red
    DeviceLightAutomaticChannel.GREEN -> R.string.device_light_plan_channel_green
    DeviceLightAutomaticChannel.BLUE -> R.string.device_light_plan_channel_blue
    DeviceLightAutomaticChannel.WHITE -> R.string.device_light_plan_channel_white
}

private const val MINUTE_MS = 60_000L
private const val MINUTES_PER_DAY = 1_440f
private const val PERCENT_SCALE = 100f
private const val CHART_GRID_DIVISIONS = 4
private const val PROFILE_VALUE_WEIGHT = 1.2f
private const val PROFILE_VALUE_MAX_LINES = 2
private const val SUMMARY_LABEL_MAX_LINES = 2
private const val PRIMARY_ACTION_WEIGHT = 1.35f
private val CHART_HOUR_LABELS = listOf("00", "06", "12", "18", "24")

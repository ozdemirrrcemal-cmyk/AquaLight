@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions", "LongParameterList")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanPhaseDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanRuntimeState
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightAlgaeLevel
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightAmbientLight
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDensity
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlan
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTank
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightManualColors
import com.aqua.aqualight.ui.common.light.AquaLightQuickSetupAlpha
import com.aqua.aqualight.ui.common.light.AquaLightQuickSetupGeometry
import com.aqua.aqualight.ui.common.light.aquaLightManualColors
import java.time.LocalTime

@Composable
internal fun DeviceLightQuickSetupScreen(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    val channelColors = aquaLightManualColors()
    val background = colorResource(R.color.background_color)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = AquaLightQuickSetupGeometry.screenHorizontalPadding,
                top = AquaLightQuickSetupGeometry.screenTopPadding,
                end = AquaLightQuickSetupGeometry.screenHorizontalPadding,
                bottom = AquaLightQuickSetupGeometry.screenBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.sectionGap)
        ) {
            if (state.tank != null && state.managedPlanSnapshot != null) {
                item("status") {
                    QuickSetupStatusHeader(state, colors, typography)
                }
                if (state.mode == DeviceLightQuickSetupMode.ACTIVE) {
                    activeProgramItems(state, actions, colors, typography, channelColors)
                } else {
                    assessmentItems(state, actions, colors, typography, channelColors)
                }
            }
        }
        if (state.mode != DeviceLightQuickSetupMode.ACTIVE && state.tank != null) {
            QuickSetupActionBar(state, actions, colors, typography)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.assessmentItems(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    channelColors: AquaLightManualColors
) {
    item("profile") {
        CompactAquariumProfile(state, colors, typography)
    }
    item("conditions") {
        ConditionsCard(state, actions, colors, typography)
    }
    val plan = state.plan
    if (plan == null) {
        item("assessment-hint") {
            AssessmentHint(colors, typography)
        }
    } else {
        item("preview") {
            ProgramCard(
                phase = plan.currentPhase.draft,
                plan = plan,
                active = false,
                showCurrentTime = false,
                detailsExpanded = state.detailsExpanded,
                onToggleDetails = actions.onToggleDetails,
                colors = colors,
                typography = typography,
                channelColors = channelColors
            )
        }
        if (state.detailsExpanded) {
            item("details") {
                TechnicalDetailsCard(
                    phases = plan.phases.map { it.draft },
                    colors = colors,
                    typography = typography
                )
            }
        }
        if (state.hasBlockingWarning) {
            item("blocked") {
                BlockingWarningCard(colors, typography)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.activeProgramItems(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    channelColors: AquaLightManualColors
) {
    val snapshot = state.managedPlanSnapshot ?: return
    val phase = state.plan?.currentPhase?.draft ?: snapshot.phaseFor(state.todayEpochDay)
    if (phase != null) {
        item("active-program") {
            ProgramCard(
                phase = phase,
                plan = state.plan,
                active = true,
                showCurrentTime = true,
                detailsExpanded = false,
                onToggleDetails = {},
                colors = colors,
                typography = typography,
                channelColors = channelColors
            )
        }
    }
    item("monitoring") {
        MonitoringCard(state, colors, typography)
    }
    item("reasons") {
        ActiveReasonCard(state, actions.onEditInstalledPlan, colors, typography)
    }
    item("protected") {
        ProtectedFooter(colors, typography)
    }
}

@Composable
private fun QuickSetupStatusHeader(
    state: DeviceLightQuickSetupUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val active = state.mode == DeviceLightQuickSetupMode.ACTIVE
    val editing = state.mode == DeviceLightQuickSetupMode.EDIT
    val runtime = state.managedPlanSnapshot?.runtimeState
    val title = when {
        active && runtime == DeviceLightManagedPlanRuntimeState.RTC_BLOCKED ->
            R.string.device_light_quick_setup_program_waiting
        active && runtime == DeviceLightManagedPlanRuntimeState.NOT_SELECTED ->
            R.string.device_light_quick_setup_program_stored
        active && runtime == DeviceLightManagedPlanRuntimeState.BEFORE_PLAN ->
            R.string.device_light_quick_setup_program_scheduled
        active -> R.string.device_light_quick_setup_program_active
        editing -> R.string.device_light_quick_setup_update_conditions
        else -> R.string.device_light_quick_setup_prepare_title
    }
    val subtitle = when {
        active && runtime == DeviceLightManagedPlanRuntimeState.RTC_BLOCKED ->
            R.string.device_light_quick_setup_program_waiting_subtitle
        active && runtime == DeviceLightManagedPlanRuntimeState.NOT_SELECTED ->
            R.string.device_light_quick_setup_program_stored_subtitle
        active && runtime == DeviceLightManagedPlanRuntimeState.BEFORE_PLAN ->
            R.string.device_light_quick_setup_program_scheduled_subtitle
        active -> R.string.device_light_quick_setup_program_active_subtitle
        editing -> R.string.device_light_quick_setup_edit_subtitle
        else -> R.string.device_light_quick_setup_prepare_subtitle
    }
    val tag = when {
        active && runtime == DeviceLightManagedPlanRuntimeState.ACTIVE ->
            R.string.device_light_quick_setup_active_tag
        active && runtime == DeviceLightManagedPlanRuntimeState.RTC_BLOCKED ->
            R.string.device_light_quick_setup_waiting_tag
        active -> R.string.device_light_quick_setup_stored_tag
        editing -> R.string.device_light_quick_setup_edit_tag
        else -> R.string.device_light_quick_setup_draft_tag
    }
    val statusTone = when {
        active && runtime == DeviceLightManagedPlanRuntimeState.ACTIVE -> colors.success
        active && runtime == DeviceLightManagedPlanRuntimeState.RTC_BLOCKED -> colors.warning
        else -> colors.accent
    }
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (active) {
                Box(
                    Modifier
                        .size(AquaLightQuickSetupGeometry.statusDotSize)
                        .clip(CircleShape)
                        .background(statusTone)
                )
                Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
            } else {
                IconTile(R.drawable.ic_light_quick_setup, colors.accent)
                Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
            ) {
                BasicText(text = stringResource(title), style = typography.title)
                BasicText(text = stringResource(subtitle), style = typography.caption)
            }
            Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
            StatusTag(
                text = stringResource(tag),
                tone = statusTone,
                typography = typography
            )
        }
    }
}

@Composable
private fun CompactAquariumProfile(
    state: DeviceLightQuickSetupUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val tank = state.tank ?: return
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(R.drawable.ic_care_plant_health_24, colors.success)
                Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
                ) {
                    BasicText(
                        text = tank.tankName,
                        style = typography.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    BasicText(
                        text = state.tankDay?.let { day ->
                            stringResource(R.string.device_light_quick_setup_day_value, day)
                        } ?: stringResource(R.string.device_light_quick_setup_setup_date_unknown),
                        style = typography.caption
                    )
                    BasicText(
                        text = stringResource(
                            R.string.device_light_quick_setup_device_height_summary,
                            tank.productDisplayName,
                            tank.heightCm
                        ),
                        style = typography.micro,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ThinDivider(colors)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
            ) {
                ProfileFact(
                    icon = R.drawable.ic_light_preset_planted,
                    text = stringResource(tank.inferredPlantDemand.compactLabelRes()),
                    colors = colors,
                    typography = typography
                )
                ProfileFact(
                    icon = R.drawable.ic_care_plant_health_24,
                    text = stringResource(tank.inferredPlantDensity.labelRes()),
                    colors = colors,
                    typography = typography
                )
                ProfileFact(
                    icon = R.drawable.ic_care_co2_24,
                    text = stringResource(
                        if (tank.inferredCo2Installed) {
                            R.string.device_light_quick_setup_co2_active
                        } else {
                            R.string.device_light_quick_setup_co2_absent
                        }
                    ),
                    colors = colors,
                    typography = typography
                )
                ProfileFact(
                    icon = R.drawable.ic_care_substrate_24,
                    text = stringResource(
                        if (tank.inferredActiveSoil) {
                            R.string.device_light_quick_setup_active_soil
                        } else {
                            R.string.device_light_quick_setup_substrate_unknown
                        }
                    ),
                    colors = colors,
                    typography = typography
                )
            }
        }
    }
}

@Composable
private fun RowScope.ProfileFact(
    @DrawableRes icon: Int,
    text: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(colors.success)
        )
        BasicText(
            text = text,
            style = typography.micro.copy(
                color = colors.primaryText,
                textAlign = TextAlign.Center
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConditionsCard(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_light_quick_setup),
                    contentDescription = null,
                    modifier = Modifier.size(AquaLightQuickSetupGeometry.iconSize),
                    colorFilter = ColorFilter.tint(colors.accent)
                )
                Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
                Column {
                    BasicText(
                        text = stringResource(R.string.device_light_quick_setup_today_conditions),
                        style = typography.title
                    )
                    BasicText(
                        text = stringResource(R.string.device_light_quick_setup_conditions_subtitle),
                        style = typography.caption
                    )
                }
            }
            ChoiceRow(
                label = stringResource(R.string.device_light_quick_setup_daylight),
                choices = listOf(
                    Choice(DeviceLightAmbientLight.LOW, R.string.device_light_quick_setup_low),
                    Choice(
                        DeviceLightAmbientLight.INDIRECT,
                        R.string.device_light_quick_setup_indirect
                    ),
                    Choice(DeviceLightAmbientLight.DIRECT, R.string.device_light_quick_setup_direct)
                ),
                selected = state.ambientLight,
                onSelected = actions.onAmbientLightSelected,
                colors = colors,
                typography = typography
            )
            ChoiceRow(
                label = stringResource(R.string.device_light_quick_setup_algae_level),
                choices = listOf(
                    Choice(DeviceLightAlgaeLevel.NONE, R.string.device_light_quick_setup_none),
                    Choice(DeviceLightAlgaeLevel.MILD, R.string.device_light_quick_setup_mild),
                    Choice(DeviceLightAlgaeLevel.VISIBLE, R.string.device_light_quick_setup_visible)
                ),
                selected = state.algaeLevel,
                onSelected = actions.onAlgaeLevelSelected,
                colors = colors,
                typography = typography
            )
        }
    }
}

private data class Choice<T>(val value: T, @StringRes val labelRes: Int)

@Composable
private fun <T> ChoiceRow(
    label: String,
    choices: List<Choice<T>>,
    selected: T?,
    onSelected: (T) -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            modifier = Modifier.width(92.dp),
            style = typography.body
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
        ) {
            choices.forEach { choice ->
                val isSelected = selected == choice.value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(AquaLightQuickSetupGeometry.segmentHeight)
                        .clip(AquaLightQuickSetupGeometry.choiceShape)
                        .background(
                            if (isSelected) {
                                colors.accent.copy(alpha = AquaLightQuickSetupAlpha.SELECTED)
                            } else {
                                colors.mediaSurface
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) colors.accent else colors.mediaOutline,
                            shape = AquaLightQuickSetupGeometry.choiceShape
                        )
                        .clickable(role = Role.RadioButton) { onSelected(choice.value) },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = stringResource(choice.labelRes),
                        style = typography.micro.copy(
                            color = if (isSelected) colors.accent else colors.secondaryText,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AssessmentHint(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.cardRadius))
            .background(colors.mediaSurface)
            .border(1.dp, colors.mediaOutline, RoundedCornerShape(AquaLightQuickSetupGeometry.cardRadius))
            .padding(AquaLightQuickSetupGeometry.cardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(colors.accent)
        )
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        BasicText(
            text = stringResource(R.string.device_light_quick_setup_complete_conditions),
            style = typography.caption
        )
    }
}

@Composable
private fun ProgramCard(
    phase: DeviceLightManagedPlanPhaseDraft,
    plan: DeviceLightQuickSetupPlan?,
    active: Boolean,
    showCurrentTime: Boolean,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    channelColors: AquaLightManualColors
) {
    val context = LocalContext.current
    val startMinute = (phase.startTimeMs / MINUTE_MS).toInt()
    val endMinute = (phase.endTimeMs / MINUTE_MS).toInt()
    val durationMinutes = endMinute - startMinute
    val peak = phase.scene.channels.values.maxOrNull() ?: 0
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_care_light_24),
                    contentDescription = null,
                    modifier = Modifier.size(AquaLightQuickSetupGeometry.iconSize),
                    colorFilter = ColorFilter.tint(colors.accent)
                )
                Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
                BasicText(
                    text = stringResource(
                        if (active) {
                            R.string.device_light_quick_setup_today_program
                        } else {
                            R.string.device_light_quick_setup_recommended_program
                        }
                    ),
                    style = typography.title,
                    modifier = Modifier.weight(1f)
                )
                if (!active) {
                    StatusTag(
                        text = stringResource(R.string.device_light_quick_setup_cautious_tag),
                        tone = colors.warning,
                        typography = typography
                    )
                }
            }
            BasicText(
                text = stringResource(
                    R.string.device_light_quick_setup_time_range,
                    LocaleFormatter.formatTimeOfDay24Hour(context, startMinute),
                    LocaleFormatter.formatTimeOfDay24Hour(context, endMinute)
                ),
                style = typography.title.copy(fontSize = 20.sp, lineHeight = 24.sp)
            )
            CompactProgramChart(
                phase = phase,
                showCurrentTime = showCurrentTime,
                colors = colors,
                typography = typography,
                channelColors = channelColors
            )
            ProgramSummary(
                durationMinutes = durationMinutes,
                rampMinutes = (phase.rampDurationMs / MINUTE_MS).toInt(),
                peak = peak,
                active = active,
                colors = colors,
                typography = typography
            )
            val algaeLevel = plan?.let { currentPlan ->
                when {
                    DeviceLightPlanReason.VISIBLE_ALGAE_GUARD in currentPlan.reasons ->
                        DeviceLightAlgaeLevel.VISIBLE
                    DeviceLightPlanReason.MILD_ALGAE_GUARD in currentPlan.reasons ->
                        DeviceLightAlgaeLevel.MILD
                    else -> DeviceLightAlgaeLevel.NONE
                }
            }
            if (algaeLevel != null && algaeLevel != DeviceLightAlgaeLevel.NONE) {
                AlgaeHoldNotice(algaeLevel, colors, typography)
            }
            if (!active) {
                DetailLink(detailsExpanded, onToggleDetails, colors, typography)
            }
        }
    }
}

@Composable
private fun CompactProgramChart(
    phase: DeviceLightManagedPlanPhaseDraft,
    showCurrentTime: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    channelColors: AquaLightManualColors
) {
    val description = stringResource(R.string.device_light_quick_setup_chart_description)
    val currentMinute = LocalTime.now().let { it.hour * 60 + it.minute }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius))
            .background(colors.mediaSurface)
            .border(
                1.dp,
                colors.mediaOutline,
                RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius)
            )
            .padding(AquaLightQuickSetupGeometry.chartPadding)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ChartYAxis(colors, typography)
            Spacer(Modifier.width(AquaLightQuickSetupGeometry.tinyGap))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(AquaLightQuickSetupGeometry.chartHeight)
                    .semantics { contentDescription = description }
            ) {
                for (index in 0..4) {
                    val fraction = index / 4f
                    drawLine(
                        color = colors.mediaOutline.copy(alpha = AquaLightQuickSetupAlpha.GUIDE),
                        start = Offset(size.width * fraction, 0f),
                        end = Offset(size.width * fraction, size.height),
                        strokeWidth = AquaLightQuickSetupGeometry.chartGridWidth.toPx()
                    )
                    drawLine(
                        color = colors.mediaOutline.copy(alpha = AquaLightQuickSetupAlpha.GUIDE),
                        start = Offset(0f, size.height * fraction),
                        end = Offset(size.width, size.height * fraction),
                        strokeWidth = AquaLightQuickSetupGeometry.chartGridWidth.toPx()
                    )
                }
                phase.scene.channels.forEach { (channel, percent) ->
                    drawPath(
                        path = channelPath(phase, percent, size.width, size.height),
                        color = channelColors.colorFor(channel),
                        style = Stroke(
                            width = AquaLightQuickSetupGeometry.chartLineWidth.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }
                if (showCurrentTime) {
                    val x = size.width * currentMinute / MINUTES_PER_DAY
                    drawLine(
                        color = colors.primaryText,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = AquaLightQuickSetupGeometry.progressMarkerWidth.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                    drawCircle(
                        color = colors.primaryText,
                        radius = 3.dp.toPx(),
                        center = Offset(x, 3.dp.toPx())
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("00", "06", "12", "18", "24").forEach { label ->
                BasicText(text = label, style = typography.micro)
            }
        }
        Spacer(Modifier.height(AquaLightQuickSetupGeometry.tinyGap))
        ChartLegend(phase, channelColors, typography)
    }
}

@Composable
private fun ChartYAxis(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier
            .width(28.dp)
            .height(AquaLightQuickSetupGeometry.chartHeight),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        listOf("100%", "75%", "50%", "25%", "0%").forEach { label ->
            BasicText(
                text = label,
                style = typography.micro.copy(color = colors.secondaryText, fontSize = 8.sp)
            )
        }
    }
}

@Composable
private fun ChartLegend(
    phase: DeviceLightManagedPlanPhaseDraft,
    channelColors: AquaLightManualColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        phase.scene.channels.keys.forEachIndexed { index, channel ->
            if (index > 0) Spacer(Modifier.width(18.dp))
            Box(
                Modifier
                    .size(AquaLightQuickSetupGeometry.legendDotSize)
                    .clip(CircleShape)
                    .background(channelColors.colorFor(channel))
            )
            Spacer(Modifier.width(AquaLightQuickSetupGeometry.tinyGap))
            BasicText(text = channel.shortLabel(), style = typography.micro)
        }
    }
}

private fun channelPath(
    phase: DeviceLightManagedPlanPhaseDraft,
    percent: Int,
    width: Float,
    height: Float
): Path {
    val start = phase.startTimeMs.toFloat() / DAY_MS
    val end = phase.endTimeMs.toFloat() / DAY_MS
    val ramp = phase.rampDurationMs.toFloat() / DAY_MS
    val peakY = height * (1f - percent / 100f)
    return Path().apply {
        moveTo(0f, height)
        lineTo(width * start, height)
        lineTo(width * (start + ramp), peakY)
        lineTo(width * (end - ramp), peakY)
        lineTo(width * end, height)
        lineTo(width, height)
    }
}

@Composable
private fun ProgramSummary(
    durationMinutes: Int,
    rampMinutes: Int,
    peak: Int,
    active: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryValue(
            stringResource(
                R.string.device_light_quick_setup_hours_short,
                formatHours(durationMinutes)
            ),
            colors,
            typography
        )
        SummaryDivider(colors)
        SummaryValue(
            stringResource(R.string.device_light_quick_setup_transition_value, rampMinutes),
            colors,
            typography
        )
        SummaryDivider(colors)
        SummaryValue(
            if (active) {
                stringResource(R.string.device_light_quick_setup_every_day)
            } else {
                stringResource(R.string.device_light_quick_setup_peak_value, peak)
            },
            colors,
            typography
        )
    }
}

@Composable
private fun RowScope.SummaryValue(
    text: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    BasicText(
        text = text,
        modifier = Modifier.weight(1f),
        style = typography.body.copy(color = colors.primaryText, textAlign = TextAlign.Center),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RowScope.SummaryDivider(colors: AquaDeviceCardColors) {
    Box(
        Modifier
            .width(1.dp)
            .height(24.dp)
            .background(colors.outline)
    )
}

@Composable
private fun AlgaeHoldNotice(
    algaeLevel: DeviceLightAlgaeLevel,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val text = if (algaeLevel == DeviceLightAlgaeLevel.VISIBLE) {
        R.string.device_light_quick_setup_visible_algae_hold
    } else {
        R.string.device_light_quick_setup_mild_algae_hold
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius))
            .background(colors.warning.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE))
            .border(
                1.dp,
                colors.warning,
                RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius)
            )
            .padding(
                horizontal = AquaLightQuickSetupGeometry.rowPadding,
                vertical = AquaLightQuickSetupGeometry.compactGap
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "!",
            style = typography.body.copy(color = colors.warning)
        )
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        BasicText(
            text = stringResource(text),
            style = typography.caption.copy(color = colors.warning)
        )
    }
}

@Composable
private fun DetailLink(
    expanded: Boolean,
    onClick: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AquaLightQuickSetupGeometry.tinyGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = stringResource(
                if (expanded) {
                    R.string.device_light_quick_setup_hide_details
                } else {
                    R.string.device_light_quick_setup_calculation_details
                }
            ),
            modifier = Modifier.weight(1f),
            style = typography.caption.copy(color = colors.accent)
        )
        BasicText(
            text = if (expanded) "−" else "›",
            style = typography.title.copy(color = colors.accent)
        )
    }
}

@Composable
private fun TechnicalDetailsCard(
    phases: List<DeviceLightManagedPlanPhaseDraft>,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_calculation_details),
                style = typography.title
            )
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_estimate_warning),
                style = typography.caption
            )
            phases.forEachIndexed { index, phase ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        text = stringResource(
                            R.string.device_light_quick_setup_phase_day_value,
                            index + 1
                        ),
                        modifier = Modifier.weight(1f),
                        style = typography.micro
                    )
                    val duration = ((phase.endTimeMs - phase.startTimeMs) / MINUTE_MS).toInt()
                    BasicText(
                        text = stringResource(
                            R.string.device_light_quick_setup_hours_short,
                            formatHours(duration)
                        ),
                        style = typography.micro.copy(color = colors.primaryText)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitoringCard(
    state: DeviceLightQuickSetupUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val snapshot = state.managedPlanSnapshot ?: return
    val appliedDay = state.plan?.currentPhase?.draft?.validFromEpochDay
        ?: snapshot.phases.minOfOrNull { it.validFromEpochDay }
    val daysSince = appliedDay?.let { (state.todayEpochDay - it).coerceAtLeast(0L) }
    val reviewDay = state.plan?.reevaluationEpochDay
        ?: appliedDay?.plus(
            if (snapshot.isHeldForReview(state.todayEpochDay, state.tankDay)) 7L else 14L
        )
    val daysUntil = reviewDay?.let { (it - state.todayEpochDay).coerceAtLeast(0L) }
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            MonitorValue(
                label = stringResource(R.string.device_light_quick_setup_last_evaluation),
                value = relativePastLabel(daysSince),
                typography = typography
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(38.dp)
                    .background(colors.outline)
            )
            MonitorValue(
                label = stringResource(R.string.device_light_quick_setup_next_check),
                value = relativeFutureLabel(daysUntil),
                typography = typography
            )
        }
    }
}

@Composable
private fun RowScope.MonitorValue(
    label: String,
    value: String,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = AquaLightQuickSetupGeometry.rowPadding),
        verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
    ) {
        BasicText(text = label, style = typography.caption)
        BasicText(text = value, style = typography.body)
    }
}

@Composable
private fun relativePastLabel(days: Long?): String = when (days) {
    null -> stringResource(R.string.device_light_quick_setup_not_available)
    0L -> stringResource(R.string.device_light_quick_setup_today)
    else -> stringResource(R.string.device_light_quick_setup_days_ago, days)
}

@Composable
private fun relativeFutureLabel(days: Long?): String = when (days) {
    null -> stringResource(R.string.device_light_quick_setup_not_available)
    0L -> stringResource(R.string.device_light_quick_setup_today)
    else -> stringResource(R.string.device_light_quick_setup_days_later, days)
}

@Composable
private fun ActiveReasonCard(
    state: DeviceLightQuickSetupUiState,
    onEdit: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val tank = state.tank ?: return
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_why),
                style = typography.title
            )
            ReasonRow(
                lifecycleReason(state),
                colors,
                typography
            )
            ReasonRow(
                stringResource(
                    R.string.device_light_quick_setup_plant_co2_reason,
                    stringResource(tank.inferredPlantDemand.labelRes()),
                    stringResource(
                        if (tank.inferredCo2Installed) {
                            R.string.device_light_quick_setup_co2_active
                        } else {
                            R.string.device_light_quick_setup_co2_absent
                        }
                    )
                ),
                colors,
                typography
            )
            ReasonRow(
                activeGuardReason(state),
                colors,
                typography
            )
            ThinDivider(colors)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius))
                    .border(
                        1.dp,
                        colors.accent,
                        RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius)
                    )
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(AquaLightQuickSetupGeometry.rowPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = stringResource(R.string.device_light_quick_setup_update_conditions),
                    modifier = Modifier.weight(1f),
                    style = typography.body.copy(color = colors.accent)
                )
                BasicText(text = "›", style = typography.title.copy(color = colors.accent))
            }
        }
    }
}

@Composable
private fun lifecycleReason(state: DeviceLightQuickSetupUiState): String =
    if ((state.tankDay ?: Long.MAX_VALUE) <= 21L) {
        stringResource(R.string.device_light_quick_setup_active_reason_new_tank)
    } else {
        stringResource(R.string.device_light_quick_setup_active_reason_established)
    }

@Composable
private fun activeGuardReason(state: DeviceLightQuickSetupUiState): String = when (state.algaeLevel) {
    DeviceLightAlgaeLevel.MILD ->
        stringResource(R.string.device_light_quick_setup_active_reason_mild_algae)
    DeviceLightAlgaeLevel.VISIBLE ->
        stringResource(R.string.device_light_quick_setup_active_reason_visible_algae)
    DeviceLightAlgaeLevel.NONE ->
        stringResource(R.string.device_light_quick_setup_active_reason_clear)
    null -> if (
        state.managedPlanSnapshot?.isHeldForReview(state.todayEpochDay, state.tankDay) == true
    ) {
        stringResource(R.string.device_light_quick_setup_active_reason_held)
    } else {
        stringResource(R.string.device_light_quick_setup_active_reason_protected)
    }
}

@Composable
private fun ReasonRow(
    text: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(AquaLightQuickSetupGeometry.detailIconSize)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE)),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = stringResource(R.string.device_light_quick_setup_check_mark),
                style = typography.micro.copy(color = colors.accent)
            )
        }
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        BasicText(text = text, style = typography.caption)
    }
}

@Composable
private fun ProtectedFooter(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AquaLightQuickSetupGeometry.rowPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = stringResource(R.string.device_light_quick_setup_check_mark),
            style = typography.body.copy(color = colors.secondaryText)
        )
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        BasicText(
            text = stringResource(R.string.device_light_quick_setup_program_protected),
            style = typography.caption
        )
    }
}

@Composable
private fun BlockingWarningCard(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    BasicText(
        text = stringResource(R.string.device_light_quick_setup_profile_blocked),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.cardRadius))
            .background(colors.danger.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE))
            .border(1.dp, colors.danger, RoundedCornerShape(AquaLightQuickSetupGeometry.cardRadius))
            .padding(AquaLightQuickSetupGeometry.cardPadding),
        style = typography.caption.copy(color = colors.danger)
    )
}

@Composable
private fun QuickSetupActionBar(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val background = colorResource(R.color.background_color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .border(width = 1.dp, color = colors.outline)
            .padding(AquaLightQuickSetupGeometry.actionBarPadding),
        horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
    ) {
        if (state.mode == DeviceLightQuickSetupMode.EDIT) {
            CompactActionButton(
                text = stringResource(R.string.device_light_quick_setup_cancel),
                enabled = !state.applying,
                secondary = true,
                onClick = actions.onCancelEdit,
                modifier = Modifier.weight(0.36f),
                colors = colors,
                typography = typography
            )
        }
        val primaryText = when {
            state.proposalMatchesInstalled ->
                R.string.device_light_quick_setup_program_current
            state.mode == DeviceLightQuickSetupMode.EDIT ->
                R.string.device_light_quick_setup_apply_changes
            else -> R.string.device_light_quick_setup_create_program
        }
        CompactActionButton(
            text = stringResource(primaryText),
            enabled = state.canApply,
            secondary = false,
            onClick = actions.onApply,
            modifier = Modifier.weight(if (state.mode == DeviceLightQuickSetupMode.EDIT) 0.64f else 1f),
            colors = colors,
            typography = typography
        )
    }
}

@Composable
private fun CompactActionButton(
    text: String,
    enabled: Boolean,
    secondary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val shape = RoundedCornerShape(AquaLightQuickSetupGeometry.actionRadius)
    val container = if (secondary) colors.surface else colors.accent
    val content = if (secondary) colors.primaryText else colorResource(R.color.aqua_button_primary_content)
    Box(
        modifier = modifier
            .height(AquaLightQuickSetupGeometry.actionHeight)
            .alpha(if (enabled) 1f else AquaLightQuickSetupAlpha.DISABLED)
            .clip(shape)
            .background(container)
            .border(1.dp, if (secondary) colors.outline else colors.accent, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = AquaLightQuickSetupGeometry.rowPadding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = typography.body.copy(color = content, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IconTile(
    @DrawableRes iconRes: Int,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(AquaLightQuickSetupGeometry.iconBoxSize)
            .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius))
            .background(tint.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(AquaLightQuickSetupGeometry.iconSize),
            colorFilter = ColorFilter.tint(tint)
        )
    }
}

@Composable
private fun StatusTag(
    text: String,
    tone: Color,
    typography: AquaDeviceCardTypography
) {
    BasicText(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius))
            .background(tone.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE))
            .border(
                1.dp,
                tone.copy(alpha = AquaLightQuickSetupAlpha.OUTLINE),
                RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius)
            )
            .padding(
                horizontal = AquaLightQuickSetupGeometry.factHorizontalPadding,
                vertical = AquaLightQuickSetupGeometry.factVerticalPadding
            ),
        style = typography.micro.copy(color = tone),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ThinDivider(colors: AquaDeviceCardColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(AquaLightQuickSetupGeometry.dividerHeight)
            .background(colors.outline)
    )
}

private fun DeviceLightManagedPlanSnapshot.phaseFor(
    todayEpochDay: Long
): DeviceLightManagedPlanPhaseDraft? {
    activePhaseIndex?.let { index -> phases.getOrNull(index)?.let { return it } }
    return phases.firstOrNull { phase ->
        todayEpochDay >= phase.validFromEpochDay &&
            (phase.validUntilEpochDayExclusive == null ||
                todayEpochDay < phase.validUntilEpochDayExclusive)
    } ?: phases.firstOrNull()
}

private fun DeviceLightManagedPlanSnapshot.isHeldForReview(
    todayEpochDay: Long,
    tankDay: Long?
): Boolean {
    val phase = phaseFor(todayEpochDay) ?: return false
    return phases.size == 1 && phase.validUntilEpochDayExclusive == null &&
        tankDay != null && tankDay < 85L &&
        runtimeState != DeviceLightManagedPlanRuntimeState.NOT_INSTALLED
}

private fun DeviceLightPlantDemand.labelRes(): Int = when (this) {
    DeviceLightPlantDemand.LOW -> R.string.device_light_quick_setup_low
    DeviceLightPlantDemand.MEDIUM -> R.string.device_light_quick_setup_medium
    DeviceLightPlantDemand.HIGH -> R.string.device_light_quick_setup_high
}

private fun DeviceLightPlantDemand.compactLabelRes(): Int = when (this) {
    DeviceLightPlantDemand.LOW -> R.string.device_light_quick_setup_low_light
    DeviceLightPlantDemand.MEDIUM -> R.string.device_light_quick_setup_medium_light
    DeviceLightPlantDemand.HIGH -> R.string.device_light_quick_setup_high_light
}

private fun DeviceLightPlantDensity.labelRes(): Int = when (this) {
    DeviceLightPlantDensity.SPARSE -> R.string.device_light_quick_setup_sparse
    DeviceLightPlantDensity.MEDIUM -> R.string.device_light_quick_setup_medium_density
    DeviceLightPlantDensity.DENSE -> R.string.device_light_quick_setup_dense
}

private fun DeviceLightAutomaticChannel.shortLabel(): String = when (this) {
    DeviceLightAutomaticChannel.RED -> "R"
    DeviceLightAutomaticChannel.GREEN -> "G"
    DeviceLightAutomaticChannel.BLUE -> "B"
    DeviceLightAutomaticChannel.WHITE -> "W"
}

private fun AquaLightManualColors.colorFor(channel: DeviceLightAutomaticChannel): Color =
    when (channel) {
        DeviceLightAutomaticChannel.RED -> red
        DeviceLightAutomaticChannel.GREEN -> green
        DeviceLightAutomaticChannel.BLUE -> blue
        DeviceLightAutomaticChannel.WHITE -> white
    }

@Composable
private fun formatHours(durationMinutes: Int): String = LocaleFormatter.formatDecimal(
    context = LocalContext.current,
    value = durationMinutes / 60.0,
    maximumFractionDigits = 1
)

private const val MINUTE_MS = 60_000L
private const val MINUTES_PER_DAY = 1_440f
private const val DAY_MS = 86_400_000f

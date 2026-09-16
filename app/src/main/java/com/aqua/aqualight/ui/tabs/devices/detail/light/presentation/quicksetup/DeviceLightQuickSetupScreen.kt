@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions", "LongParameterList")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumDaylightExposure
import com.aqua.aqualight.application.aquarium.AquariumPlantCoverage
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.aquarium.AquariumSurfaceGrowth
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanPhaseDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanRuntimeState
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanConfidence
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanWarning
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlan
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightManualColors
import com.aqua.aqualight.ui.common.light.AquaLightQuickSetupAlpha
import com.aqua.aqualight.ui.common.light.AquaLightQuickSetupGeometry
import com.aqua.aqualight.ui.common.light.aquaLightDashboardTypography
import com.aqua.aqualight.ui.common.light.aquaLightManualColors

@Composable
internal fun DeviceLightQuickSetupScreen(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    modifier: Modifier = Modifier
) {
    val channelColors = aquaLightManualColors()
    val colors = channelColors.card
    val typography = aquaLightDashboardTypography(colors)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color))
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
                item("status") { QuickSetupStatusHeader(state, colors, typography) }
                if (state.mode == DeviceLightQuickSetupMode.ACTIVE) {
                    activeProgramItems(state, actions, colors, typography, channelColors)
                } else {
                    assessmentItems(state, actions, colors, typography, channelColors)
                }
            }
        }
        if (state.mode != DeviceLightQuickSetupMode.ACTIVE &&
            state.tank != null &&
            !state.profileBlocked
        ) {
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
    item("profile") { CompactAquariumProfile(state, colors, typography) }
    if (state.profileBlocked) {
        item("blocked") { BlockingWarningCard(colors, typography) }
        return
    }
    if (state.hasSetupQuestions) {
        item("setup-data") { SetupDataCard(state, actions, colors, typography) }
    }
    if (state.hasConditionQuestions) {
        item("conditions") { ConditionsCard(state, actions, colors, typography) }
    }
    val plan = state.plan
    if (plan == null) {
        item("assessment-hint") { AssessmentHint(state, colors, typography) }
    } else {
        item("preview") {
            ProgramCard(
                phase = plan.currentPhase.draft,
                plan = plan,
                active = false,
                detailsExpanded = state.detailsExpanded,
                onToggleDetails = actions.onToggleDetails,
                colors = colors,
                typography = typography,
                channelColors = channelColors
            )
        }
        if (state.detailsExpanded) {
            item("details") { TechnicalDetailsCard(plan, state, colors, typography) }
        }
        if (state.hasBlockingWarning) {
            item("blocked") { BlockingWarningCard(colors, typography) }
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
    phase?.let { current ->
        item("active-program") {
            ProgramCard(
                phase = current,
                plan = state.plan,
                active = true,
                detailsExpanded = false,
                onToggleDetails = {},
                colors = colors,
                typography = typography,
                channelColors = channelColors
            )
        }
    }
    item("monitoring") { MonitoringCard(state, colors, typography) }
    item("reasons") { ActiveReasonCard(state, actions.onEditInstalledPlan, colors, typography) }
    item("protected") { ProtectedFooter(colors, typography) }
}

@Composable
private fun QuickSetupStatusHeader(
    state: DeviceLightQuickSetupUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val presentation = headerPresentation(state)
    val tone = if (presentation.warning) colors.warning else colors.accent
    val subtitle = when (state.mode) {
        DeviceLightQuickSetupMode.CREATE -> pluralStringResource(
            R.plurals.device_light_quick_setup_questions_remaining,
            state.missingInputCount,
            state.missingInputCount
        )
        DeviceLightQuickSetupMode.EDIT ->
            stringResource(R.string.device_light_quick_setup_edit_subtitle)
        DeviceLightQuickSetupMode.ACTIVE -> stringResource(presentation.subtitleRes)
    }
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (state.mode == DeviceLightQuickSetupMode.ACTIVE) {
                Box(
                    Modifier
                        .size(AquaLightQuickSetupGeometry.statusDotSize)
                        .clip(CircleShape)
                        .background(tone)
                )
            } else {
                IconTile(R.drawable.ic_light_quick_setup, colors.accent)
            }
            Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
            ) {
                BasicText(text = stringResource(presentation.titleRes), style = typography.title)
                BasicText(text = subtitle, style = typography.caption)
            }
            Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
            StatusTag(stringResource(presentation.tagRes), tone, typography)
        }
    }
}

private data class HeaderPresentation(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val tagRes: Int,
    val warning: Boolean = false
)

private fun headerPresentation(state: DeviceLightQuickSetupUiState): HeaderPresentation =
    when (state.mode) {
        DeviceLightQuickSetupMode.CREATE -> HeaderPresentation(
            R.string.device_light_quick_setup_prepare_title,
            R.string.device_light_quick_setup_prepare_subtitle,
            R.string.device_light_quick_setup_draft_tag
        )
        DeviceLightQuickSetupMode.EDIT -> HeaderPresentation(
            R.string.device_light_quick_setup_update_conditions,
            R.string.device_light_quick_setup_edit_subtitle,
            R.string.device_light_quick_setup_edit_tag
        )
        DeviceLightQuickSetupMode.ACTIVE -> activeHeaderPresentation(
            state.managedPlanSnapshot?.runtimeState
        )
    }

private fun activeHeaderPresentation(
    runtime: DeviceLightManagedPlanRuntimeState?
): HeaderPresentation = when (runtime) {
    DeviceLightManagedPlanRuntimeState.ACTIVE -> HeaderPresentation(
        R.string.device_light_quick_setup_program_active,
        R.string.device_light_quick_setup_program_active_subtitle,
        R.string.device_light_quick_setup_active_tag
    )
    DeviceLightManagedPlanRuntimeState.RTC_BLOCKED -> HeaderPresentation(
        R.string.device_light_quick_setup_program_waiting,
        R.string.device_light_quick_setup_program_waiting_subtitle,
        R.string.device_light_quick_setup_waiting_tag,
        warning = true
    )
    DeviceLightManagedPlanRuntimeState.BEFORE_PLAN -> HeaderPresentation(
        R.string.device_light_quick_setup_program_scheduled,
        R.string.device_light_quick_setup_program_scheduled_subtitle,
        R.string.device_light_quick_setup_stored_tag
    )
    DeviceLightManagedPlanRuntimeState.NOT_INSTALLED,
    DeviceLightManagedPlanRuntimeState.NOT_SELECTED,
    null -> HeaderPresentation(
        R.string.device_light_quick_setup_program_stored,
        R.string.device_light_quick_setup_program_stored_subtitle,
        R.string.device_light_quick_setup_stored_tag
    )
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
                IconTile(R.drawable.ic_care_plant_health_24, colors.accent)
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
                        } ?: stringResource(
                            R.string.device_light_quick_setup_setup_date_unknown
                        ),
                        style = typography.caption
                    )
                    BasicText(
                        text = stringResource(
                            R.string.device_light_quick_setup_verified_device_summary,
                            tank.productDisplayName,
                            tank.fixtureLengthMm,
                            tank.hardwareRevision
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
                    R.drawable.ic_light_preset_planted,
                    stringResource((state.plantDemand ?: tank.plantDemand).compactLabelRes()),
                    colors,
                    typography
                )
                ProfileFact(
                    R.drawable.ic_care_plant_health_24,
                    stringResource((state.plantCoverage ?: tank.plantCoverage).labelRes()),
                    colors,
                    typography
                )
                ProfileFact(
                    R.drawable.ic_care_co2_24,
                    stringResource(co2SummaryRes(state)),
                    colors,
                    typography
                )
                ProfileFact(
                    R.drawable.ic_care_substrate_24,
                    stringResource(tank.substrateSemantic.labelRes()),
                    colors,
                    typography
                )
            }
        }
    }
}

@StringRes
private fun co2SummaryRes(state: DeviceLightQuickSetupUiState): Int = when {
    state.tank?.co2ComponentPresent != true -> R.string.device_light_quick_setup_co2_absent
    state.co2Readiness == AquariumCo2Readiness.READY_AT_LIGHT_ON ->
        R.string.device_light_quick_setup_co2_ready
    state.co2Readiness == AquariumCo2Readiness.NOT_READY_AT_LIGHT_ON ->
        R.string.device_light_quick_setup_co2_not_ready_short
    else -> R.string.device_light_quick_setup_co2_equipment
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
            colorFilter = ColorFilter.tint(colors.accent)
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
private fun SetupDataCard(
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
            CardHeader(
                R.drawable.ic_care_device_24,
                stringResource(R.string.device_light_quick_setup_measurements_title),
                stringResource(R.string.device_light_quick_setup_measurements_subtitle),
                colors,
                typography
            )
            if (state.requestsWaterDepth) {
                SetupValueRow(
                    stringResource(R.string.device_light_quick_setup_water_depth),
                    state.waterDepthCm?.let {
                        stringResource(R.string.device_light_quick_setup_cm_value, it)
                    },
                    actions.onWaterDepthRequested,
                    colors,
                    typography
                )
            }
            if (state.requestsFixtureHeight) {
                SetupValueRow(
                    stringResource(R.string.device_light_quick_setup_fixture_height),
                    state.fixtureHeightAboveWaterCm?.let {
                        stringResource(R.string.device_light_quick_setup_cm_value, it)
                    },
                    actions.onFixtureHeightRequested,
                    colors,
                    typography
                )
            }
            if (state.requestsProgramEnd) {
                SetupValueRow(
                    stringResource(R.string.device_light_quick_setup_program_end),
                    state.programEndMinute?.let { formatTime(it) },
                    actions.onProgramEndRequested,
                    colors,
                    typography
                )
            }
            if (state.requestsDirectDaylightWindow) {
                SetupValueRow(
                    stringResource(R.string.device_light_quick_setup_daylight_start),
                    state.daylightStartMinute?.let { formatTime(it) },
                    actions.onDaylightStartRequested,
                    colors,
                    typography
                )
                SetupValueRow(
                    stringResource(R.string.device_light_quick_setup_daylight_end),
                    state.daylightEndMinute?.let { formatTime(it) },
                    actions.onDaylightEndRequested,
                    colors,
                    typography
                )
            }
        }
    }
}

@Composable
private fun SetupValueRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius))
            .border(
                1.dp,
                if (value == null) colors.warning else colors.outline,
                RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(AquaLightQuickSetupGeometry.rowPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(text = label, modifier = Modifier.weight(1f), style = typography.body)
        BasicText(
            text = value ?: stringResource(R.string.device_light_quick_setup_enter_value),
            style = typography.caption.copy(
                color = if (value == null) colors.warning else colors.accent
            )
        )
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        Image(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(colors.secondaryText)
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
    val tank = state.tank ?: return
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
        ) {
            CardHeader(
                R.drawable.ic_light_quick_setup,
                stringResource(R.string.device_light_quick_setup_today_conditions),
                stringResource(R.string.device_light_quick_setup_conditions_subtitle),
                colors,
                typography
            )
            if (state.requestsPlantDemand) {
                ChoiceGrid(
                    stringResource(R.string.device_light_quick_setup_plant_demand),
                    plantDemandChoices(tank.reviewedPlantDemandFloor),
                    state.plantDemand,
                    actions.onPlantDemandSelected,
                    colors,
                    typography
                )
                if (tank.reviewedPlantDemandFloor == DeviceLightPlantDemand.MEDIUM) {
                    BasicText(
                        text = stringResource(
                            R.string.device_light_quick_setup_plant_demand_floor_help
                        ),
                        style = typography.micro
                    )
                }
            }
            if (state.requestsPlantCoverage) {
                ChoiceGrid(
                    stringResource(R.string.device_light_quick_setup_plant_coverage),
                    listOf(
                        Choice(
                            AquariumPlantCoverage.SPARSE,
                            R.string.device_light_quick_setup_sparse
                        ),
                        Choice(
                            AquariumPlantCoverage.MEDIUM,
                            R.string.device_light_quick_setup_medium_density
                        ),
                        Choice(
                            AquariumPlantCoverage.DENSE,
                            R.string.device_light_quick_setup_dense
                        )
                    ),
                    state.plantCoverage,
                    actions.onPlantCoverageSelected,
                    colors,
                    typography
                )
            }
            if (state.requestsCo2Readiness) {
                ChoiceGrid(
                    stringResource(R.string.device_light_quick_setup_co2_question),
                    listOf(
                        Choice(
                            AquariumCo2Readiness.READY_AT_LIGHT_ON,
                            R.string.device_light_quick_setup_yes_ready
                        ),
                        Choice(
                            AquariumCo2Readiness.NOT_READY_AT_LIGHT_ON,
                            R.string.device_light_quick_setup_no_not_ready
                        )
                    ),
                    state.co2Readiness,
                    actions.onCo2ReadinessSelected,
                    colors,
                    typography
                )
                BasicText(
                    text = stringResource(R.string.device_light_quick_setup_co2_help),
                    style = typography.micro
                )
            }
            if (state.requestsDaylight) {
                ChoiceGrid(
                    stringResource(R.string.device_light_quick_setup_daylight),
                    listOf(
                        Choice(
                            AquariumDaylightExposure.LOW,
                            R.string.device_light_quick_setup_low
                        ),
                        Choice(
                            AquariumDaylightExposure.INDIRECT,
                            R.string.device_light_quick_setup_indirect
                        ),
                        Choice(
                            AquariumDaylightExposure.DIRECT,
                            R.string.device_light_quick_setup_direct
                        )
                    ),
                    state.daylightExposure,
                    actions.onDaylightSelected,
                    colors,
                    typography
                )
            }
            if (state.requestsSurfaceObservation) {
                ChoiceGrid(
                    stringResource(R.string.device_light_quick_setup_surface_growth),
                    buildList {
                        add(
                            Choice(
                                AquariumSurfaceGrowth.NONE,
                                R.string.device_light_quick_setup_none
                            )
                        )
                        if (tank.hasShrimp) {
                            add(
                                Choice(
                                    AquariumSurfaceGrowth.TARGET_BIOFILM,
                                    R.string.device_light_quick_setup_target_biofilm
                                )
                            )
                        }
                        add(
                            Choice(
                                AquariumSurfaceGrowth.STABLE_ALGAE,
                                R.string.device_light_quick_setup_stable_algae
                            )
                        )
                        add(
                            Choice(
                                AquariumSurfaceGrowth.WORSENING_ALGAE,
                                R.string.device_light_quick_setup_worsening_algae
                            )
                        )
                    },
                    state.surfaceGrowth,
                    actions.onSurfaceGrowthSelected,
                    colors,
                    typography
                )
            }
            if (state.requestsShelter) {
                ChoiceGrid(
                    stringResource(R.string.device_light_quick_setup_shelter),
                    listOf(
                        Choice(
                            AquariumShelterAvailability.ADEQUATE,
                            R.string.device_light_quick_setup_shelter_adequate
                        ),
                        Choice(
                            AquariumShelterAvailability.LIMITED,
                            R.string.device_light_quick_setup_shelter_limited
                        ),
                        Choice(
                            AquariumShelterAvailability.NONE,
                            R.string.device_light_quick_setup_none
                        )
                    ),
                    state.shelterAvailability,
                    actions.onShelterSelected,
                    colors,
                    typography
                )
            }
        }
    }
}

private data class Choice<T>(val value: T, @StringRes val labelRes: Int)

private fun plantDemandChoices(
    minimum: DeviceLightPlantDemand
): List<Choice<DeviceLightPlantDemand>> = buildList {
    if (minimum == DeviceLightPlantDemand.UNKNOWN ||
        minimum == DeviceLightPlantDemand.LOW
    ) {
        add(Choice(DeviceLightPlantDemand.LOW, R.string.device_light_quick_setup_low))
    }
    if (minimum != DeviceLightPlantDemand.HIGH) {
        add(Choice(DeviceLightPlantDemand.MEDIUM, R.string.device_light_quick_setup_medium))
    }
    add(Choice(DeviceLightPlantDemand.HIGH, R.string.device_light_quick_setup_high))
}

@Composable
private fun <T> ChoiceGrid(
    label: String,
    choices: List<Choice<T>>,
    selected: T?,
    onSelected: (T) -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)) {
        BasicText(text = label, style = typography.body)
        choices.chunked(MAX_CHOICES_PER_ROW).forEach { rowChoices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
            ) {
                rowChoices.forEach { choice ->
                    val isSelected = choice.value == selected
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
                                1.dp,
                                if (isSelected) colors.accent else colors.outline,
                                AquaLightQuickSetupGeometry.choiceShape
                            )
                            .clickable(
                                role = Role.RadioButton,
                                onClick = { onSelected(choice.value) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = stringResource(choice.labelRes),
                            style = typography.caption.copy(
                                color = if (isSelected) colors.accent else colors.secondaryText,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(MAX_CHOICES_PER_ROW - rowChoices.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CardHeader(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(AquaLightQuickSetupGeometry.iconSize),
            colorFilter = ColorFilter.tint(colors.accent)
        )
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = title, style = typography.title)
            BasicText(text = subtitle, style = typography.caption)
        }
    }
}

@Composable
private fun AssessmentHint(
    state: DeviceLightQuickSetupUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val count = state.missingInputCount
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.cardRadius))
            .background(colors.surface)
            .border(
                1.dp,
                if (count == 0) colors.accent else colors.outline,
                RoundedCornerShape(AquaLightQuickSetupGeometry.cardRadius)
            )
            .padding(AquaLightQuickSetupGeometry.cardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (count == 0) colors.accent else colors.warning)
        )
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        BasicText(
            text = if (count == 0) {
                stringResource(R.string.device_light_quick_setup_calculating)
            } else {
                pluralStringResource(
                    R.plurals.device_light_quick_setup_complete_remaining,
                    count,
                    count
                )
            },
            style = typography.caption
        )
    }
}

@Composable
private fun ProgramCard(
    phase: DeviceLightManagedPlanPhaseDraft,
    plan: DeviceLightQuickSetupPlan?,
    active: Boolean,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    channelColors: AquaLightManualColors
) {
    val startMinute = (phase.startTimeMs / MINUTE_MS).toInt()
    val endMinute = (phase.endTimeMs / MINUTE_MS).toInt()
    val durationMinutes = endMinute - startMinute
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = stringResource(
                            if (active) {
                                R.string.device_light_quick_setup_today_program
                            } else {
                                R.string.device_light_quick_setup_recommended_program
                            }
                        ),
                        style = typography.title
                    )
                    BasicText(
                        text = stringResource(
                            R.string.device_light_quick_setup_time_range,
                            formatTime(startMinute),
                            formatTime(endMinute)
                        ),
                        style = typography.caption
                    )
                }
                StatusTag(
                    stringResource(
                        if (plan?.confidence == DeviceLightPlanConfidence.CALIBRATED) {
                            R.string.device_light_quick_setup_calibrated_tag
                        } else {
                            R.string.device_light_quick_setup_conservative_tag
                        }
                    ),
                    if (plan?.confidence == DeviceLightPlanConfidence.CALIBRATED) {
                        colors.success
                    } else {
                        colors.warning
                    },
                    typography
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)) {
                FactChip(
                    stringResource(
                        R.string.device_light_quick_setup_hours_short,
                        formatHours(durationMinutes)
                    ),
                    colors,
                    typography
                )
                FactChip(
                    stringResource(R.string.device_light_quick_setup_every_day),
                    colors,
                    typography
                )
                FactChip(
                    stringResource(
                        R.string.device_light_quick_setup_transition_value,
                        phase.rampDurationMs / MINUTE_MS
                    ),
                    colors,
                    typography
                )
            }
            ThinDivider(colors)
            phase.scene.channels.forEach { (channel, value) ->
                ChannelRow(channel, value, channelColors, colors, typography)
            }
            plan?.let { currentPlan ->
                if (currentPlan.reasons.any { reason ->
                        reason == DeviceLightPlanReason.STABLE_ALGAE_HOLD ||
                            reason == DeviceLightPlanReason.WORSENING_ALGAE_GUARD
                    }
                ) {
                    GuardNotice(
                        DeviceLightPlanReason.WORSENING_ALGAE_GUARD in currentPlan.reasons,
                        colors,
                        typography
                    )
                }
                PlanSafetyNotices(currentPlan.warnings, colors, typography)
                DetailLink(detailsExpanded, onToggleDetails, colors, typography)
            }
        }
    }
}

@Composable
private fun RowScope.FactChip(
    text: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    BasicText(
        text = text,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius))
            .background(colors.mediaSurface)
            .padding(
                horizontal = AquaLightQuickSetupGeometry.factHorizontalPadding,
                vertical = AquaLightQuickSetupGeometry.factVerticalPadding
            ),
        style = typography.micro.copy(textAlign = TextAlign.Center),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ChannelRow(
    channel: DeviceLightAutomaticChannel,
    value: Int,
    channelColors: AquaLightManualColors,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(channelColors.colorFor(channel))
        )
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        BasicText(
            text = stringResource(channel.labelRes()),
            modifier = Modifier.width(58.dp),
            style = typography.micro
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(CircleShape)
                .background(colors.mediaSurface)
        ) {
            if (value > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(value / 100f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(channelColors.colorFor(channel))
                )
            }
        }
        Spacer(Modifier.width(AquaLightQuickSetupGeometry.compactGap))
        BasicText(
            text = stringResource(R.string.device_light_quick_setup_percent_value, value),
            modifier = Modifier.width(38.dp),
            style = typography.micro.copy(
                color = colors.primaryText,
                textAlign = TextAlign.End
            )
        )
    }
}

@Composable
private fun GuardNotice(
    worsening: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    BasicText(
        text = stringResource(
            if (worsening) {
                R.string.device_light_quick_setup_worsening_hold
            } else {
                R.string.device_light_quick_setup_stable_hold
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius))
            .background(colors.warning.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE))
            .border(
                1.dp,
                colors.warning,
                RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius)
            )
            .padding(AquaLightQuickSetupGeometry.rowPadding),
        style = typography.caption.copy(color = colors.warning)
    )
}

@Composable
private fun PlanSafetyNotices(
    warnings: Set<DeviceLightPlanWarning>,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val messageResources = warnings.mapNotNull { warning -> warning.noticeRes() }
    if (messageResources.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius))
            .background(colors.warning.copy(alpha = AquaLightQuickSetupAlpha.SUBTLE))
            .border(
                1.dp,
                colors.warning,
                RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius)
            )
            .padding(AquaLightQuickSetupGeometry.rowPadding),
        verticalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.tinyGap)
    ) {
        messageResources.forEach { messageRes ->
            BasicText(
                text = stringResource(messageRes),
                style = typography.caption.copy(color = colors.warning)
            )
        }
    }
}

@StringRes
private fun DeviceLightPlanWarning.noticeRes(): Int? = when (this) {
    DeviceLightPlanWarning.SETUP_DATE_MISSING ->
        R.string.device_light_quick_setup_warning_setup_date_missing
    DeviceLightPlanWarning.SETUP_DATE_IN_FUTURE ->
        R.string.device_light_quick_setup_warning_setup_date_future
    DeviceLightPlanWarning.HIGH_LIGHT_WITHOUT_READY_CO2 ->
        R.string.device_light_quick_setup_warning_high_light_co2
    DeviceLightPlanWarning.DEEP_INSTALLATION_UNCALIBRATED ->
        R.string.device_light_quick_setup_warning_deep_uncalibrated
    DeviceLightPlanWarning.DIRECT_DAYLIGHT_OVERLAP ->
        R.string.device_light_quick_setup_warning_daylight_overlap
    DeviceLightPlanWarning.SHRIMP_SHELTER_MISSING ->
        R.string.device_light_quick_setup_warning_shrimp_shelter
    DeviceLightPlanWarning.NOT_PLANTED_FRESHWATER,
    DeviceLightPlanWarning.NO_PLANTS,
    DeviceLightPlanWarning.UNKNOWN_PLANT_DEMAND,
    DeviceLightPlanWarning.CALIBRATION_UNAVAILABLE -> null
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
    plan: DeviceLightQuickSetupPlan,
    state: DeviceLightQuickSetupUiState,
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
            DetailValueRow(
                stringResource(R.string.device_light_quick_setup_policy),
                plan.policyVersion,
                typography
            )
            DetailValueRow(
                stringResource(R.string.device_light_quick_setup_evidence),
                stringResource(
                    R.string.device_light_quick_setup_source_count,
                    plan.evidenceSourceIds.size
                ),
                typography
            )
            DetailValueRow(
                stringResource(R.string.device_light_quick_setup_calibration),
                if (plan.confidence == DeviceLightPlanConfidence.CALIBRATED) {
                    stringResource(
                        R.string.device_light_quick_setup_calibration_value,
                        plan.calibrationProfileId,
                        plan.calibrationRevision
                    )
                } else {
                    stringResource(R.string.device_light_quick_setup_calibration_missing)
                },
                typography
            )
            DetailValueRow(
                stringResource(R.string.device_light_quick_setup_geometry),
                stringResource(
                    R.string.device_light_quick_setup_geometry_value,
                    state.waterDepthCm ?: 0,
                    state.fixtureHeightAboveWaterCm ?: 0
                ),
                typography
            )
            DetailValueRow(
                stringResource(R.string.device_light_quick_setup_next_check),
                relativeFutureLabel(
                    (plan.reevaluationEpochDay - state.todayEpochDay).coerceAtLeast(0L)
                ),
                typography
            )
            if (plan.confidence == DeviceLightPlanConfidence.CONSERVATIVE_UNCALIBRATED) {
                BasicText(
                    text = stringResource(R.string.device_light_quick_setup_estimate_warning),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AquaLightQuickSetupGeometry.factRadius))
                        .background(colors.mediaSurface)
                        .padding(AquaLightQuickSetupGeometry.rowPadding),
                    style = typography.caption
                )
            }
        }
    }
}

@Composable
private fun DetailValueRow(
    label: String,
    value: String,
    typography: AquaDeviceCardTypography
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        BasicText(text = label, modifier = Modifier.weight(1f), style = typography.caption)
        BasicText(
            text = value,
            modifier = Modifier.weight(1f),
            style = typography.micro.copy(textAlign = TextAlign.End)
        )
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
        ?: snapshot.phases.minOfOrNull { phase -> phase.validFromEpochDay }
    val daysSince = appliedDay?.let { day -> (state.todayEpochDay - day).coerceAtLeast(0L) }
    val reviewDay = state.plan?.reevaluationEpochDay ?: state.tank?.nextReevaluationEpochDay
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            MonitorValue(
                stringResource(R.string.device_light_quick_setup_last_evaluation),
                relativePastLabel(daysSince),
                typography
            )
            Box(Modifier.width(1.dp).height(38.dp).background(colors.outline))
            MonitorValue(
                stringResource(R.string.device_light_quick_setup_next_check),
                if (reviewDay == null || reviewDay <= state.todayEpochDay) {
                    stringResource(R.string.device_light_quick_setup_reassessment_required)
                } else {
                    relativeFutureLabel(reviewDay - state.todayEpochDay)
                },
                typography
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
    else -> pluralStringResource(
        R.plurals.device_light_quick_setup_days_ago,
        days.toSafePluralCount(),
        days
    )
}

@Composable
private fun relativeFutureLabel(days: Long): String = when (days) {
    0L -> stringResource(R.string.device_light_quick_setup_today)
    else -> pluralStringResource(
        R.plurals.device_light_quick_setup_days_later,
        days.toSafePluralCount(),
        days
    )
}

private fun Long.toSafePluralCount(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

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
                installedDurationReason(state),
                colors,
                typography
            )
            ReasonRow(
                stringResource(
                    R.string.device_light_quick_setup_plant_co2_reason,
                    stringResource((state.plantDemand ?: tank.plantDemand).labelRes()),
                    stringResource(co2SummaryRes(state))
                ),
                colors,
                typography
            )
            ReasonRow(activeGuardReason(state), colors, typography)
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
private fun installedDurationReason(state: DeviceLightQuickSetupUiState): String {
    val snapshot = state.managedPlanSnapshot
    val phase = snapshot?.phaseFor(state.todayEpochDay)
    val durationMinutes = phase?.let { installedPhase ->
        ((installedPhase.endTimeMs - installedPhase.startTimeMs) / MINUTE_MS)
            .takeIf { duration -> duration > 0L }
            ?.toInt()
    } ?: state.plan?.currentPhase?.lifecycleStage?.durationMinutes
    return when {
        durationMinutes == null || durationMinutes <= 6 * 60 ->
            stringResource(R.string.device_light_quick_setup_active_reason_new_tank)
        durationMinutes <= 7 * 60 ->
            stringResource(R.string.device_light_quick_setup_active_reason_acclimation)
        else -> stringResource(R.string.device_light_quick_setup_active_reason_established)
    }
}

@Composable
private fun activeGuardReason(state: DeviceLightQuickSetupUiState): String =
    when (state.surfaceGrowth) {
        AquariumSurfaceGrowth.STABLE_ALGAE ->
            stringResource(R.string.device_light_quick_setup_active_reason_stable_algae)
        AquariumSurfaceGrowth.WORSENING_ALGAE ->
            stringResource(R.string.device_light_quick_setup_active_reason_worsening_algae)
        AquariumSurfaceGrowth.TARGET_BIOFILM ->
            stringResource(R.string.device_light_quick_setup_active_reason_biofilm)
        AquariumSurfaceGrowth.NONE ->
            stringResource(R.string.device_light_quick_setup_active_reason_clear)
        AquariumSurfaceGrowth.UNKNOWN,
        null -> stringResource(R.string.device_light_quick_setup_active_reason_held)
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
            .border(
                1.dp,
                colors.danger,
                RoundedCornerShape(AquaLightQuickSetupGeometry.cardRadius)
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.background_color))
            .padding(AquaLightQuickSetupGeometry.actionBarPadding),
        horizontalArrangement = Arrangement.spacedBy(AquaLightQuickSetupGeometry.compactGap)
    ) {
        if (state.mode == DeviceLightQuickSetupMode.EDIT) {
            CompactActionButton(
                stringResource(R.string.device_light_quick_setup_cancel),
                !state.applying,
                true,
                actions.onCancelEdit,
                Modifier.weight(0.36f),
                colors,
                typography
            )
        }
        val primaryText = when {
            state.proposalMatchesInstalled -> R.string.device_light_quick_setup_program_current
            state.mode == DeviceLightQuickSetupMode.EDIT ->
                R.string.device_light_quick_setup_apply_changes
            else -> R.string.device_light_quick_setup_create_program
        }
        CompactActionButton(
            stringResource(primaryText),
            state.canApply,
            false,
            actions.onApply,
            Modifier.weight(if (state.mode == DeviceLightQuickSetupMode.EDIT) 0.64f else 1f),
            colors,
            typography
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
    val content = if (secondary) {
        colors.primaryText
    } else {
        colorResource(R.color.aqua_button_primary_content)
    }
    Box(
        modifier = modifier
            .height(AquaLightQuickSetupGeometry.actionHeight)
            .alpha(if (enabled) 1f else AquaLightQuickSetupAlpha.DISABLED)
            .clip(shape)
            .background(container)
            .border(1.dp, if (secondary) colors.outline else colors.accent, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
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
private fun IconTile(@DrawableRes iconRes: Int, tint: Color) {
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
        maxLines = 1
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

@StringRes
private fun DeviceLightPlantDemand.labelRes(): Int = when (this) {
    DeviceLightPlantDemand.UNKNOWN -> R.string.device_light_quick_setup_unknown
    DeviceLightPlantDemand.LOW -> R.string.device_light_quick_setup_low
    DeviceLightPlantDemand.MEDIUM -> R.string.device_light_quick_setup_medium
    DeviceLightPlantDemand.HIGH -> R.string.device_light_quick_setup_high
}

@StringRes
private fun DeviceLightPlantDemand.compactLabelRes(): Int = when (this) {
    DeviceLightPlantDemand.UNKNOWN -> R.string.device_light_quick_setup_plant_demand_unknown
    DeviceLightPlantDemand.LOW -> R.string.device_light_quick_setup_low_light
    DeviceLightPlantDemand.MEDIUM -> R.string.device_light_quick_setup_medium_light
    DeviceLightPlantDemand.HIGH -> R.string.device_light_quick_setup_high_light
}

@StringRes
private fun AquariumPlantCoverage.labelRes(): Int = when (this) {
    AquariumPlantCoverage.UNKNOWN -> R.string.device_light_quick_setup_coverage_unknown
    AquariumPlantCoverage.SPARSE -> R.string.device_light_quick_setup_sparse
    AquariumPlantCoverage.MEDIUM -> R.string.device_light_quick_setup_medium_density
    AquariumPlantCoverage.DENSE -> R.string.device_light_quick_setup_dense
}

@StringRes
private fun AquariumSubstrateSemantic.labelRes(): Int = when (this) {
    AquariumSubstrateSemantic.ACTIVE_SOIL -> R.string.device_light_quick_setup_active_soil
    AquariumSubstrateSemantic.NUTRIENT_BASE -> R.string.device_light_quick_setup_nutrient_base
    AquariumSubstrateSemantic.INERT -> R.string.device_light_quick_setup_inert_substrate
    AquariumSubstrateSemantic.ADDITIVE -> R.string.device_light_quick_setup_substrate_additive
    AquariumSubstrateSemantic.UNKNOWN -> R.string.device_light_quick_setup_substrate_unverified
    AquariumSubstrateSemantic.NOT_APPLICABLE -> R.string.device_light_quick_setup_no_substrate
}

@StringRes
private fun DeviceLightAutomaticChannel.labelRes(): Int = when (this) {
    DeviceLightAutomaticChannel.RED -> R.string.device_light_quick_setup_channel_red
    DeviceLightAutomaticChannel.GREEN -> R.string.device_light_quick_setup_channel_green
    DeviceLightAutomaticChannel.BLUE -> R.string.device_light_quick_setup_channel_blue
    DeviceLightAutomaticChannel.WHITE -> R.string.device_light_quick_setup_channel_white
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

@Composable
private fun formatTime(minutesOfDay: Int): String = LocaleFormatter.formatTimeOfDay24Hour(
    LocalContext.current,
    minutesOfDay
)

private const val MAX_CHOICES_PER_ROW = 3
private const val MINUTE_MS = 60_000L

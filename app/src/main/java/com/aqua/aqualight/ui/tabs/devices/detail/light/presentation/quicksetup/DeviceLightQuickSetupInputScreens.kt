package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowSurface
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowColors
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowTypography
import java.time.LocalDate

@Composable
internal fun QuickSetupProfileScreen(state: DeviceLightQuickSetupUiState) {
    val context = checkNotNull(state.context)
    val profile = checkNotNull(state.plantProfile)
    val ageDays = (LocalDate.now().toEpochDay() - context.setupDateEpochDay).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.sectionGap)) {
        QuickSetupHeading(
            title = stringResource(R.string.device_light_quick_setup_title),
            description = stringResource(R.string.device_light_quick_setup_profile_description)
        )
        QuickSetupProfileCard(
            title = stringResource(R.string.device_light_quick_setup_aquarium_title),
            rows = listOf(
                stringResource(R.string.device_light_quick_setup_name_label) to context.aquariumName,
                stringResource(R.string.device_light_quick_setup_dimensions_label) to stringResource(
                    R.string.device_light_quick_setup_dimensions_value,
                    context.tankLengthCm,
                    context.tankWidthCm,
                    context.tankHeightCm
                ),
                stringResource(R.string.device_light_quick_setup_age_label) to stringResource(
                    R.string.device_light_quick_setup_age_days,
                    ageDays
                ),
                stringResource(R.string.device_light_quick_setup_type_label) to context.tankType,
                stringResource(R.string.device_light_quick_setup_style_label) to context.tankStyle
            )
        )
        QuickSetupProfileCard(
            title = stringResource(R.string.device_light_quick_setup_plants_title),
            rows = listOf(
                stringResource(R.string.device_light_quick_setup_selected_plants_label) to
                    profile.selectedPlantCount.toString(),
                stringResource(R.string.device_light_quick_setup_highest_demand_label) to
                    plantDemandText(profile.highestDemand)
            )
        )
        QuickSetupProfileCard(
            title = stringResource(R.string.device_light_quick_setup_setup_title),
            rows = listOf(
                stringResource(R.string.device_light_quick_setup_substrate_label) to
                    substrateText(context.substrateSemantic),
                stringResource(R.string.device_light_quick_setup_co2_label) to stringResource(
                    if (context.co2Present) {
                        R.string.device_light_quick_setup_present
                    } else {
                        R.string.device_light_quick_setup_not_present
                    }
                )
            )
        )
        QuickSetupProfileCard(
            title = stringResource(R.string.device_light_quick_setup_lighting_title),
            rows = listOf(
                stringResource(R.string.device_light_quick_setup_product_label) to
                    context.productDisplayName,
                stringResource(R.string.device_light_quick_setup_channels_label) to
                    context.channelKeys.joinToString(separator = " · ") { it.uppercase() }
            )
        )
        QuickSetupInfoCard(stringResource(R.string.device_light_quick_setup_profile_read_only_info))
    }
}

@Composable
internal fun QuickSetupWaterHeightScreen(
    state: DeviceLightQuickSetupUiState,
    onValueChange: (String) -> Unit
) {
    val context = checkNotNull(state.context)
    Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.sectionGap)) {
        QuickSetupHeading(
            title = stringResource(R.string.device_light_quick_setup_water_title),
            description = stringResource(R.string.device_light_quick_setup_water_description)
        )
        AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
            WaterHeightIllustration()
        }
        QuickSetupNumberField(
            value = state.waterHeightText,
            placeholder = stringResource(R.string.device_light_quick_setup_measurement_placeholder),
            suffix = stringResource(R.string.device_light_quick_setup_unit_cm),
            onValueChange = onValueChange
        )
        QuickSetupInfoCard(
            stringResource(
                R.string.device_light_quick_setup_water_limit_info,
                context.tankHeightCm
            )
        )
    }
}

@Composable
internal fun QuickSetupFixtureHeightScreen(
    state: DeviceLightQuickSetupUiState,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.sectionGap)) {
        QuickSetupHeading(
            title = stringResource(R.string.device_light_quick_setup_fixture_title),
            description = stringResource(R.string.device_light_quick_setup_fixture_description)
        )
        AquaGuidedFlowSurface(Modifier.fillMaxWidth()) {
            FixtureHeightIllustration()
        }
        QuickSetupNumberField(
            value = state.fixtureHeightText,
            placeholder = stringResource(R.string.device_light_quick_setup_measurement_placeholder),
            suffix = stringResource(R.string.device_light_quick_setup_unit_cm),
            onValueChange = onValueChange
        )
        QuickSetupInfoCard(stringResource(R.string.device_light_quick_setup_fixture_info))
    }
}

@Composable
internal fun QuickSetupLightTimeScreen(
    state: DeviceLightQuickSetupUiState,
    onChanged: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.sectionGap)) {
        QuickSetupHeading(
            title = stringResource(R.string.device_light_quick_setup_time_title),
            description = stringResource(R.string.device_light_quick_setup_time_description)
        )
        Spacer(Modifier.height(4.dp))
        QuickSetupTimeControl(state.firstLightOnMinuteOfDay, onChanged)
        QuickSetupInfoCard(stringResource(R.string.device_light_quick_setup_time_info))
    }
}

@Composable
internal fun QuickSetupCo2Screen(
    state: DeviceLightQuickSetupUiState,
    onCheckedChange: (Boolean) -> Unit
) {
    val lightTime = state.firstLightOnMinuteOfDay.toClockTextForUi()
    val co2Time = ((state.firstLightOnMinuteOfDay - CO2_PRECHARGE_MINUTES) + MINUTES_PER_DAY)
        .rem(MINUTES_PER_DAY)
        .toClockTextForUi()
    Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.sectionGap)) {
        QuickSetupHeading(
            title = stringResource(R.string.device_light_quick_setup_co2_title),
            description = stringResource(R.string.device_light_quick_setup_co2_description)
        )
        QuickSetupProfileCard(
            title = stringResource(R.string.device_light_quick_setup_co2_timing_title),
            rows = listOf(
                stringResource(R.string.device_light_quick_setup_light_on_label) to lightTime,
                stringResource(R.string.device_light_quick_setup_co2_latest_label) to co2Time
            )
        )
        QuickSetupSwitchRow(
            title = stringResource(R.string.device_light_quick_setup_co2_switch_title),
            summary = stringResource(R.string.device_light_quick_setup_co2_switch_summary),
            checked = state.co2Precharged,
            onCheckedChange = onCheckedChange
        )
        QuickSetupInfoCard(stringResource(R.string.device_light_quick_setup_co2_info))
    }
}

@Composable
private fun QuickSetupProfileCard(
    title: String,
    rows: List<Pair<String, String>>
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    AquaDeviceCardSurface(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicText(text = title, style = typography.title)
            rows.forEach { (label, value) ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    BasicText(
                        text = label,
                        style = typography.caption,
                        modifier = Modifier.weight(1f)
                    )
                    BasicText(
                        text = value,
                        style = typography.body,
                        modifier = Modifier.weight(1.15f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WaterHeightIllustration() {
    val colors = aquaGuidedFlowColors()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightQuickSetupGeometry.measurementIllustrationHeight)
            .padding(18.dp)
    ) {
        val left = size.width * 0.18f
        val right = size.width * 0.72f
        val top = size.height * 0.12f
        val bottom = size.height * 0.88f
        drawRect(
            color = colors.outline,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            style = Stroke(width = 2.dp.toPx())
        )
        val waterTop = size.height * 0.30f
        drawRect(
            color = colors.accent.copy(alpha = 0.34f),
            topLeft = Offset(left + 2.dp.toPx(), waterTop),
            size = androidx.compose.ui.geometry.Size(
                right - left - 4.dp.toPx(),
                bottom - waterTop - 2.dp.toPx()
            )
        )
        val substrateTop = size.height * 0.78f
        drawRect(
            color = colors.textSecondary.copy(alpha = 0.32f),
            topLeft = Offset(left + 2.dp.toPx(), substrateTop),
            size = androidx.compose.ui.geometry.Size(
                right - left - 4.dp.toPx(),
                bottom - substrateTop - 2.dp.toPx()
            )
        )
        val arrowX = size.width * 0.84f
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX, waterTop),
            end = Offset(arrowX, substrateTop),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX - 8.dp.toPx(), waterTop),
            end = Offset(arrowX + 8.dp.toPx(), waterTop),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX - 8.dp.toPx(), substrateTop),
            end = Offset(arrowX + 8.dp.toPx(), substrateTop),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun FixtureHeightIllustration() {
    val colors = aquaGuidedFlowColors()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightQuickSetupGeometry.measurementIllustrationHeight)
            .padding(18.dp)
    ) {
        val tankLeft = size.width * 0.13f
        val tankRight = size.width * 0.87f
        val waterTop = size.height * 0.58f
        val tankBottom = size.height * 0.90f
        drawRect(
            color = colors.outline,
            topLeft = Offset(tankLeft, waterTop),
            size = androidx.compose.ui.geometry.Size(tankRight - tankLeft, tankBottom - waterTop),
            style = Stroke(width = 2.dp.toPx())
        )
        drawRect(
            color = colors.accent.copy(alpha = 0.30f),
            topLeft = Offset(tankLeft + 2.dp.toPx(), waterTop + 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
                tankRight - tankLeft - 4.dp.toPx(),
                tankBottom - waterTop - 4.dp.toPx()
            )
        )
        val fixtureY = size.height * 0.22f
        drawLine(
            color = colors.textPrimary,
            start = Offset(size.width * 0.30f, fixtureY),
            end = Offset(size.width * 0.70f, fixtureY),
            strokeWidth = 8.dp.toPx(),
            cap = StrokeCap.Round
        )
        val arrowX = size.width * 0.78f
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX, fixtureY + 6.dp.toPx()),
            end = Offset(arrowX, waterTop),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = colors.textPrimary,
            start = Offset(arrowX - 7.dp.toPx(), waterTop),
            end = Offset(arrowX + 7.dp.toPx(), waterTop),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun plantDemandText(demand: AquariumPlantLightDemand): String = stringResource(
    when (demand) {
        AquariumPlantLightDemand.LOW -> R.string.device_light_quick_setup_demand_low
        AquariumPlantLightDemand.MEDIUM -> R.string.device_light_quick_setup_demand_medium
        AquariumPlantLightDemand.HIGH -> R.string.device_light_quick_setup_demand_high
    }
)

@Composable
private fun substrateText(semantic: AquariumSubstrateSemantic): String = stringResource(
    when (semantic) {
        AquariumSubstrateSemantic.NOT_APPLICABLE -> R.string.device_light_quick_setup_substrate_na
        AquariumSubstrateSemantic.UNKNOWN -> R.string.device_light_quick_setup_substrate_unknown
        AquariumSubstrateSemantic.INERT -> R.string.device_light_quick_setup_substrate_inert
        AquariumSubstrateSemantic.NUTRIENT_BASE ->
            R.string.device_light_quick_setup_substrate_nutrient
        AquariumSubstrateSemantic.ACTIVE_SOIL ->
            R.string.device_light_quick_setup_substrate_active_soil
        AquariumSubstrateSemantic.ADDITIVE -> R.string.device_light_quick_setup_substrate_additive
    }
)

private fun Int.toClockTextForUi(): String = "%02d:%02d".format(this / 60, this % 60)

private const val CO2_PRECHARGE_MINUTES = 120
private const val MINUTES_PER_DAY = 1_440

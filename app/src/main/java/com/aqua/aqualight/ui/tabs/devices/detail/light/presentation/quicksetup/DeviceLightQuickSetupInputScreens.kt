package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowSurface
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

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.deviceLightChannelNameResource
import java.time.LocalDate

@Composable
internal fun QuickSetupAquariumProfileCard(state: DeviceLightQuickSetupUiState) {
    val context = checkNotNull(state.context)
    val ageDays = (LocalDate.now().toEpochDay() - context.setupDateEpochDay)
        .coerceAtLeast(0)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
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
            stringResource(R.string.device_light_quick_setup_age_label) to pluralStringResource(
                R.plurals.device_light_quick_setup_age_days,
                ageDays,
                ageDays
            ),
            stringResource(R.string.device_light_quick_setup_type_label) to
                quickSetupTankTypeText(context.tankType),
            stringResource(R.string.device_light_quick_setup_style_label) to
                quickSetupTankStyleText(context.tankStyle)
        )
    )
}

@Composable
internal fun QuickSetupPlantsProfileCard(state: DeviceLightQuickSetupUiState) {
    val profile = checkNotNull(state.plantProfile)
    QuickSetupProfileCard(
        title = stringResource(R.string.device_light_quick_setup_plants_title),
        rows = listOf(
            stringResource(R.string.device_light_quick_setup_selected_plants_label) to
                profile.selectedPlantCount.toString(),
            stringResource(R.string.device_light_quick_setup_highest_demand_label) to
                plantDemandText(profile.highestDemand)
        )
    )
}

@Composable
internal fun QuickSetupSetupProfileCard(state: DeviceLightQuickSetupUiState) {
    val context = checkNotNull(state.context)
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
}

@Composable
internal fun QuickSetupLightingProfileCard(state: DeviceLightQuickSetupUiState) {
    val context = checkNotNull(state.context)
    val channelLabels = mutableListOf<String>()
    for (key in context.channelKeys) {
        channelLabels += stringResource(deviceLightChannelNameResource(key))
    }
    QuickSetupProfileCard(
        title = stringResource(R.string.device_light_quick_setup_lighting_title),
        rows = listOf(
            stringResource(R.string.device_light_quick_setup_product_label) to
                context.productDisplayName,
            stringResource(R.string.device_light_quick_setup_channels_label) to
                channelLabels.joinToString(separator = " · ")
        )
    )
}

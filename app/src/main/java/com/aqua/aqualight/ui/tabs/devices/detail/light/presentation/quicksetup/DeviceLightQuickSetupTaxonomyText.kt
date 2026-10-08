package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy

@Composable
internal fun quickSetupTankTypeText(value: String): String = when (value.trim()) {
    AquariumTankTaxonomy.TYPE_FRESHWATER_FISH ->
        stringResource(R.string.aquarium_tank_profile_freshwater_fish)

    AquariumTankTaxonomy.TYPE_SHRIMP ->
        stringResource(R.string.aquarium_tank_profile_shrimp)

    AquariumTankTaxonomy.TYPE_PLANTED ->
        stringResource(R.string.aquarium_tank_profile_planted)

    AquariumTankTaxonomy.TYPE_BRACKISH_GENERAL ->
        stringResource(R.string.aquarium_tank_profile_brackish_general)

    AquariumTankTaxonomy.TYPE_MARINE_FISH ->
        stringResource(R.string.aquarium_tank_profile_marine_fish)

    AquariumTankTaxonomy.TYPE_SOFT_CORAL_REEF ->
        stringResource(R.string.aquarium_tank_profile_soft_coral_reef)

    AquariumTankTaxonomy.TYPE_LPS_REEF ->
        stringResource(R.string.aquarium_tank_profile_lps_reef)

    AquariumTankTaxonomy.TYPE_SPS_REEF ->
        stringResource(R.string.aquarium_tank_profile_sps_reef)

    AquariumTankTaxonomy.TYPE_MIXED_REEF ->
        stringResource(R.string.aquarium_tank_profile_mixed_reef)

    AquariumTankTaxonomy.TYPE_OTHER_FRESHWATER,
    AquariumTankTaxonomy.TYPE_OTHER_BRACKISH,
    AquariumTankTaxonomy.TYPE_OTHER_MARINE ->
        stringResource(R.string.aquarium_tank_profile_other)

    else -> stringResource(R.string.device_light_unknown_value)
}

@Composable
internal fun quickSetupTankStyleText(value: String): String = when (value.trim()) {
    AquariumTankTaxonomy.STYLE_NATURE_AQUARIUM ->
        stringResource(R.string.aquarium_text_nature_aquarium)
    AquariumTankTaxonomy.STYLE_IWAGUMI -> stringResource(R.string.aquarium_style_iwagumi)
    AquariumTankTaxonomy.STYLE_DUTCH -> stringResource(R.string.aquarium_style_dutch)
    AquariumTankTaxonomy.STYLE_JUNGLE -> stringResource(R.string.aquarium_style_jungle)
    AquariumTankTaxonomy.STYLE_BIOTOPE -> stringResource(R.string.aquarium_style_biotope)
    AquariumTankTaxonomy.STYLE_BLACKWATER -> stringResource(R.string.aquarium_style_blackwater)
    AquariumTankTaxonomy.STYLE_FOREST -> stringResource(R.string.aquarium_style_forest)
    AquariumTankTaxonomy.STYLE_MOUNTAIN -> stringResource(R.string.aquarium_style_mountain)
    AquariumTankTaxonomy.STYLE_ISLAND -> stringResource(R.string.aquarium_style_island)
    else -> stringResource(R.string.device_light_unknown_value)
}

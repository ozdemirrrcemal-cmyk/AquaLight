package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy

@Composable
internal fun quickSetupTankTypeText(value: String): String = when (value.trim()) {
    AquariumTankTaxonomy.TYPE_FISH -> stringResource(R.string.aquarium_tank_type_fish)
    AquariumTankTaxonomy.TYPE_SHRIMP -> stringResource(R.string.aquarium_tank_type_shrimp)
    AquariumTankTaxonomy.TYPE_PLANTED -> stringResource(R.string.aquarium_tank_type_planted)
    AquariumTankTaxonomy.TYPE_MARINE -> stringResource(R.string.aquarium_tank_type_marine)
    AquariumTankTaxonomy.TYPE_SOFTIES -> stringResource(R.string.aquarium_tank_type_softies)
    AquariumTankTaxonomy.TYPE_MIXED_REEF -> stringResource(R.string.aquarium_tank_type_mixed_reef)
    AquariumTankTaxonomy.TYPE_SPS -> stringResource(R.string.aquarium_tank_type_sps)
    AquariumTankTaxonomy.TYPE_CORAL -> stringResource(R.string.aquarium_tank_type_coral)
    AquariumTankTaxonomy.TYPE_OTHER -> stringResource(R.string.aquarium_tank_type_other)
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

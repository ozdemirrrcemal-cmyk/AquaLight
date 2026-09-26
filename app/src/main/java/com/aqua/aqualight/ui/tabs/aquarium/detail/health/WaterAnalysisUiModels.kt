package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy

internal enum class WaterTestParameterId {
    PH,
    NITRATE,
    NITRITE,
    AMMONIA_AMMONIUM,
    GH,
    KH,
    PHOSPHATE,
    TDS,
    EC,
    CO2,
    IRON,
    POTASSIUM,
    SALINITY,
    SPECIFIC_GRAVITY,
    CALCIUM,
    MAGNESIUM,
    COPPER,
    DISSOLVED_OXYGEN
}

internal enum class WaterTestImportance {
    RECOMMENDED,
    ADDITIONAL
}

internal data class WaterTestParameterUiModel(
    val id: WaterTestParameterId,
    @DrawableRes val iconRes: Int,
    @StringRes val nameRes: Int,
    @StringRes val symbolRes: Int?,
    @StringRes val unitRes: Int?,
    val importance: WaterTestImportance,
    val value: String
)

internal sealed interface TemperatureSensorUiState {
    object Unavailable : TemperatureSensorUiState
    object Loading : TemperatureSensorUiState
    data class Available(val deviceName: String) : TemperatureSensorUiState
    data class Reading(
        val deviceName: String,
        val temperatureText: String
    ) : TemperatureSensorUiState
    data class Stale(val deviceName: String?) : TemperatureSensorUiState
    object Error : TemperatureSensorUiState
}

internal object WaterTestProfileUiCatalog {

    private val freshwaterFishRecommended = listOf(
        WaterTestParameterId.PH,
        WaterTestParameterId.AMMONIA_AMMONIUM,
        WaterTestParameterId.NITRITE,
        WaterTestParameterId.NITRATE,
        WaterTestParameterId.GH,
        WaterTestParameterId.KH
    )

    private val freshwaterFishAdditional = listOf(
        WaterTestParameterId.PHOSPHATE,
        WaterTestParameterId.TDS,
        WaterTestParameterId.EC,
        WaterTestParameterId.DISSOLVED_OXYGEN
    )

    private val plantedRecommended = listOf(
        WaterTestParameterId.PH,
        WaterTestParameterId.NITRATE,
        WaterTestParameterId.PHOSPHATE,
        WaterTestParameterId.GH,
        WaterTestParameterId.KH
    )

    private val plantedAdditional = listOf(
        WaterTestParameterId.AMMONIA_AMMONIUM,
        WaterTestParameterId.NITRITE,
        WaterTestParameterId.CO2,
        WaterTestParameterId.IRON,
        WaterTestParameterId.POTASSIUM,
        WaterTestParameterId.TDS,
        WaterTestParameterId.EC,
        WaterTestParameterId.DISSOLVED_OXYGEN
    )

    private val shrimpRecommended = listOf(
        WaterTestParameterId.PH,
        WaterTestParameterId.AMMONIA_AMMONIUM,
        WaterTestParameterId.NITRITE,
        WaterTestParameterId.NITRATE,
        WaterTestParameterId.GH,
        WaterTestParameterId.KH,
        WaterTestParameterId.TDS
    )

    private val shrimpAdditional = listOf(
        WaterTestParameterId.EC,
        WaterTestParameterId.PHOSPHATE,
        WaterTestParameterId.COPPER,
        WaterTestParameterId.DISSOLVED_OXYGEN
    )

    private val brackishRecommended = listOf(
        WaterTestParameterId.PH,
        WaterTestParameterId.AMMONIA_AMMONIUM,
        WaterTestParameterId.NITRITE,
        WaterTestParameterId.NITRATE,
        WaterTestParameterId.SALINITY,
        WaterTestParameterId.KH
    )

    private val brackishAdditional = listOf(
        WaterTestParameterId.GH,
        WaterTestParameterId.PHOSPHATE,
        WaterTestParameterId.SPECIFIC_GRAVITY,
        WaterTestParameterId.DISSOLVED_OXYGEN
    )

    private val marineFishRecommended = listOf(
        WaterTestParameterId.PH,
        WaterTestParameterId.AMMONIA_AMMONIUM,
        WaterTestParameterId.NITRITE,
        WaterTestParameterId.NITRATE,
        WaterTestParameterId.SALINITY,
        WaterTestParameterId.KH,
        WaterTestParameterId.PHOSPHATE
    )

    private val reefRecommended = listOf(
        WaterTestParameterId.PH,
        WaterTestParameterId.NITRATE,
        WaterTestParameterId.PHOSPHATE,
        WaterTestParameterId.SALINITY,
        WaterTestParameterId.KH,
        WaterTestParameterId.CALCIUM,
        WaterTestParameterId.MAGNESIUM
    )

    private val marineAdditional = listOf(
        WaterTestParameterId.AMMONIA_AMMONIUM,
        WaterTestParameterId.NITRITE,
        WaterTestParameterId.SPECIFIC_GRAVITY,
        WaterTestParameterId.DISSOLVED_OXYGEN
    )

    private val marineFishAdditional = listOf(
        WaterTestParameterId.SPECIFIC_GRAVITY,
        WaterTestParameterId.CALCIUM,
        WaterTestParameterId.MAGNESIUM,
        WaterTestParameterId.DISSOLVED_OXYGEN
    )

    fun recommendedIds(tankProfile: String): List<WaterTestParameterId> = when (tankProfile) {
        AquariumTankTaxonomy.TYPE_FRESHWATER_FISH,
        AquariumTankTaxonomy.TYPE_OTHER_FRESHWATER -> freshwaterFishRecommended

        AquariumTankTaxonomy.TYPE_PLANTED -> plantedRecommended
        AquariumTankTaxonomy.TYPE_SHRIMP -> shrimpRecommended

        AquariumTankTaxonomy.TYPE_BRACKISH_GENERAL,
        AquariumTankTaxonomy.TYPE_OTHER_BRACKISH -> brackishRecommended

        AquariumTankTaxonomy.TYPE_MARINE_FISH,
        AquariumTankTaxonomy.TYPE_OTHER_MARINE -> marineFishRecommended

        AquariumTankTaxonomy.TYPE_SOFT_CORAL_REEF,
        AquariumTankTaxonomy.TYPE_LPS_REEF,
        AquariumTankTaxonomy.TYPE_SPS_REEF,
        AquariumTankTaxonomy.TYPE_MIXED_REEF -> reefRecommended

        else -> emptyList()
    }

    fun additionalIds(tankProfile: String): List<WaterTestParameterId> = when (tankProfile) {
        AquariumTankTaxonomy.TYPE_FRESHWATER_FISH,
        AquariumTankTaxonomy.TYPE_OTHER_FRESHWATER -> freshwaterFishAdditional

        AquariumTankTaxonomy.TYPE_PLANTED -> plantedAdditional
        AquariumTankTaxonomy.TYPE_SHRIMP -> shrimpAdditional

        AquariumTankTaxonomy.TYPE_BRACKISH_GENERAL,
        AquariumTankTaxonomy.TYPE_OTHER_BRACKISH -> brackishAdditional

        AquariumTankTaxonomy.TYPE_MARINE_FISH,
        AquariumTankTaxonomy.TYPE_OTHER_MARINE -> marineFishAdditional

        AquariumTankTaxonomy.TYPE_SOFT_CORAL_REEF,
        AquariumTankTaxonomy.TYPE_LPS_REEF,
        AquariumTankTaxonomy.TYPE_SPS_REEF,
        AquariumTankTaxonomy.TYPE_MIXED_REEF -> marineAdditional

        else -> emptyList()
    }

    @DrawableRes
    fun profileIconRes(tankProfile: String): Int = when (tankProfile) {
        AquariumTankTaxonomy.TYPE_FRESHWATER_FISH,
        AquariumTankTaxonomy.TYPE_MARINE_FISH -> R.drawable.ic_life_fish_24

        AquariumTankTaxonomy.TYPE_PLANTED -> R.drawable.ic_health_plant_24
        AquariumTankTaxonomy.TYPE_SHRIMP -> R.drawable.ic_life_shrimp_24

        AquariumTankTaxonomy.TYPE_SOFT_CORAL_REEF,
        AquariumTankTaxonomy.TYPE_LPS_REEF,
        AquariumTankTaxonomy.TYPE_SPS_REEF,
        AquariumTankTaxonomy.TYPE_MIXED_REEF -> R.drawable.ic_life_coral_24

        AquariumTankTaxonomy.TYPE_BRACKISH_GENERAL,
        AquariumTankTaxonomy.TYPE_OTHER_FRESHWATER,
        AquariumTankTaxonomy.TYPE_OTHER_BRACKISH,
        AquariumTankTaxonomy.TYPE_OTHER_MARINE -> R.drawable.ic_care_water_change_24

        else -> R.drawable.ic_care_water_test_24
    }

    fun model(
        tankProfile: String,
        id: WaterTestParameterId,
        importance: WaterTestImportance,
        value: String
    ): WaterTestParameterUiModel {
        val environment = AquariumTankTaxonomy.environmentForTankType(tankProfile)
        val iconRes = when (id) {
            WaterTestParameterId.PH -> R.drawable.ic_care_water_test_24
            WaterTestParameterId.NITRATE,
            WaterTestParameterId.NITRITE,
            WaterTestParameterId.AMMONIA_AMMONIUM,
            WaterTestParameterId.PHOSPHATE,
            WaterTestParameterId.COPPER -> R.drawable.ic_water_test_molecule_24

            WaterTestParameterId.GH -> R.drawable.ic_water_test_shield_24
            WaterTestParameterId.KH,
            WaterTestParameterId.SALINITY,
            WaterTestParameterId.EC,
            WaterTestParameterId.DISSOLVED_OXYGEN -> R.drawable.ic_water_test_wave_24

            WaterTestParameterId.TDS,
            WaterTestParameterId.SPECIFIC_GRAVITY -> R.drawable.ic_care_water_change_24

            WaterTestParameterId.CO2 -> R.drawable.ic_health_plant_24
            WaterTestParameterId.CALCIUM,
            WaterTestParameterId.POTASSIUM -> R.drawable.ic_water_test_crystal_24

            WaterTestParameterId.MAGNESIUM,
            WaterTestParameterId.IRON -> R.drawable.ic_water_test_mineral_24
        }
        val nameRes = when (id) {
            WaterTestParameterId.PH -> R.string.tank_health_test_ph
            WaterTestParameterId.NITRATE -> R.string.tank_health_test_nitrate
            WaterTestParameterId.NITRITE -> R.string.tank_health_test_nitrite
            WaterTestParameterId.AMMONIA_AMMONIUM -> R.string.tank_health_test_ammonia_ammonium
            WaterTestParameterId.GH -> R.string.tank_health_test_general_hardness
            WaterTestParameterId.KH -> if (
                environment == AquariumTankTaxonomy.WATER_ENVIRONMENT_MARINE
            ) {
                R.string.tank_health_test_alkalinity
            } else {
                R.string.tank_health_test_carbonate_hardness
            }

            WaterTestParameterId.PHOSPHATE -> R.string.tank_health_test_phosphate
            WaterTestParameterId.TDS -> R.string.tank_health_test_tds
            WaterTestParameterId.EC -> R.string.tank_health_test_conductivity
            WaterTestParameterId.CO2 -> R.string.tank_health_test_carbon_dioxide
            WaterTestParameterId.IRON -> R.string.tank_health_test_iron
            WaterTestParameterId.POTASSIUM -> R.string.tank_health_test_potassium
            WaterTestParameterId.SALINITY -> R.string.tank_health_test_salinity
            WaterTestParameterId.SPECIFIC_GRAVITY -> R.string.tank_health_test_specific_gravity
            WaterTestParameterId.CALCIUM -> R.string.tank_health_test_calcium
            WaterTestParameterId.MAGNESIUM -> R.string.tank_health_test_magnesium
            WaterTestParameterId.COPPER -> R.string.tank_health_test_copper
            WaterTestParameterId.DISSOLVED_OXYGEN -> R.string.tank_health_test_dissolved_oxygen
        }
        val symbolRes = when (id) {
            WaterTestParameterId.PH -> R.string.tank_health_test_ph
            WaterTestParameterId.NITRATE -> R.string.tank_health_test_symbol_nitrate
            WaterTestParameterId.NITRITE -> R.string.tank_health_test_symbol_nitrite
            WaterTestParameterId.AMMONIA_AMMONIUM -> R.string.tank_health_test_symbol_ammonia_ammonium
            WaterTestParameterId.GH -> R.string.tank_health_test_symbol_gh
            WaterTestParameterId.KH -> R.string.tank_health_test_symbol_kh
            WaterTestParameterId.PHOSPHATE -> R.string.tank_health_test_symbol_phosphate
            WaterTestParameterId.TDS -> R.string.tank_health_test_symbol_tds
            WaterTestParameterId.EC -> R.string.tank_health_test_symbol_ec
            WaterTestParameterId.CO2 -> R.string.tank_health_test_symbol_co2
            WaterTestParameterId.IRON -> R.string.tank_health_test_symbol_iron
            WaterTestParameterId.POTASSIUM -> R.string.tank_health_test_symbol_potassium
            WaterTestParameterId.SALINITY -> null
            WaterTestParameterId.SPECIFIC_GRAVITY -> R.string.tank_health_test_symbol_specific_gravity
            WaterTestParameterId.CALCIUM -> R.string.tank_health_test_symbol_calcium
            WaterTestParameterId.MAGNESIUM -> R.string.tank_health_test_symbol_magnesium
            WaterTestParameterId.COPPER -> R.string.tank_health_test_symbol_copper
            WaterTestParameterId.DISSOLVED_OXYGEN -> R.string.tank_health_test_symbol_oxygen
        }
        val unitRes = when (id) {
            WaterTestParameterId.PH,
            WaterTestParameterId.SPECIFIC_GRAVITY -> null

            WaterTestParameterId.GH -> R.string.tank_health_analysis_unit_dgh
            WaterTestParameterId.KH -> R.string.tank_health_analysis_unit_dkh
            WaterTestParameterId.TDS -> R.string.tank_health_analysis_unit_ppm
            WaterTestParameterId.EC -> R.string.tank_health_analysis_unit_us_cm
            WaterTestParameterId.SALINITY -> R.string.tank_health_analysis_unit_ppt

            else -> R.string.tank_health_analysis_unit_mg_l
        }
        return WaterTestParameterUiModel(
            id = id,
            iconRes = iconRes,
            nameRes = nameRes,
            symbolRes = symbolRes,
            unitRes = unitRes,
            importance = importance,
            value = value
        )
    }
}

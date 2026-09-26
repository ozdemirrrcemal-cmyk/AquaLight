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

    private val iconResByParameter = mapOf(
        WaterTestParameterId.PH to R.drawable.ic_care_water_test_24,
        WaterTestParameterId.NITRATE to R.drawable.ic_water_test_molecule_24,
        WaterTestParameterId.NITRITE to R.drawable.ic_water_test_molecule_24,
        WaterTestParameterId.AMMONIA_AMMONIUM to R.drawable.ic_water_test_molecule_24,
        WaterTestParameterId.GH to R.drawable.ic_water_test_shield_24,
        WaterTestParameterId.KH to R.drawable.ic_water_test_wave_24,
        WaterTestParameterId.PHOSPHATE to R.drawable.ic_water_test_molecule_24,
        WaterTestParameterId.TDS to R.drawable.ic_care_water_change_24,
        WaterTestParameterId.EC to R.drawable.ic_water_test_wave_24,
        WaterTestParameterId.CO2 to R.drawable.ic_health_plant_24,
        WaterTestParameterId.IRON to R.drawable.ic_water_test_mineral_24,
        WaterTestParameterId.POTASSIUM to R.drawable.ic_water_test_crystal_24,
        WaterTestParameterId.SALINITY to R.drawable.ic_water_test_wave_24,
        WaterTestParameterId.SPECIFIC_GRAVITY to R.drawable.ic_care_water_change_24,
        WaterTestParameterId.CALCIUM to R.drawable.ic_water_test_crystal_24,
        WaterTestParameterId.MAGNESIUM to R.drawable.ic_water_test_mineral_24,
        WaterTestParameterId.COPPER to R.drawable.ic_water_test_molecule_24,
        WaterTestParameterId.DISSOLVED_OXYGEN to R.drawable.ic_water_test_wave_24
    )

    private val nameResByParameter = mapOf(
        WaterTestParameterId.PH to R.string.tank_health_test_ph,
        WaterTestParameterId.NITRATE to R.string.tank_health_test_nitrate,
        WaterTestParameterId.NITRITE to R.string.tank_health_test_nitrite,
        WaterTestParameterId.AMMONIA_AMMONIUM to R.string.tank_health_test_ammonia_ammonium,
        WaterTestParameterId.GH to R.string.tank_health_test_general_hardness,
        WaterTestParameterId.KH to R.string.tank_health_test_carbonate_hardness,
        WaterTestParameterId.PHOSPHATE to R.string.tank_health_test_phosphate,
        WaterTestParameterId.TDS to R.string.tank_health_test_tds,
        WaterTestParameterId.EC to R.string.tank_health_test_conductivity,
        WaterTestParameterId.CO2 to R.string.tank_health_test_carbon_dioxide,
        WaterTestParameterId.IRON to R.string.tank_health_test_iron,
        WaterTestParameterId.POTASSIUM to R.string.tank_health_test_potassium,
        WaterTestParameterId.SALINITY to R.string.tank_health_test_salinity,
        WaterTestParameterId.SPECIFIC_GRAVITY to R.string.tank_health_test_specific_gravity,
        WaterTestParameterId.CALCIUM to R.string.tank_health_test_calcium,
        WaterTestParameterId.MAGNESIUM to R.string.tank_health_test_magnesium,
        WaterTestParameterId.COPPER to R.string.tank_health_test_copper,
        WaterTestParameterId.DISSOLVED_OXYGEN to R.string.tank_health_test_dissolved_oxygen
    )

    private val symbolResByParameter = mapOf(
        WaterTestParameterId.PH to R.string.tank_health_test_ph,
        WaterTestParameterId.NITRATE to R.string.tank_health_test_symbol_nitrate,
        WaterTestParameterId.NITRITE to R.string.tank_health_test_symbol_nitrite,
        WaterTestParameterId.AMMONIA_AMMONIUM to R.string.tank_health_test_symbol_ammonia_ammonium,
        WaterTestParameterId.GH to R.string.tank_health_test_symbol_gh,
        WaterTestParameterId.KH to R.string.tank_health_test_symbol_kh,
        WaterTestParameterId.PHOSPHATE to R.string.tank_health_test_symbol_phosphate,
        WaterTestParameterId.TDS to R.string.tank_health_test_symbol_tds,
        WaterTestParameterId.EC to R.string.tank_health_test_symbol_ec,
        WaterTestParameterId.CO2 to R.string.tank_health_test_symbol_co2,
        WaterTestParameterId.IRON to R.string.tank_health_test_symbol_iron,
        WaterTestParameterId.POTASSIUM to R.string.tank_health_test_symbol_potassium,
        WaterTestParameterId.SPECIFIC_GRAVITY to R.string.tank_health_test_symbol_specific_gravity,
        WaterTestParameterId.CALCIUM to R.string.tank_health_test_symbol_calcium,
        WaterTestParameterId.MAGNESIUM to R.string.tank_health_test_symbol_magnesium,
        WaterTestParameterId.COPPER to R.string.tank_health_test_symbol_copper,
        WaterTestParameterId.DISSOLVED_OXYGEN to R.string.tank_health_test_symbol_oxygen
    )

    private val unitResByParameter = mapOf(
        WaterTestParameterId.NITRATE to R.string.tank_health_analysis_unit_mg_l,
        WaterTestParameterId.NITRITE to R.string.tank_health_analysis_unit_mg_l,
        WaterTestParameterId.AMMONIA_AMMONIUM to R.string.tank_health_analysis_unit_mg_l,
        WaterTestParameterId.GH to R.string.tank_health_analysis_unit_dgh,
        WaterTestParameterId.KH to R.string.tank_health_analysis_unit_dkh,
        WaterTestParameterId.PHOSPHATE to R.string.tank_health_analysis_unit_mg_l,
        WaterTestParameterId.TDS to R.string.tank_health_analysis_unit_ppm,
        WaterTestParameterId.EC to R.string.tank_health_analysis_unit_us_cm,
        WaterTestParameterId.CO2 to R.string.tank_health_analysis_unit_mg_l,
        WaterTestParameterId.IRON to R.string.tank_health_analysis_unit_mg_l,
        WaterTestParameterId.POTASSIUM to R.string.tank_health_analysis_unit_mg_l,
        WaterTestParameterId.SALINITY to R.string.tank_health_analysis_unit_ppt,
        WaterTestParameterId.CALCIUM to R.string.tank_health_analysis_unit_mg_l,
        WaterTestParameterId.MAGNESIUM to R.string.tank_health_analysis_unit_mg_l,
        WaterTestParameterId.COPPER to R.string.tank_health_analysis_unit_mg_l,
        WaterTestParameterId.DISSOLVED_OXYGEN to R.string.tank_health_analysis_unit_mg_l
    )

    fun model(
        tankProfile: String,
        id: WaterTestParameterId,
        importance: WaterTestImportance,
        value: String
    ): WaterTestParameterUiModel {
        val isMarineKh = id == WaterTestParameterId.KH &&
            AquariumTankTaxonomy.environmentForTankType(tankProfile) ==
            AquariumTankTaxonomy.WATER_ENVIRONMENT_MARINE
        val nameRes = if (isMarineKh) {
            R.string.tank_health_test_alkalinity
        } else {
            requireNotNull(nameResByParameter[id])
        }

        return WaterTestParameterUiModel(
            id = id,
            iconRes = requireNotNull(iconResByParameter[id]),
            nameRes = nameRes,
            symbolRes = symbolResByParameter[id],
            unitRes = unitResByParameter[id],
            importance = importance,
            value = value
        )
    }

}

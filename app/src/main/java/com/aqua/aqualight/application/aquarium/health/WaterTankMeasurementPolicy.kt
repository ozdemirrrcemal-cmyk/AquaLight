package com.aqua.aqualight.application.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy

data class WaterTankMeasurementScope(
    val recommended: List<WaterParameter>,
    val additional: List<WaterParameter>
) {
    init {
        require(recommended.distinct().size == recommended.size)
        require(additional.distinct().size == additional.size)
        require(recommended.none(additional::contains))
    }
}

/** One tank-type field policy for the UI, input validation and assessment coverage. */
object WaterTankMeasurementPolicy {
    private val freshwaterFish = WaterTankMeasurementScope(
        recommended = listOf(
            WaterParameter.PH, WaterParameter.AMMONIA_AMMONIUM,
            WaterParameter.NITRITE, WaterParameter.NITRATE,
            WaterParameter.GH, WaterParameter.KH
        ),
        additional = listOf(
            WaterParameter.PHOSPHATE, WaterParameter.TDS,
            WaterParameter.EC, WaterParameter.DISSOLVED_OXYGEN
        )
    )
    private val planted = WaterTankMeasurementScope(
        recommended = listOf(
            WaterParameter.PH, WaterParameter.NITRATE,
            WaterParameter.PHOSPHATE, WaterParameter.GH, WaterParameter.KH
        ),
        additional = listOf(
            WaterParameter.AMMONIA_AMMONIUM, WaterParameter.NITRITE,
            WaterParameter.CO2, WaterParameter.IRON, WaterParameter.POTASSIUM,
            WaterParameter.TDS, WaterParameter.EC, WaterParameter.DISSOLVED_OXYGEN
        )
    )
    private val shrimp = WaterTankMeasurementScope(
        recommended = listOf(
            WaterParameter.PH, WaterParameter.AMMONIA_AMMONIUM,
            WaterParameter.NITRITE, WaterParameter.NITRATE,
            WaterParameter.GH, WaterParameter.KH, WaterParameter.TDS
        ),
        additional = listOf(
            WaterParameter.EC, WaterParameter.PHOSPHATE,
            WaterParameter.COPPER, WaterParameter.DISSOLVED_OXYGEN
        )
    )
    private val brackish = WaterTankMeasurementScope(
        recommended = listOf(
            WaterParameter.PH, WaterParameter.AMMONIA_AMMONIUM,
            WaterParameter.NITRITE, WaterParameter.NITRATE,
            WaterParameter.SALINITY, WaterParameter.KH
        ),
        additional = listOf(
            WaterParameter.GH, WaterParameter.PHOSPHATE,
            WaterParameter.SPECIFIC_GRAVITY, WaterParameter.DISSOLVED_OXYGEN
        )
    )
    private val marineFish = WaterTankMeasurementScope(
        recommended = listOf(
            WaterParameter.PH, WaterParameter.AMMONIA_AMMONIUM,
            WaterParameter.NITRITE, WaterParameter.NITRATE,
            WaterParameter.SALINITY, WaterParameter.KH, WaterParameter.PHOSPHATE
        ),
        additional = listOf(
            WaterParameter.SPECIFIC_GRAVITY, WaterParameter.CALCIUM,
            WaterParameter.MAGNESIUM, WaterParameter.DISSOLVED_OXYGEN
        )
    )
    private val reef = WaterTankMeasurementScope(
        recommended = listOf(
            WaterParameter.PH, WaterParameter.NITRATE,
            WaterParameter.PHOSPHATE, WaterParameter.SALINITY,
            WaterParameter.KH, WaterParameter.CALCIUM, WaterParameter.MAGNESIUM
        ),
        additional = listOf(
            WaterParameter.AMMONIA_AMMONIUM, WaterParameter.NITRITE,
            WaterParameter.SPECIFIC_GRAVITY, WaterParameter.DISSOLVED_OXYGEN
        )
    )

    fun scopeFor(tankType: String): WaterTankMeasurementScope? = when (tankType) {
        AquariumTankTaxonomy.TYPE_FRESHWATER_FISH,
        AquariumTankTaxonomy.TYPE_OTHER_FRESHWATER -> freshwaterFish
        AquariumTankTaxonomy.TYPE_PLANTED -> planted
        AquariumTankTaxonomy.TYPE_SHRIMP -> shrimp
        AquariumTankTaxonomy.TYPE_BRACKISH_GENERAL,
        AquariumTankTaxonomy.TYPE_OTHER_BRACKISH -> brackish
        AquariumTankTaxonomy.TYPE_MARINE_FISH,
        AquariumTankTaxonomy.TYPE_OTHER_MARINE -> marineFish
        AquariumTankTaxonomy.TYPE_SOFT_CORAL_REEF,
        AquariumTankTaxonomy.TYPE_LPS_REEF,
        AquariumTankTaxonomy.TYPE_SPS_REEF,
        AquariumTankTaxonomy.TYPE_MIXED_REEF -> reef
        else -> null
    }
}

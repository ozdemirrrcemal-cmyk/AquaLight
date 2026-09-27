package com.aqua.aqualight.application.aquarium.health.water

import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.LivestockParameterRange
import com.aqua.aqualight.application.aquarium.LivestockWaterRequirements
import com.aqua.aqualight.application.aquarium.health.context.HealthPlantContext

internal object WaterAssessmentRequirements {
    val comparable = setOf(AquariumWaterParameter.TEMPERATURE_C, AquariumWaterParameter.PH,
        AquariumWaterParameter.GH_DGH)
    val plantScope = listOf(AquariumWaterParameter.TEMPERATURE_C, AquariumWaterParameter.PH,
        AquariumWaterParameter.GH_DGH, AquariumWaterParameter.KH_DKH)

    fun plant(plant: HealthPlantContext): LivestockWaterRequirements {
        val care = plant.care ?: return LivestockWaterRequirements()
        fun range(low: Double?, high: Double?, lowKey: String, highKey: String): LivestockParameterRange? {
            val minimum = low.takeIf { lowKey in care.verifiedCareFields }
            val maximum = high.takeIf { highKey in care.verifiedCareFields }
            return if (minimum == null && maximum == null) null else LivestockParameterRange(minimum, maximum)
        }
        return LivestockWaterRequirements(
            temperatureC = range(care.temperatureMinC, care.temperatureMaxC, "temperatureMinC", "temperatureMaxC"),
            ph = range(care.pHMin, care.pHMax, "pHMin", "pHMax"),
            ghDgh = range(care.ghMin, care.ghMax, "GHMin_dGH", "GHMax_dGH")
        )
    }

    fun range(requirements: LivestockWaterRequirements, parameter: AquariumWaterParameter): LivestockParameterRange? =
        when (parameter) {
            AquariumWaterParameter.TEMPERATURE_C -> requirements.temperatureC
            AquariumWaterParameter.PH -> requirements.ph
            AquariumWaterParameter.GH_DGH -> requirements.ghDgh
            AquariumWaterParameter.KH_DKH -> requirements.khDkh
            AquariumWaterParameter.TDS_PPM -> requirements.tdsPpm
            AquariumWaterParameter.SPECIFIC_GRAVITY -> requirements.specificGravity
            AquariumWaterParameter.ALKALINITY_DKH -> requirements.alkalinityDkh
            AquariumWaterParameter.CALCIUM_PPM -> requirements.calciumPpm
            AquariumWaterParameter.MAGNESIUM_PPM -> requirements.magnesiumPpm
            AquariumWaterParameter.NITRATE_PPM -> requirements.nitratePpm
            AquariumWaterParameter.PHOSPHATE_PPM -> requirements.phosphatePpm
            AquariumWaterParameter.PAR -> requirements.par
        }
}

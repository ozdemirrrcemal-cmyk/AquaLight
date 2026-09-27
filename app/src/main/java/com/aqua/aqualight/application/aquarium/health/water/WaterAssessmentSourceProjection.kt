package com.aqua.aqualight.application.aquarium.health.water

import com.aqua.aqualight.application.aquarium.AquariumWaterSnapshot
import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementNormalizer
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource

/** Adapts only resolved semantics to the existing comparator's documented source units. */
internal object WaterAssessmentSourceProjection {
    private const val MG_CACO3_PER_DEGREE_GH = 17.86

    private val parameters = mapOf(
        WaterParameter.PH to AquariumWaterParameter.PH, WaterParameter.GH to AquariumWaterParameter.GH_DGH,
        WaterParameter.KH to AquariumWaterParameter.KH_DKH, WaterParameter.TDS to AquariumWaterParameter.TDS_PPM,
        WaterParameter.SPECIFIC_GRAVITY to AquariumWaterParameter.SPECIFIC_GRAVITY,
        WaterParameter.TOTAL_ALKALINITY to AquariumWaterParameter.ALKALINITY_DKH,
        WaterParameter.CALCIUM to AquariumWaterParameter.CALCIUM_PPM,
        WaterParameter.MAGNESIUM to AquariumWaterParameter.MAGNESIUM_PPM,
        WaterParameter.NITRATE to AquariumWaterParameter.NITRATE_PPM,
        WaterParameter.PHOSPHATE to AquariumWaterParameter.PHOSPHATE_PPM
    )

    fun recordedParameters(input: WaterAnalysisInput): Set<AquariumWaterParameter> = buildSet {
        input.measurements.mapNotNull { parameters[it.parameter] }.forEach(::add)
        if (input.temperatureCelsius != null) add(AquariumWaterParameter.TEMPERATURE_C)
    }
    fun project(input: WaterAnalysisInput): AquariumWaterSnapshot {
        val values = input.measurements.associate { measurement ->
            measurement.parameter to WaterMeasurementNormalizer.canonicalValueForStoredSource(
                measurement.parameter, measurement.value, measurement.selection
            )
        }
        val gh = input.measurements.singleOrNull { it.parameter == WaterParameter.GH }
        val ghDgh = values[WaterParameter.GH]?.let { canonical ->
            if (gh?.selection?.unit == WaterMeasurementUnit.DGH) gh.value else canonical / MG_CACO3_PER_DEGREE_GH
        }
        return AquariumWaterSnapshot(
            temperatureC = input.temperatureCelsius.takeIf { input.temperatureSource == WaterTemperatureSource.MANUAL },
            ph = values[WaterParameter.PH],
            ghDgh = ghDgh,
            nitratePpm = values[WaterParameter.NITRATE],
            phosphatePpm = values[WaterParameter.PHOSPHATE]
            // Unspecified TDS scale, SG calibration, KH scope and elemental/catalog ppm
            // compatibility must not acquire authority from the comparator's numeric slots.
        )
    }
}

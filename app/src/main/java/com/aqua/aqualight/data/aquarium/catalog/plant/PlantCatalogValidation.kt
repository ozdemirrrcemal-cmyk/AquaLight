package com.aqua.aqualight.data.aquarium.catalog.plant

import com.google.gson.JsonObject

internal object PlantCatalogValidation {
    private val numericRanges = listOf(
        "temperatureMinC" to "temperatureMaxC", "pHMin" to "pHMax",
        "KHMin_dKH" to "KHMax_dKH", "GHMin_dGH" to "GHMax_dGH",
        "temperatureToleranceMinC" to "temperatureToleranceMaxC",
        "temperatureOptimalMinC" to "temperatureOptimalMaxC", "co2MinMgL" to "co2MaxMgL"
    )
    private val verifiedKeys = numericRanges.flatMap { listOf(it.first, it.second) }.toSet() + setOf(
        "canGrowEmersed", "co2Requirement", "difficulty", "growthForm", "growthRate",
        "lightRequirement", "nutrientDemand", "plantingMethod", "rootFeeder",
        "substrateRequirement", "temperatureRangeBasis", "waterColumnFeeder"
    )

    fun validate(row: JsonObject) {
        require(row.get("recordId").asString.matches(Regex("plant-[a-z0-9]+(?:-[a-z0-9]+)*")))
        require(row.get("canonicalScientificName").asString.isNotBlank())
        numericRanges.forEach { (minimum, maximum) -> validateRange(row, minimum, maximum) }
        val verified = row.getAsJsonArray("verifiedCareFields").map { it.asString }
        require(verified.distinct().size == verified.size)
        verified.forEach { key ->
            require(key in verifiedKeys) { "Unknown verified plant care field." }
            val value = row.get(key)
            require(value != null && !value.isJsonNull && value.isJsonPrimitive)
            require(value.asString.isNotBlank() && value.asString != "UNKNOWN")
        }
        val status = row.get("healthDataStatus").asString
        require(status in setOf("VERIFIED", "PARTIAL"))
        require(row.get("healthAnalysisReady").asBoolean == (status == "VERIFIED"))
        require(status != "VERIFIED" || verified.isNotEmpty())
    }

    private fun validateRange(row: JsonObject, minimum: String, maximum: String) {
        val lower = row.get(minimum)?.takeUnless { it.isJsonNull }?.asDouble
        val upper = row.get(maximum)?.takeUnless { it.isJsonNull }?.asDouble
        require(lower?.isFinite() != false && upper?.isFinite() != false)
        require(lower == null || upper == null || lower <= upper) { "Reversed plant care range." }
    }
}

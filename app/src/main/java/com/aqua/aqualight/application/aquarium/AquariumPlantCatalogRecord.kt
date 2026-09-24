package com.aqua.aqualight.application.aquarium


/** Plant identity and care facts loaded from the single packaged catalog. */
data class AquariumPlantCare(
    val lightRequirement: String,
    val co2Requirement: String,
    val difficulty: String,
    val growthRate: String,
    val temperatureMinC: Double?, val temperatureMaxC: Double?,
    val pHMin: Double?, val pHMax: Double?,
    val khMin: Double?, val khMax: Double?,
    val ghMin: Double?, val ghMax: Double?,
    val nutrientDemand: String,
    val substrateRequirement: String,
    val rootFeeder: Boolean?,
    val waterColumnFeeder: Boolean?,
    val healthDataStatus: String,
    val healthAnalysisReady: Boolean,
    val verifiedCareFields: Set<String>
)

data class AquariumPlantCatalogRecord(
    val id: String,
    val displayName: String,
    val canonicalScientificName: String,
    val placement: Set<String>,
    val growthForm: String,
    val care: AquariumPlantCare
)

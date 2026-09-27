package com.aqua.aqualight.application.aquarium.health.water

/** Exact catalog vocabulary, not a guess from a species name or a UI category. */
internal object WaterHabitatPolicy {
    private val freshwater = setOf("Freshwater", "Freshwater / soft", "Freshwater / cool", "Freshwater / Tanganyika",
        "Freshwater / Malawi", "Freshwater / Rift Lake", "Freshwater / blackwater", "Freshwater / Sulawesi",
        "Amphibian / freshwater", "Freshwater / hard", "Freshwater / pond", "Freshwater / warm-soft",
        "Paludarium / freshwater")
    private val marine = setOf("Marine", "Marine / reef-sensitive", "LPS", "SPS", "Soft coral", "Marine echinoderm",
        "Marine / LPS", "Marine anemone", "Marine / Soft coral", "Marine / SPS", "Marine / seahorse", "NPS coral",
        "Marine bivalve", "Marine shrimp", "Marine / Corallimorph", "Marine / SPS / plating", "Marine / SPS/LPS",
        "Marine / LPS/SPS-like", "Marine polychaete")
    private val brackish = setOf("Brackish", "Brackish paludarium", "Paludarium / brackish")
    private val mixed = setOf("Freshwater / low brackish", "Freshwater / brackish tolerant")

    fun gap(tankEnvironment: String?, waterGroup: String?): WaterAssessmentGap? {
        if (tankEnvironment == null) return WaterAssessmentGap.UNKNOWN_TANK_TYPE
        val supported = when (waterGroup) {
            in freshwater -> setOf("Freshwater")
            in marine -> setOf("Marine")
            in brackish -> setOf("Brackish")
            in mixed -> setOf("Freshwater", "Brackish")
            else -> emptySet()
        }
        return when {
            supported.isEmpty() -> WaterAssessmentGap.UNKNOWN_HABITAT
            tankEnvironment in supported -> null
            else -> WaterAssessmentGap.INCOMPATIBLE_HABITAT
        }
    }
}

package com.aqua.aqualight.application.aquarium.lighting

/** Explicit plant-mass classification. It is never inferred from marker count. */
enum class PlantDensity {
    LOW,
    MEDIUM,
    HIGH
}

/** Highest lighting demand among the plants that are actually in the aquarium. */
enum class PlantLightDemand {
    LOW,
    MEDIUM,
    HIGH
}

/** Installation and operating state are deliberately distinct. */
enum class Co2Status {
    NONE,
    INSTALLED,
    ACTIVE
}

/** User-confirmed observation severity; absence of a value means not observed. */
enum class AquariumObservationSeverity {
    NONE,
    MILD,
    SIGNIFICANT
}

/**
 * Aquarium-owned semantic facts required by Smart Light automation.
 *
 * Nullable fields are genuinely unknown. Callers must surface them as missing data and must not
 * replace them with guesses derived from names, notes, material labels, or UI copy.
 */
data class AquariumLightingProfile(
    val plantDensity: PlantDensity? = null,
    val highestPlantLightDemand: PlantLightDemand? = null,
    val co2Status: Co2Status? = null,
    val isActiveSoil: Boolean? = null,
    val waterDepthCm: Int? = null,
    val fixtureMountHeightCm: Int? = null,
    val preferredViewingStartMinuteOfDay: Int? = null,
    val preferredViewingEndMinuteOfDay: Int? = null,
    val algaeObservation: AquariumObservationSeverity? = null,
    val plantStressObservation: AquariumObservationSeverity? = null,
    val observationDateEpochDay: Long? = null
) {
    init {
        waterDepthCm?.let { value -> require(value in WATER_DEPTH_CM_RANGE) }
        fixtureMountHeightCm?.let { value -> require(value in FIXTURE_HEIGHT_CM_RANGE) }
        preferredViewingStartMinuteOfDay?.let { value -> require(value in MINUTE_OF_DAY_RANGE) }
        preferredViewingEndMinuteOfDay?.let { value -> require(value in MINUTE_OF_DAY_RANGE) }
        require(
            (preferredViewingStartMinuteOfDay == null) ==
                (preferredViewingEndMinuteOfDay == null)
        ) {
            "Preferred viewing-window boundaries must be provided together."
        }
        if (
            preferredViewingStartMinuteOfDay != null &&
            preferredViewingEndMinuteOfDay != null
        ) {
            require(preferredViewingEndMinuteOfDay > preferredViewingStartMinuteOfDay) {
                "The Smart Light viewing window must be same-day and non-empty."
            }
        }
        val hasObservation = algaeObservation != null || plantStressObservation != null
        require(hasObservation == (observationDateEpochDay != null)) {
            "Aquarium observations and their explicit date must be provided together."
        }
    }

    companion object {
        val WATER_DEPTH_CM_RANGE = 5..200
        val FIXTURE_HEIGHT_CM_RANGE = 0..200
        val MINUTE_OF_DAY_RANGE = 0 until 24 * 60
    }
}

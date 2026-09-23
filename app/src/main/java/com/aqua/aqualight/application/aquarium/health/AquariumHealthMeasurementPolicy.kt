package com.aqua.aqualight.application.aquarium.health

object AquariumHealthMeasurementPolicy {

    const val MAX_NOTE_CHARS = 2_000
    const val MAX_OWNER_UID_CHARS = 128

    private const val MIN_TIMESTAMP_MILLIS = 946_684_800_000L
    private const val MAX_TIMESTAMP_MILLIS = 4_102_444_800_000L

    private const val ZERO = 0.0
    private const val MIN_TEMPERATURE_C = -2.0
    private const val MAX_TEMPERATURE_C = 60.0
    private const val MAX_PH = 14.0
    private const val MAX_HARDNESS_DH = 100.0
    private const val MAX_TDS_PPM = 100_000.0
    private const val MAX_TOTAL_AMMONIA_PPM = 10_000.0
    private const val MAX_NITRITE_PPM = 10_000.0
    private const val MAX_NITRATE_PPM = 100_000.0
    private const val MAX_PHOSPHATE_PPM = 10_000.0
    private const val MAX_DISSOLVED_OXYGEN_MG_L = 50.0
    private const val MAX_CO2_MG_L = 500.0
    private const val MIN_SPECIFIC_GRAVITY = 0.9
    private const val MAX_SPECIFIC_GRAVITY = 1.2
    private const val MAX_CALCIUM_PPM = 5_000.0
    private const val MAX_MAGNESIUM_PPM = 10_000.0
    private const val MAX_PAR_UMOL_M2_S = 10_000.0

    private val parameterBounds: Map<HealthWaterParameter, ClosedFloatingPointRange<Double>> =
        mapOf(
            HealthWaterParameter.TEMPERATURE_C to MIN_TEMPERATURE_C..MAX_TEMPERATURE_C,
            HealthWaterParameter.PH to ZERO..MAX_PH,
            HealthWaterParameter.GH_DGH to ZERO..MAX_HARDNESS_DH,
            HealthWaterParameter.KH_DKH to ZERO..MAX_HARDNESS_DH,
            HealthWaterParameter.TDS_PPM to ZERO..MAX_TDS_PPM,
            HealthWaterParameter.TOTAL_AMMONIA_PPM to ZERO..MAX_TOTAL_AMMONIA_PPM,
            HealthWaterParameter.NITRITE_PPM to ZERO..MAX_NITRITE_PPM,
            HealthWaterParameter.NITRATE_PPM to ZERO..MAX_NITRATE_PPM,
            HealthWaterParameter.PHOSPHATE_PPM to ZERO..MAX_PHOSPHATE_PPM,
            HealthWaterParameter.DISSOLVED_OXYGEN_MG_L to ZERO..MAX_DISSOLVED_OXYGEN_MG_L,
            HealthWaterParameter.CO2_MG_L to ZERO..MAX_CO2_MG_L,
            HealthWaterParameter.SPECIFIC_GRAVITY to MIN_SPECIFIC_GRAVITY..MAX_SPECIFIC_GRAVITY,
            HealthWaterParameter.ALKALINITY_DKH to ZERO..MAX_HARDNESS_DH,
            HealthWaterParameter.CALCIUM_PPM to ZERO..MAX_CALCIUM_PPM,
            HealthWaterParameter.MAGNESIUM_PPM to ZERO..MAX_MAGNESIUM_PPM,
            HealthWaterParameter.PAR_UMOL_M2_S to ZERO..MAX_PAR_UMOL_M2_S
        )

    fun validateWaterTestInput(
        input: AquariumWaterTestInput,
        nowMillis: Long = System.currentTimeMillis()
    ): AquariumWaterTestInput {
        requirePositiveId("tankId", input.tankId)
        requireObservationTimestamp(
            field = "measuredAtMillis",
            value = input.measuredAtMillis,
            nowMillis = nowMillis
        )
        validateReadings(input.readings)
        requireCanonicalOptionalText("note", input.note, MAX_NOTE_CHARS)
        return input
    }

    fun validateObservationInput(
        input: LivestockHealthObservationInput,
        nowMillis: Long = System.currentTimeMillis()
    ): LivestockHealthObservationInput {
        requirePositiveId("tankId", input.tankId)
        input.livestockId?.let { id -> requirePositiveId("livestockId", id) }
        LivestockHealthSymptomCatalog.requireApplicable(
            symptomKey = input.symptomKey,
            categoryKey = input.categoryKey
        )
        requireObservationTimestamp(
            field = "observedAtMillis",
            value = input.observedAtMillis,
            nowMillis = nowMillis
        )
        requireCanonicalOptionalText("note", input.note, MAX_NOTE_CHARS)
        return input
    }

    fun validatePlantObservationInput(
        input: PlantHealthObservationInput,
        nowMillis: Long = System.currentTimeMillis()
    ): PlantHealthObservationInput {
        requirePositiveId("tankId", input.tankId)
        input.plantId?.let { id -> requirePositiveId("plantId", id) }
        PlantHealthSymptomCatalog.requireValidSelection(
            symptomKey = input.symptomKey,
            algaeTypeKey = input.algaeTypeKey
        )
        requireObservationTimestamp(
            field = "observedAtMillis",
            value = input.observedAtMillis,
            nowMillis = nowMillis
        )
        requireCanonicalOptionalText("note", input.note, MAX_NOTE_CHARS)
        return input
    }

    fun validateReadings(readings: List<AquariumWaterReading>) {
        require(readings.isNotEmpty()) {
            "A water test must contain at least one reading."
        }

        val parameters = mutableSetOf<HealthWaterParameter>()
        readings.forEach { reading ->
            require(parameters.add(reading.parameter)) {
                "A water test cannot contain duplicate ${reading.parameter.name} readings."
            }
            require(reading.value.isFinite()) {
                "${reading.parameter.name} must be finite."
            }
            val bounds = checkNotNull(parameterBounds[reading.parameter])
            require(reading.value in bounds) {
                "${reading.parameter.name} is outside the supported persistence range."
            }
        }
    }

    fun requireStoredTimestamp(field: String, value: Long) {
        require(value in MIN_TIMESTAMP_MILLIS..MAX_TIMESTAMP_MILLIS) {
            "$field is outside the supported commercial timestamp range."
        }
    }

    fun requireCanonicalOwnerUid(value: String): String {
        val canonical = value.trim()
        require(
            canonical.isNotBlank() &&
                canonical == value &&
                canonical.length <= MAX_OWNER_UID_CHARS
        ) {
            "ownerUid must be canonical and non-blank."
        }
        return canonical
    }

    fun requirePositiveId(field: String, value: Long) {
        require(value > 0L) {
            "$field must be positive."
        }
    }

    fun requireCanonicalOptionalText(
        field: String,
        value: String,
        maxChars: Int
    ) {
        require(value == value.trim()) {
            "$field must be canonical."
        }
        require(value.length <= maxChars) {
            "$field exceeds $maxChars characters."
        }
    }

    private fun requireObservationTimestamp(
        field: String,
        value: Long,
        nowMillis: Long
    ) {
        requireStoredTimestamp(field, value)
        require(nowMillis in MIN_TIMESTAMP_MILLIS..MAX_TIMESTAMP_MILLIS) {
            "Reference time is outside the supported commercial timestamp range."
        }
        require(value <= nowMillis) {
            "$field must not be in the future."
        }
    }
}

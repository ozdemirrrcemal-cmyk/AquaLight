package com.aqua.aqualight.application.aquarium.health

object AquariumHealthMeasurementPolicy {

    const val MAX_NOTE_CHARS = 2_000
    const val MAX_OWNER_UID_CHARS = 128

    private const val MIN_TIMESTAMP_MILLIS = 946_684_800_000L
    private const val MAX_TIMESTAMP_MILLIS = 4_102_444_800_000L

    private val parameterBounds: Map<HealthWaterParameter, ClosedFloatingPointRange<Double>> =
        mapOf(
            HealthWaterParameter.TEMPERATURE_C to -2.0..60.0,
            HealthWaterParameter.PH to 0.0..14.0,
            HealthWaterParameter.GH_DGH to 0.0..100.0,
            HealthWaterParameter.KH_DKH to 0.0..100.0,
            HealthWaterParameter.TDS_PPM to 0.0..100_000.0,
            HealthWaterParameter.TOTAL_AMMONIA_PPM to 0.0..10_000.0,
            HealthWaterParameter.NITRITE_PPM to 0.0..10_000.0,
            HealthWaterParameter.NITRATE_PPM to 0.0..100_000.0,
            HealthWaterParameter.PHOSPHATE_PPM to 0.0..10_000.0,
            HealthWaterParameter.DISSOLVED_OXYGEN_MG_L to 0.0..50.0,
            HealthWaterParameter.CO2_MG_L to 0.0..500.0,
            HealthWaterParameter.SPECIFIC_GRAVITY to 0.9..1.2,
            HealthWaterParameter.ALKALINITY_DKH to 0.0..100.0,
            HealthWaterParameter.CALCIUM_PPM to 0.0..5_000.0,
            HealthWaterParameter.MAGNESIUM_PPM to 0.0..10_000.0,
            HealthWaterParameter.PAR_UMOL_M2_S to 0.0..10_000.0
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

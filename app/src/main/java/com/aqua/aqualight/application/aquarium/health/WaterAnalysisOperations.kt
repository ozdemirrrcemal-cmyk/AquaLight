package com.aqua.aqualight.application.aquarium.health

import kotlinx.coroutines.flow.Flow

interface WaterAnalysisOperations {
    fun analysesForTank(tankId: Long): Flow<List<WaterAnalysisSnapshot>>
    fun analysis(analysisId: Long): Flow<WaterAnalysisSnapshot?>
    suspend fun saveAnalysis(input: WaterAnalysisInput): Long
    suspend fun deleteAnalysis(analysisId: Long)
}

data class WaterAnalysisInput(
    val tankId: Long,
    val measuredAtMillis: Long,
    val temperatureCelsius: Double?,
    val temperatureSource: WaterTemperatureSource?,
    val measurements: List<WaterMeasurementInput>
)

data class WaterMeasurementInput(
    val parameter: WaterParameter,
    val value: Double,
    val selection: WaterMeasurementSelection
)

data class WaterAnalysisSnapshot(
    val id: Long,
    val tankId: Long,
    val measuredAtMillis: Long,
    val temperatureCelsius: Double?,
    val temperatureSource: WaterTemperatureSource?,
    val measurements: List<WaterMeasurementSnapshot>,
    val createdAtMillis: Long
)

data class WaterMeasurementSnapshot(
    val parameter: WaterParameter,
    val value: Double,
    val method: WaterMeasurementMethod,
    val testKitId: String?,
    val basis: WaterMeasurementBasis,
    val unit: WaterMeasurementUnit,
    val canonicalValue: Double?,
    val canonicalBasis: WaterMeasurementBasis,
    val canonicalUnit: WaterMeasurementUnit
)

data class WaterMeasurementSelection(
    val method: WaterMeasurementMethod,
    val testKitId: String?,
    val basis: WaterMeasurementBasis,
    val unit: WaterMeasurementUnit
)

enum class WaterParameter {
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

enum class WaterMeasurementMethod {
    MANUAL,
    TEST_KIT,
    DIGITAL,
    SENSOR
}

enum class WaterMeasurementBasis(val id: String) {
    PH("ph"),
    NO3("no3"),
    NO3_N("no3_n"),
    NO2("no2"),
    NH3_NH4("nh3_nh4"),
    TAN("tan"),
    GH("gh"),
    KH("kh"),
    PO4("po4"),
    P("p"),
    TDS("tds"),
    EC("ec"),
    CO2("co2"),
    FE("fe"),
    K("k"),
    SALINITY("salinity"),
    SG("sg"),
    CA("ca"),
    MG("mg"),
    CU("cu"),
    O2("o2");

    companion object {
        fun fromId(id: String): WaterMeasurementBasis? =
            entries.firstOrNull { basis -> basis.id == id }
    }
}

enum class WaterMeasurementUnit(val id: String) {
    NONE("none"),
    MG_L("mg_l"),
    DGH("dgh"),
    DKH("dkh"),
    PPM("ppm"),
    US_CM("us_cm"),
    PPT("ppt"),
    MEQ_L("meq_l"),
    PPM_CACO3("ppm_caco3");

    companion object {
        fun fromId(id: String): WaterMeasurementUnit? =
            entries.firstOrNull { unit -> unit.id == id }
    }
}

enum class WaterTemperatureSource {
    MANUAL,
    SENSOR
}

internal object WaterAnalysisPolicy {
    const val MAX_MEASUREMENTS = 32
    const val MIN_DATE_MILLIS = 946_684_800_000L
    const val MAX_DATE_MILLIS = 4_102_444_800_000L
    const val FUTURE_TOLERANCE_MILLIS = 60_000L
    const val MIN_TEMPERATURE_C = -50.0
    const val MAX_TEMPERATURE_C = 100.0

    fun validate(
        input: WaterAnalysisInput,
        nowMillis: Long = System.currentTimeMillis()
    ): WaterAnalysisInput {
        require(input.tankId > 0L) { "tankId must be positive." }
        require(input.measuredAtMillis in MIN_DATE_MILLIS..MAX_DATE_MILLIS) {
            "measuredAtMillis is outside the supported range."
        }
        require(input.measuredAtMillis <= nowMillis + FUTURE_TOLERANCE_MILLIS) {
            "measuredAtMillis cannot be more than one minute in the future."
        }
        require(input.measurements.isNotEmpty()) {
            "A water analysis must contain at least one measurement."
        }
        require(input.measurements.size <= MAX_MEASUREMENTS) {
            "A water analysis contains too many measurements."
        }
        require(
            input.measurements.map { measurement -> measurement.parameter }.distinct().size ==
                input.measurements.size
        ) {
            "A water analysis cannot contain duplicate parameters."
        }

        if (input.temperatureCelsius == null) {
            require(input.temperatureSource == null) {
                "Temperature source requires a temperature value."
            }
        } else {
            require(input.temperatureCelsius.isFinite()) {
                "Temperature must be finite."
            }
            require(input.temperatureCelsius in MIN_TEMPERATURE_C..MAX_TEMPERATURE_C) {
                "Temperature is outside the supported range."
            }
            requireNotNull(input.temperatureSource) {
                "Temperature value requires a source."
            }
            require(input.temperatureSource != WaterTemperatureSource.SENSOR) {
                "Sensor temperature requires verified sample provenance."
            }
        }

        input.measurements.forEach { measurement ->
            require(measurement.value.isFinite() && measurement.value >= 0.0) {
                "Measurement values must be finite and non-negative."
            }
            require(
                WaterMeasurementCatalog.isSelectionValid(
                    parameter = measurement.parameter,
                    selection = measurement.selection
                )
            ) {
                "Measurement selection is not valid for ${measurement.parameter}."
            }
        }

        return input
    }
}

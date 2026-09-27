package com.aqua.aqualight.application.aquarium.health

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessment

interface WaterAnalysisOperations {
    fun analysesForTank(tankId: Long): Flow<List<WaterAnalysisSnapshot>>
    fun latestAnalysis(tankId: Long): Flow<WaterAnalysisSnapshot?>
    fun analysis(tankId: Long, analysisId: Long): Flow<WaterAnalysisSnapshot?>
    suspend fun saveAnalysis(input: WaterAnalysisInput): Long
    suspend fun deleteAnalysis(tankId: Long, analysisId: Long)
}

data class WaterAnalysisInput(
    val tankId: Long,
    val measuredAtMillis: Long,
    val temperatureCelsius: Double?,
    val temperatureSource: WaterTemperatureSource?,
    val measurements: List<WaterMeasurementInput>,
    val requestId: String = UUID.randomUUID().toString()
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
    val createdAtMillis: Long,
    val assessment: WaterQualityAssessment? = null,
    val contextCapturedAtMillis: Long? = null
) {
    init {
        require(measurements.all { measurement -> measurement.resultId.analysisId == id }) {
            "Water analysis results must belong to their observation."
        }
    }
}

/** Stable identity within an immutable analysis event; distinct analytes never overwrite each other. */
data class WaterMeasurementResultId(
    val analysisId: Long,
    val parameter: WaterParameter
) {
    init {
        require(analysisId > 0L) { "Water result analysisId must be positive." }
    }
}

data class WaterMeasurementSnapshot(
    val resultId: WaterMeasurementResultId,
    val parameter: WaterParameter,
    val value: Double,
    val method: WaterMeasurementMethod,
    val testKitId: String?,
    val basis: WaterMeasurementBasis,
    val unit: WaterMeasurementUnit,
    val canonicalValue: Double?,
    val canonicalBasis: WaterMeasurementBasis,
    val canonicalUnit: WaterMeasurementUnit
) {
    init {
        require(resultId.parameter == parameter) {
            "Water result identity must match its measured parameter."
        }
    }
    val semanticStatus: WaterMeasurementSemanticStatus
        get() = when {
            parameter == WaterParameter.AMMONIA_AMMONIUM ->
                WaterMeasurementSemanticStatus.LEGACY_UNASSESSED
            canonicalValue != null && WaterMeasurementNormalizer.hasCanonicalSemantics(parameter) ->
                WaterMeasurementSemanticStatus.SOURCE_TYPED
            else -> WaterMeasurementSemanticStatus.SOURCE_NATIVE_UNASSESSED
        }
}

enum class WaterMeasurementSemanticStatus {
    SOURCE_TYPED,
    SOURCE_NATIVE_UNASSESSED,
    LEGACY_UNASSESSED
}

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
    TOTAL_AMMONIA_NITROGEN,
    FREE_AMMONIA_NH3,
    GH,
    KH,
    TOTAL_ALKALINITY,
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
    TAN_N("tan_n"),
    FREE_NH3("free_nh3"),
    GH("gh"),
    KH("kh"),
    TOTAL_ALKALINITY("total_alkalinity"),
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
        require(isValidRequestId(input.requestId)) { "requestId must be a canonical UUID." }
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
            require(measurement.parameter != WaterParameter.AMMONIA_AMMONIUM) {
                "Legacy NH3/NH4 has no resolved analyte and cannot be a new measurement."
            }
            require(WaterMeasurementInputPolicy.accepts(measurement.parameter, measurement.value)) {
                "Measurement value is outside the supported input envelope."
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

    fun isValidRequestId(requestId: String): Boolean =
        runCatching { UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)
}

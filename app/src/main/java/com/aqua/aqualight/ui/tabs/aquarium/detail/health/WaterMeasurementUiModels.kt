package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import androidx.annotation.StringRes
import com.aqua.aqualight.R

internal enum class WaterMeasurementMethodUi {
    MANUAL,
    TEST_KIT,
    DIGITAL,
    SENSOR
}

internal data class WaterMeasurementSelectionUi(
    val method: WaterMeasurementMethodUi,
    val testKitId: String,
    val basisId: String,
    val unitId: String
)

internal data class WaterMeasurementOptionUi(
    val id: String,
    @StringRes val labelRes: Int
)

internal object WaterMeasurementUiCatalog {

    fun defaultSelection(parameterId: WaterTestParameterId): WaterMeasurementSelectionUi =
        WaterMeasurementSelectionUi(
            method = if (parameterId == WaterTestParameterId.NITRATE) {
                WaterMeasurementMethodUi.TEST_KIT
            } else {
                WaterMeasurementMethodUi.MANUAL
            },
            testKitId = if (parameterId == WaterTestParameterId.NITRATE) {
                KIT_SALIFERT_NITRATE
            } else {
                OPTION_NONE
            },
            basisId = canonicalBasis(parameterId).id,
            unitId = canonicalUnit(parameterId)?.id ?: UNIT_NONE
        )

    fun testKitOptions(parameterId: WaterTestParameterId): List<WaterMeasurementOptionUi> =
        buildList {
            add(WaterMeasurementOptionUi(OPTION_NONE, R.string.water_measurement_not_selected))
            if (parameterId == WaterTestParameterId.NITRATE) {
                add(
                    WaterMeasurementOptionUi(
                        KIT_SALIFERT_NITRATE,
                        R.string.water_measurement_kit_salifert_nitrate
                    )
                )
            }
            add(WaterMeasurementOptionUi(KIT_OTHER, R.string.water_measurement_kit_other))
        }

    fun basisOptions(parameterId: WaterTestParameterId): List<WaterMeasurementOptionUi> =
        when (parameterId) {
            WaterTestParameterId.NITRATE -> listOf(
                WaterMeasurementOptionUi(BASIS_NO3, R.string.water_measurement_basis_no3),
                WaterMeasurementOptionUi(BASIS_NO3_N, R.string.water_measurement_basis_no3_n)
            )
            WaterTestParameterId.PHOSPHATE -> listOf(
                WaterMeasurementOptionUi(BASIS_PO4, R.string.water_measurement_basis_po4),
                WaterMeasurementOptionUi(BASIS_P, R.string.water_measurement_basis_p)
            )
            WaterTestParameterId.AMMONIA_AMMONIUM -> listOf(
                WaterMeasurementOptionUi(
                    BASIS_NH3_NH4,
                    R.string.water_measurement_basis_nh3_nh4
                ),
                WaterMeasurementOptionUi(BASIS_TAN, R.string.water_measurement_basis_tan)
            )
            else -> listOf(canonicalBasis(parameterId))
        }

    fun unitOptions(parameterId: WaterTestParameterId): List<WaterMeasurementOptionUi> =
        when (parameterId) {
            WaterTestParameterId.GH -> listOf(
                WaterMeasurementOptionUi(UNIT_DGH, R.string.tank_health_analysis_unit_dgh),
                WaterMeasurementOptionUi(
                    UNIT_PPM_CACO3,
                    R.string.tank_health_analysis_unit_ppm_caco3
                )
            )
            WaterTestParameterId.KH -> listOf(
                WaterMeasurementOptionUi(UNIT_DKH, R.string.tank_health_analysis_unit_dkh),
                WaterMeasurementOptionUi(UNIT_MEQ_L, R.string.tank_health_analysis_unit_meq_l),
                WaterMeasurementOptionUi(
                    UNIT_PPM_CACO3,
                    R.string.tank_health_analysis_unit_ppm_caco3
                )
            )
            else -> canonicalUnit(parameterId)?.let(::listOf).orEmpty()
        }

    fun canonicalBasis(parameterId: WaterTestParameterId): WaterMeasurementOptionUi =
        when (parameterId) {
            WaterTestParameterId.PH ->
                WaterMeasurementOptionUi(BASIS_PH, R.string.tank_health_test_ph)
            WaterTestParameterId.NITRATE ->
                WaterMeasurementOptionUi(BASIS_NO3, R.string.water_measurement_basis_no3)
            WaterTestParameterId.NITRITE ->
                WaterMeasurementOptionUi(BASIS_NO2, R.string.tank_health_test_symbol_nitrite)
            WaterTestParameterId.AMMONIA_AMMONIUM ->
                WaterMeasurementOptionUi(
                    BASIS_NH3_NH4,
                    R.string.water_measurement_basis_nh3_nh4
                )
            WaterTestParameterId.GH ->
                WaterMeasurementOptionUi(BASIS_GH, R.string.tank_health_test_symbol_gh)
            WaterTestParameterId.KH ->
                WaterMeasurementOptionUi(BASIS_KH, R.string.tank_health_test_symbol_kh)
            WaterTestParameterId.PHOSPHATE ->
                WaterMeasurementOptionUi(BASIS_PO4, R.string.water_measurement_basis_po4)
            WaterTestParameterId.TDS ->
                WaterMeasurementOptionUi(BASIS_TDS, R.string.tank_health_test_symbol_tds)
            WaterTestParameterId.EC ->
                WaterMeasurementOptionUi(BASIS_EC, R.string.tank_health_test_symbol_ec)
            WaterTestParameterId.CO2 ->
                WaterMeasurementOptionUi(BASIS_CO2, R.string.tank_health_test_symbol_co2)
            WaterTestParameterId.IRON ->
                WaterMeasurementOptionUi(BASIS_FE, R.string.tank_health_test_symbol_iron)
            WaterTestParameterId.POTASSIUM ->
                WaterMeasurementOptionUi(BASIS_K, R.string.tank_health_test_symbol_potassium)
            WaterTestParameterId.SALINITY ->
                WaterMeasurementOptionUi(BASIS_SALINITY, R.string.tank_health_test_salinity)
            WaterTestParameterId.SPECIFIC_GRAVITY ->
                WaterMeasurementOptionUi(
                    BASIS_SG,
                    R.string.tank_health_test_symbol_specific_gravity
                )
            WaterTestParameterId.CALCIUM ->
                WaterMeasurementOptionUi(BASIS_CA, R.string.tank_health_test_symbol_calcium)
            WaterTestParameterId.MAGNESIUM ->
                WaterMeasurementOptionUi(BASIS_MG, R.string.tank_health_test_symbol_magnesium)
            WaterTestParameterId.COPPER ->
                WaterMeasurementOptionUi(BASIS_CU, R.string.tank_health_test_symbol_copper)
            WaterTestParameterId.DISSOLVED_OXYGEN ->
                WaterMeasurementOptionUi(BASIS_O2, R.string.tank_health_test_symbol_oxygen)
        }

    fun canonicalUnit(parameterId: WaterTestParameterId): WaterMeasurementOptionUi? =
        when (parameterId) {
            WaterTestParameterId.PH,
            WaterTestParameterId.SPECIFIC_GRAVITY -> null
            WaterTestParameterId.GH ->
                WaterMeasurementOptionUi(UNIT_DGH, R.string.tank_health_analysis_unit_dgh)
            WaterTestParameterId.KH ->
                WaterMeasurementOptionUi(UNIT_DKH, R.string.tank_health_analysis_unit_dkh)
            WaterTestParameterId.TDS ->
                WaterMeasurementOptionUi(UNIT_PPM, R.string.tank_health_analysis_unit_ppm)
            WaterTestParameterId.EC ->
                WaterMeasurementOptionUi(UNIT_US_CM, R.string.tank_health_analysis_unit_us_cm)
            WaterTestParameterId.SALINITY ->
                WaterMeasurementOptionUi(UNIT_PPT, R.string.tank_health_analysis_unit_ppt)
            else ->
                WaterMeasurementOptionUi(UNIT_MG_L, R.string.tank_health_analysis_unit_mg_l)
        }

    fun optionLabelRes(
        options: List<WaterMeasurementOptionUi>,
        selectedId: String
    ): Int = options.firstOrNull { option -> option.id == selectedId }
        ?.labelRes
        ?: options.first().labelRes

    const val OPTION_NONE = "none"
    const val KIT_SALIFERT_NITRATE = "salifert_nitrate"
    const val KIT_OTHER = "other"

    const val BASIS_PH = "ph"
    const val BASIS_NO3 = "no3"
    const val BASIS_NO3_N = "no3_n"
    const val BASIS_NO2 = "no2"
    const val BASIS_NH3_NH4 = "nh3_nh4"
    const val BASIS_TAN = "tan"
    const val BASIS_GH = "gh"
    const val BASIS_KH = "kh"
    const val BASIS_PO4 = "po4"
    const val BASIS_P = "p"
    const val BASIS_TDS = "tds"
    const val BASIS_EC = "ec"
    const val BASIS_CO2 = "co2"
    const val BASIS_FE = "fe"
    const val BASIS_K = "k"
    const val BASIS_SALINITY = "salinity"
    const val BASIS_SG = "sg"
    const val BASIS_CA = "ca"
    const val BASIS_MG = "mg"
    const val BASIS_CU = "cu"
    const val BASIS_O2 = "o2"

    const val UNIT_NONE = "none"
    const val UNIT_MG_L = "mg_l"
    const val UNIT_DGH = "dgh"
    const val UNIT_DKH = "dkh"
    const val UNIT_PPM = "ppm"
    const val UNIT_US_CM = "us_cm"
    const val UNIT_PPT = "ppt"
    const val UNIT_MEQ_L = "meq_l"
    const val UNIT_PPM_CACO3 = "ppm_caco3"
}

internal object WaterMeasurementUiStateCodec {
    private const val STATE_KEY = "water_measurement_ui_selections"
    private const val DELIMITER = "|"
    private const val PART_COUNT = 5

    fun save(
        outState: Bundle,
        selections: Map<WaterTestParameterId, WaterMeasurementSelectionUi>
    ) {
        outState.putStringArrayList(
            STATE_KEY,
            ArrayList(
                selections.map { (parameterId, selection) ->
                    listOf(
                        parameterId.name,
                        selection.method.name,
                        selection.testKitId,
                        selection.basisId,
                        selection.unitId
                    ).joinToString(DELIMITER)
                }
            )
        )
    }

    fun restore(
        savedInstanceState: Bundle?,
        destination: MutableMap<WaterTestParameterId, WaterMeasurementSelectionUi>
    ) {
        savedInstanceState
            ?.getStringArrayList(STATE_KEY)
            .orEmpty()
            .forEach { encoded ->
                val parts = encoded.split(DELIMITER, limit = PART_COUNT)
                if (parts.size == PART_COUNT) {
                    val parameterId = runCatching {
                        WaterTestParameterId.valueOf(parts[0])
                    }.getOrNull()
                    val method = runCatching {
                        WaterMeasurementMethodUi.valueOf(parts[1])
                    }.getOrNull()
                    if (parameterId != null && method != null) {
                        destination[parameterId] = WaterMeasurementSelectionUi(
                            method = method,
                            testKitId = parts[2],
                            basisId = parts[3],
                            unitId = parts[4]
                        )
                    }
                }
            }
    }
}

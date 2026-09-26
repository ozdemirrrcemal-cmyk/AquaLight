package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementCatalog
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSelection
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter

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
        WaterMeasurementCatalog
            .defaultSelection(parameterId.toDomainParameter())
            .toUiSelection()

    fun normalizeSelection(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ): WaterMeasurementSelectionUi {
        val parameter = parameterId.toDomainParameter()
        val candidate = selection.toDomainSelectionOrNull(parameter)
            ?: WaterMeasurementCatalog.defaultSelection(parameter)
        return WaterMeasurementCatalog
            .normalizeSelection(parameter, candidate)
            .toUiSelection()
    }

    fun isSelectionValid(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ): Boolean {
        val parameter = parameterId.toDomainParameter()
        val candidate = selection.toDomainSelectionOrNull(parameter) ?: return false
        return WaterMeasurementCatalog.isSelectionValid(parameter, candidate)
    }

    fun domainSelectionOrNull(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ): WaterMeasurementSelection? {
        val parameter = parameterId.toDomainParameter()
        val candidate = selection.toDomainSelectionOrNull(parameter) ?: return null
        return candidate.takeIf {
            WaterMeasurementCatalog.isSelectionValid(parameter, candidate)
        }
    }

    fun testKitOptions(parameterId: WaterTestParameterId): List<WaterMeasurementOptionUi> {
        val parameter = parameterId.toDomainParameter()
        return buildList {
            add(WaterMeasurementOptionUi(OPTION_NONE, R.string.water_measurement_not_selected))
            WaterMeasurementCatalog.builtInTestKitsFor(parameter).forEach { definition ->
                add(
                    WaterMeasurementOptionUi(
                        id = definition.id,
                        labelRes = testKitLabelRes(definition.id)
                    )
                )
            }
            add(WaterMeasurementOptionUi(KIT_OTHER, R.string.water_measurement_kit_other))
        }
    }

    fun basisOptions(parameterId: WaterTestParameterId): List<WaterMeasurementOptionUi> =
        WaterMeasurementCatalog
            .basisOptions(parameterId.toDomainParameter())
            .map(::basisOption)

    fun unitOptions(parameterId: WaterTestParameterId): List<WaterMeasurementOptionUi> =
        WaterMeasurementCatalog
            .unitOptions(parameterId.toDomainParameter())
            .map(::unitOption)

    fun selectableBasisOptions(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ): List<WaterMeasurementOptionUi> {
        val parameter = parameterId.toDomainParameter()
        val candidate = selection.toDomainSelectionOrNull(parameter)
            ?: WaterMeasurementCatalog.defaultSelection(parameter)
        return WaterMeasurementCatalog
            .selectableBasisOptions(parameter, candidate)
            .map(::basisOption)
    }

    fun selectableUnitOptions(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ): List<WaterMeasurementOptionUi> {
        val parameter = parameterId.toDomainParameter()
        val candidate = selection.toDomainSelectionOrNull(parameter)
            ?: WaterMeasurementCatalog.defaultSelection(parameter)
        return WaterMeasurementCatalog
            .selectableUnitOptions(parameter, candidate)
            .map(::unitOption)
    }

    fun canonicalBasis(parameterId: WaterTestParameterId): WaterMeasurementOptionUi =
        basisOption(
            WaterMeasurementCatalog.canonicalBasis(parameterId.toDomainParameter())
        )

    fun canonicalUnit(parameterId: WaterTestParameterId): WaterMeasurementOptionUi? =
        WaterMeasurementCatalog
            .canonicalUnit(parameterId.toDomainParameter())
            .takeUnless { unit -> unit == WaterMeasurementUnit.NONE }
            ?.let(::unitOption)

    fun optionLabelRes(
        options: List<WaterMeasurementOptionUi>,
        selectedId: String
    ): Int = options.firstOrNull { option -> option.id == selectedId }
        ?.labelRes
        ?: options.firstOrNull()?.labelRes
        ?: R.string.water_measurement_not_selected

    private fun WaterMeasurementSelectionUi.toDomainSelectionOrNull(
        parameter: WaterParameter
    ): WaterMeasurementSelection? {
        val basis = WaterMeasurementBasis.fromId(basisId) ?: return null
        val unit = WaterMeasurementUnit.fromId(unitId) ?: return null
        val method = runCatching { WaterMeasurementMethod.valueOf(method.name) }.getOrNull()
            ?: return null
        val kitId = testKitId
            .takeUnless { id -> id == OPTION_NONE || id.isBlank() }
        return WaterMeasurementSelection(
            method = method,
            testKitId = kitId,
            basis = basis,
            unit = unit
        ).takeIf { candidate ->
            basis in WaterMeasurementCatalog.basisOptions(parameter) &&
                (
                    unit == WaterMeasurementCatalog.canonicalUnit(parameter) ||
                        unit in WaterMeasurementCatalog.unitOptions(parameter)
                    )
        }
    }

    private fun WaterMeasurementSelection.toUiSelection(): WaterMeasurementSelectionUi =
        WaterMeasurementSelectionUi(
            method = WaterMeasurementMethodUi.valueOf(method.name),
            testKitId = testKitId ?: OPTION_NONE,
            basisId = basis.id,
            unitId = unit.id
        )

    private fun basisOption(basis: WaterMeasurementBasis): WaterMeasurementOptionUi =
        WaterMeasurementOptionUi(
            id = basis.id,
            labelRes = when (basis) {
                WaterMeasurementBasis.PH -> R.string.tank_health_test_ph
                WaterMeasurementBasis.NO3 -> R.string.water_measurement_basis_no3
                WaterMeasurementBasis.NO3_N -> R.string.water_measurement_basis_no3_n
                WaterMeasurementBasis.NO2 -> R.string.tank_health_test_symbol_nitrite
                WaterMeasurementBasis.NH3_NH4 -> R.string.water_measurement_basis_nh3_nh4
                WaterMeasurementBasis.TAN -> R.string.water_measurement_basis_tan
                WaterMeasurementBasis.GH -> R.string.tank_health_test_symbol_gh
                WaterMeasurementBasis.KH -> R.string.tank_health_test_symbol_kh
                WaterMeasurementBasis.PO4 -> R.string.water_measurement_basis_po4
                WaterMeasurementBasis.P -> R.string.water_measurement_basis_p
                WaterMeasurementBasis.TDS -> R.string.tank_health_test_symbol_tds
                WaterMeasurementBasis.EC -> R.string.tank_health_test_symbol_ec
                WaterMeasurementBasis.CO2 -> R.string.tank_health_test_symbol_co2
                WaterMeasurementBasis.FE -> R.string.tank_health_test_symbol_iron
                WaterMeasurementBasis.K -> R.string.tank_health_test_symbol_potassium
                WaterMeasurementBasis.SALINITY -> R.string.tank_health_test_salinity
                WaterMeasurementBasis.SG -> R.string.tank_health_test_symbol_specific_gravity
                WaterMeasurementBasis.CA -> R.string.tank_health_test_symbol_calcium
                WaterMeasurementBasis.MG -> R.string.tank_health_test_symbol_magnesium
                WaterMeasurementBasis.CU -> R.string.tank_health_test_symbol_copper
                WaterMeasurementBasis.O2 -> R.string.tank_health_test_symbol_oxygen
            }
        )

    private fun unitOption(unit: WaterMeasurementUnit): WaterMeasurementOptionUi =
        WaterMeasurementOptionUi(
            id = unit.id,
            labelRes = when (unit) {
                WaterMeasurementUnit.NONE -> R.string.water_measurement_not_selected
                WaterMeasurementUnit.MG_L -> R.string.tank_health_analysis_unit_mg_l
                WaterMeasurementUnit.DGH -> R.string.tank_health_analysis_unit_dgh
                WaterMeasurementUnit.DKH -> R.string.tank_health_analysis_unit_dkh
                WaterMeasurementUnit.PPM -> R.string.tank_health_analysis_unit_ppm
                WaterMeasurementUnit.US_CM -> R.string.tank_health_analysis_unit_us_cm
                WaterMeasurementUnit.PPT -> R.string.tank_health_analysis_unit_ppt
                WaterMeasurementUnit.MEQ_L -> R.string.tank_health_analysis_unit_meq_l
                WaterMeasurementUnit.PPM_CACO3 -> R.string.tank_health_analysis_unit_ppm_caco3
            }
        )

    @StringRes
    private fun testKitLabelRes(id: String): Int =
        when (id) {
            KIT_SALIFERT_NITRATE -> R.string.water_measurement_kit_salifert_nitrate
            else -> R.string.water_measurement_kit_other
        }

    const val OPTION_NONE = "none"
    const val KIT_SALIFERT_NITRATE = WaterMeasurementCatalog.SALIFERT_NITRATE_TEST_KIT_ID
    const val KIT_OTHER = WaterMeasurementCatalog.OTHER_TEST_KIT_ID

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

internal fun WaterTestParameterId.toDomainParameter(): WaterParameter =
    WaterParameter.valueOf(name)

internal fun WaterParameter.toUiParameterId(): WaterTestParameterId =
    WaterTestParameterId.valueOf(name)

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
                        destination[parameterId] = WaterMeasurementUiCatalog.normalizeSelection(
                            parameterId = parameterId,
                            selection = WaterMeasurementSelectionUi(
                                method = method,
                                testKitId = parts[2],
                                basisId = parts[3],
                                unitId = parts[4]
                            )
                        )
                    }
                }
            }
    }
}

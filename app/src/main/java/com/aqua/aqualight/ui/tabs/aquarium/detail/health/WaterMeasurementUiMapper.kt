package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementCatalog
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSelection
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter

internal object WaterMeasurementUiMapper {

    private val basisLabelRes = mapOf(
        WaterMeasurementBasis.PH to R.string.tank_health_test_ph,
        WaterMeasurementBasis.NO3 to R.string.water_measurement_basis_no3,
        WaterMeasurementBasis.NO3_N to R.string.water_measurement_basis_no3_n,
        WaterMeasurementBasis.NO2 to R.string.tank_health_test_symbol_nitrite,
        WaterMeasurementBasis.NH3_NH4 to R.string.water_measurement_basis_nh3_nh4,
        WaterMeasurementBasis.TAN to R.string.water_measurement_basis_tan,
        WaterMeasurementBasis.TAN_N to R.string.water_measurement_basis_tan_n,
        WaterMeasurementBasis.FREE_NH3 to R.string.water_measurement_basis_free_nh3,
        WaterMeasurementBasis.GH to R.string.tank_health_test_symbol_gh,
        WaterMeasurementBasis.KH to R.string.tank_health_test_symbol_kh,
        WaterMeasurementBasis.PO4 to R.string.water_measurement_basis_po4,
        WaterMeasurementBasis.P to R.string.water_measurement_basis_p,
        WaterMeasurementBasis.TDS to R.string.tank_health_test_symbol_tds,
        WaterMeasurementBasis.EC to R.string.tank_health_test_symbol_ec,
        WaterMeasurementBasis.CO2 to R.string.tank_health_test_symbol_co2,
        WaterMeasurementBasis.FE to R.string.tank_health_test_symbol_iron,
        WaterMeasurementBasis.K to R.string.tank_health_test_symbol_potassium,
        WaterMeasurementBasis.SALINITY to R.string.tank_health_test_salinity,
        WaterMeasurementBasis.SG to R.string.tank_health_test_symbol_specific_gravity,
        WaterMeasurementBasis.CA to R.string.tank_health_test_symbol_calcium,
        WaterMeasurementBasis.MG to R.string.tank_health_test_symbol_magnesium,
        WaterMeasurementBasis.CU to R.string.tank_health_test_symbol_copper,
        WaterMeasurementBasis.O2 to R.string.tank_health_test_symbol_oxygen
    )

    private val unitLabelRes = mapOf(
        WaterMeasurementUnit.NONE to R.string.water_measurement_not_selected,
        WaterMeasurementUnit.MG_L to R.string.tank_health_analysis_unit_mg_l,
        WaterMeasurementUnit.DGH to R.string.tank_health_analysis_unit_dgh,
        WaterMeasurementUnit.DKH to R.string.tank_health_analysis_unit_dkh,
        WaterMeasurementUnit.PPM to R.string.tank_health_analysis_unit_ppm,
        WaterMeasurementUnit.US_CM to R.string.tank_health_analysis_unit_us_cm,
        WaterMeasurementUnit.PPT to R.string.tank_health_analysis_unit_ppt,
        WaterMeasurementUnit.MEQ_L to R.string.tank_health_analysis_unit_meq_l,
        WaterMeasurementUnit.PPM_CACO3 to R.string.tank_health_analysis_unit_ppm_caco3
    )

    fun basisOption(basis: WaterMeasurementBasis): WaterMeasurementOptionUi =
        WaterMeasurementOptionUi(
            id = basis.id,
            labelRes = requireNotNull(basisLabelRes[basis])
        )

    fun unitOption(unit: WaterMeasurementUnit): WaterMeasurementOptionUi =
        WaterMeasurementOptionUi(
            id = unit.id,
            labelRes = requireNotNull(unitLabelRes[unit])
        )

    @StringRes
    fun testKitLabelRes(id: String): Int =
        if (id == WaterMeasurementCatalog.SALIFERT_NITRATE_TEST_KIT_ID) {
            R.string.water_measurement_kit_salifert_nitrate
        } else {
            R.string.water_measurement_kit_other
        }

    fun toUiSelection(selection: WaterMeasurementSelection): WaterMeasurementSelectionUi =
        WaterMeasurementSelectionUi(
            method = WaterMeasurementMethodUi.valueOf(selection.method.name),
            testKitId = selection.testKitId ?: WaterMeasurementUiCatalog.OPTION_NONE,
            basisId = selection.basis.id,
            unitId = selection.unit.id
        )

    fun toDomainSelectionOrNull(
        selection: WaterMeasurementSelectionUi
    ): WaterMeasurementSelection? {
        val method = runCatching {
            WaterMeasurementMethod.valueOf(selection.method.name)
        }.getOrNull()
        val basis = WaterMeasurementBasis.fromId(selection.basisId)
        val unit = WaterMeasurementUnit.fromId(selection.unitId)
        return if (method == null || basis == null || unit == null) {
            null
        } else {
            WaterMeasurementSelection(
                method = method,
                testKitId = selection.testKitId.takeUnless { id ->
                    id == WaterMeasurementUiCatalog.OPTION_NONE || id.isBlank()
                },
                basis = basis,
                unit = unit
            )
        }
    }
}

internal fun WaterTestParameterId.toDomainParameter(): WaterParameter =
    WaterParameter.valueOf(name)

internal fun WaterParameter.toUiParameterId(): WaterTestParameterId =
    WaterTestParameterId.valueOf(name)

internal fun selectedOptionLabelRes(
    options: List<WaterMeasurementOptionUi>,
    selectedId: String
): Int = options.firstOrNull { option -> option.id == selectedId }
    ?.labelRes
    ?: options.firstOrNull()?.labelRes
    ?: R.string.water_measurement_not_selected

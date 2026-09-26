package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.i18n.LocaleFormatter

internal object WaterAnalysisPresentation {

    fun measurementValueText(
        context: Context,
        measurement: WaterMeasurementSnapshot
    ): String {
        val canonical = measurement.canonicalValue
        val value = canonical ?: measurement.value
        val unit = if (canonical != null) measurement.canonicalUnit else measurement.unit
        val valueText = LocaleFormatter.formatDecimal(
            context = context,
            value = value,
            maximumFractionDigits = MAX_DISPLAY_FRACTION_DIGITS
        )
        if (unit == WaterMeasurementUnit.NONE) return valueText
        return "$valueText ${context.getString(unitLabelRes(unit))}"
    }

    fun temperatureValueText(context: Context, temperatureCelsius: Double?): String =
        temperatureCelsius?.let { value ->
            val valueText = LocaleFormatter.formatDecimal(
                context = context,
                value = value,
                maximumFractionDigits = MAX_DISPLAY_FRACTION_DIGITS
            )
            "$valueText ${context.getString(R.string.tank_health_analysis_temperature_unit)}"
        } ?: context.getString(R.string.tank_health_value_not_measured)

    @StringRes
    fun measurementSymbolRes(measurement: WaterMeasurementSnapshot): Int? {
        val basis = if (measurement.canonicalValue != null) {
            measurement.canonicalBasis
        } else {
            measurement.basis
        }
        return when (basis) {
            WaterMeasurementBasis.PH,
            WaterMeasurementBasis.SALINITY -> null
            else -> basisLabelRes(basis)
        }
    }

    fun measurementMetaText(
        context: Context,
        measurement: WaterMeasurementSnapshot
    ): String {
        val parts = buildList {
            add(context.getString(methodLabelRes(measurement.method)))
            measurement.testKitId?.let { id -> add(context.getString(testKitLabelRes(id))) }
            add(context.getString(basisLabelRes(measurement.basis)))
            if (measurement.unit != WaterMeasurementUnit.NONE) {
                add(context.getString(unitLabelRes(measurement.unit)))
            }
        }
        return parts.joinToString(META_SEPARATOR)
    }

    @StringRes
    fun parameterNameRes(parameter: WaterParameter): Int =
        when (parameter) {
            WaterParameter.PH -> R.string.tank_health_test_ph
            WaterParameter.NITRATE -> R.string.tank_health_test_nitrate
            WaterParameter.NITRITE -> R.string.tank_health_test_nitrite
            WaterParameter.AMMONIA_AMMONIUM -> R.string.tank_health_test_ammonia_ammonium
            WaterParameter.GH -> R.string.tank_health_test_general_hardness
            WaterParameter.KH -> R.string.tank_health_test_carbonate_hardness
            WaterParameter.PHOSPHATE -> R.string.tank_health_test_phosphate
            WaterParameter.TDS -> R.string.tank_health_test_tds
            WaterParameter.EC -> R.string.tank_health_test_conductivity
            WaterParameter.CO2 -> R.string.tank_health_test_carbon_dioxide
            WaterParameter.IRON -> R.string.tank_health_test_iron
            WaterParameter.POTASSIUM -> R.string.tank_health_test_potassium
            WaterParameter.SALINITY -> R.string.tank_health_test_salinity
            WaterParameter.SPECIFIC_GRAVITY -> R.string.tank_health_test_specific_gravity
            WaterParameter.CALCIUM -> R.string.tank_health_test_calcium
            WaterParameter.MAGNESIUM -> R.string.tank_health_test_magnesium
            WaterParameter.COPPER -> R.string.tank_health_test_copper
            WaterParameter.DISSOLVED_OXYGEN -> R.string.tank_health_test_dissolved_oxygen
        }

    @StringRes
    fun basisLabelRes(basis: WaterMeasurementBasis): Int =
        when (basis) {
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

    @StringRes
    fun unitLabelRes(unit: WaterMeasurementUnit): Int =
        when (unit) {
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

    @StringRes
    fun methodLabelRes(method: WaterMeasurementMethod): Int =
        when (method) {
            WaterMeasurementMethod.MANUAL -> R.string.water_measurement_method_manual
            WaterMeasurementMethod.TEST_KIT -> R.string.water_measurement_method_test_kit
            WaterMeasurementMethod.DIGITAL -> R.string.water_measurement_method_digital
            WaterMeasurementMethod.SENSOR -> R.string.water_measurement_method_sensor
        }

    @StringRes
    fun testKitLabelRes(id: String): Int =
        when (id) {
            "salifert_nitrate" -> R.string.water_measurement_kit_salifert_nitrate
            else -> R.string.water_measurement_kit_other
        }

    private const val MAX_DISPLAY_FRACTION_DIGITS = 4
    private const val META_SEPARATOR = " • "
}

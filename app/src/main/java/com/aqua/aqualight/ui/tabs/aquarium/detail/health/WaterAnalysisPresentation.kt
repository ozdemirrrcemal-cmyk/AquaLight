package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementCatalog
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.i18n.LocaleFormatter

internal object WaterAnalysisPresentation {

    private val parameterNames = mapOf(
        WaterParameter.PH to R.string.tank_health_test_ph,
        WaterParameter.NITRATE to R.string.tank_health_test_nitrate,
        WaterParameter.NITRITE to R.string.tank_health_test_nitrite,
        WaterParameter.AMMONIA_AMMONIUM to R.string.tank_health_test_ammonia_ammonium,
        WaterParameter.GH to R.string.tank_health_test_general_hardness,
        WaterParameter.KH to R.string.tank_health_test_carbonate_hardness,
        WaterParameter.PHOSPHATE to R.string.tank_health_test_phosphate,
        WaterParameter.TDS to R.string.tank_health_test_tds,
        WaterParameter.EC to R.string.tank_health_test_conductivity,
        WaterParameter.CO2 to R.string.tank_health_test_carbon_dioxide,
        WaterParameter.IRON to R.string.tank_health_test_iron,
        WaterParameter.POTASSIUM to R.string.tank_health_test_potassium,
        WaterParameter.SALINITY to R.string.tank_health_test_salinity,
        WaterParameter.SPECIFIC_GRAVITY to R.string.tank_health_test_specific_gravity,
        WaterParameter.CALCIUM to R.string.tank_health_test_calcium,
        WaterParameter.MAGNESIUM to R.string.tank_health_test_magnesium,
        WaterParameter.COPPER to R.string.tank_health_test_copper,
        WaterParameter.DISSOLVED_OXYGEN to R.string.tank_health_test_dissolved_oxygen
    )

    private val basisLabels = mapOf(
        WaterMeasurementBasis.PH to R.string.tank_health_test_ph,
        WaterMeasurementBasis.NO3 to R.string.water_measurement_basis_no3,
        WaterMeasurementBasis.NO3_N to R.string.water_measurement_basis_no3_n,
        WaterMeasurementBasis.NO2 to R.string.tank_health_test_symbol_nitrite,
        WaterMeasurementBasis.NH3_NH4 to R.string.water_measurement_basis_nh3_nh4,
        WaterMeasurementBasis.TAN to R.string.water_measurement_basis_tan,
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

    private val unitLabels = mapOf(
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

    private val methodLabels = mapOf(
        WaterMeasurementMethod.MANUAL to R.string.water_measurement_method_manual,
        WaterMeasurementMethod.TEST_KIT to R.string.water_measurement_method_test_kit,
        WaterMeasurementMethod.DIGITAL to R.string.water_measurement_method_digital,
        WaterMeasurementMethod.SENSOR to R.string.water_measurement_method_sensor
    )

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
        return if (unit == WaterMeasurementUnit.NONE) {
            valueText
        } else {
            "$valueText ${context.getString(unitLabelRes(unit))}"
        }
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
        return basisLabelRes(basis).takeUnless {
            basis == WaterMeasurementBasis.PH ||
                basis == WaterMeasurementBasis.SALINITY
        }
    }

    fun measurementMetaText(
        context: Context,
        measurement: WaterMeasurementSnapshot
    ): String =
        buildList {
            add(context.getString(methodLabelRes(measurement.method)))
            measurement.testKitId?.let { id ->
                add(context.getString(testKitLabelRes(id)))
            }
            add(context.getString(basisLabelRes(measurement.basis)))
            if (measurement.unit != WaterMeasurementUnit.NONE) {
                add(context.getString(unitLabelRes(measurement.unit)))
            }
        }.joinToString(META_SEPARATOR)

    @StringRes
    fun parameterNameRes(parameter: WaterParameter): Int =
        requireNotNull(parameterNames[parameter])

    @StringRes
    fun basisLabelRes(basis: WaterMeasurementBasis): Int =
        requireNotNull(basisLabels[basis])

    @StringRes
    fun unitLabelRes(unit: WaterMeasurementUnit): Int =
        requireNotNull(unitLabels[unit])

    @StringRes
    fun methodLabelRes(method: WaterMeasurementMethod): Int =
        requireNotNull(methodLabels[method])

    @StringRes
    fun testKitLabelRes(id: String): Int =
        if (id == WaterMeasurementCatalog.SALIFERT_NITRATE_TEST_KIT_ID) {
            R.string.water_measurement_kit_salifert_nitrate
        } else {
            R.string.water_measurement_kit_other
        }

    private const val MAX_DISPLAY_FRACTION_DIGITS = 4
    private const val META_SEPARATOR = " • "
}

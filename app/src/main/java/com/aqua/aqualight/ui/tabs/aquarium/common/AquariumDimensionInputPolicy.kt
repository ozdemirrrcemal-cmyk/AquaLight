package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import com.aqua.aqualight.i18n.LocaleFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Keeps displayed tank dimensions, locale parsing and canonical centimeter storage consistent. */
object AquariumDimensionInputPolicy {
    private const val CM_PER_INCH = 2.54
    private const val UNIT_IN = "in"

    fun format(context: Context, centimeters: Int, unit: String): String {
        return format(centimeters.toDouble(), unit, LocaleFormatter.appLocale(context))
    }

    fun parseCentimeters(context: Context, value: CharSequence, unit: String): Int? {
        return parseCentimeters(value.toString(), unit, LocaleFormatter.appLocale(context))
    }

    fun convert(
        context: Context,
        value: CharSequence,
        fromUnit: String,
        toUnit: String
    ): String? {
        return convert(
            value = value.toString(),
            fromUnit = fromUnit,
            toUnit = toUnit,
            locale = LocaleFormatter.appLocale(context)
        )
    }

    internal fun format(centimeters: Double, unit: String, locale: Locale): String {
        return LocaleFormatter.formatDecimal(
            value = fromCentimeters(centimeters, unit),
            locale = locale
        )
    }

    internal fun parseCentimeters(value: String, unit: String, locale: Locale): Int? {
        val displayedValue = LocaleFormatter.parseDecimal(value, locale) ?: return null
        return canonicalCentimeters(displayedValue, unit)
    }

    internal fun convert(
        value: String,
        fromUnit: String,
        toUnit: String,
        locale: Locale
    ): String? {
        val displayedValue = LocaleFormatter.parseDecimal(value, locale) ?: return null
        val centimeters = canonicalCentimeters(displayedValue, fromUnit) ?: return null
        val convertedValue = fromCentimeters(centimeters.toDouble(), toUnit)

        return LocaleFormatter.formatDecimal(
            value = convertedValue,
            locale = locale
        )
    }

    private fun canonicalCentimeters(value: Double, unit: String): Int? {
        if (!value.isFinite()) return null
        val centimeters = toCentimeters(value, unit)
        if (centimeters <= 0.0 ||
            centimeters > AquariumMeasurementPolicy.MAX_DIMENSION_CM.toDouble()
        ) {
            return null
        }
        return centimeters.roundToInt()
            .takeIf(AquariumMeasurementPolicy::isValidDimensionCm)
    }

    private fun toCentimeters(value: Double, unit: String): Double {
        return if (isInches(unit)) value * CM_PER_INCH else value
    }

    private fun fromCentimeters(value: Double, unit: String): Double {
        return if (isInches(unit)) value / CM_PER_INCH else value
    }

    private fun isInches(unit: String): Boolean {
        return unit.equals(UNIT_IN, ignoreCase = true)
    }
}

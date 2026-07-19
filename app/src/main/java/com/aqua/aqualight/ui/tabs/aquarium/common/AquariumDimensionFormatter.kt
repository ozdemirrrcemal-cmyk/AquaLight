package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.localization.LocaleFormatters
import java.util.Locale
import kotlin.math.roundToInt

object AquariumDimensionFormatter {
    private const val GALLON_PER_LITER = 0.264172

    fun sizeTitle(
        context: Context,
        sizeUnit: String
    ): String {
        return if (sizeUnit.equals("in", ignoreCase = true)) {
            context.getString(R.string.aquarium_text_size_in)
        } else {
            context.getString(R.string.aquarium_text_size_cm)
        }
    }

    fun sizeText(
        context: Context,
        widthCm: Int,
        lengthCm: Int,
        heightCm: Int,
        sizeUnit: String,
        @StringRes separatorRes: Int = R.string.aquarium_dimension_separator_spaced_multiply
    ): String {
        val locale = context.currentLocale()
        val separator = context.getString(separatorRes)
        return if (sizeUnit.equals("in", ignoreCase = true)) {
            context.getString(
                R.string.aquarium_dimension_format,
                LocaleFormatters.formatNumber(widthCm / 2.54, locale),
                separator,
                LocaleFormatters.formatNumber(lengthCm / 2.54, locale),
                LocaleFormatters.formatNumber(heightCm / 2.54, locale)
            )
        } else {
            context.getString(
                R.string.aquarium_dimension_format,
                LocaleFormatters.formatInteger(widthCm.toLong(), locale),
                separator,
                LocaleFormatters.formatInteger(lengthCm.toLong(), locale),
                LocaleFormatters.formatInteger(heightCm.toLong(), locale)
            )
        }
    }

    fun volumeText(
        context: Context,
        widthCm: Int,
        lengthCm: Int,
        heightCm: Int,
        volumeUnit: String,
        rounded: Boolean = false
    ): String {
        val locale = context.currentLocale()
        val liters = (widthCm * lengthCm * heightCm) / 1000.0

        return if (volumeUnit.equals("gal", ignoreCase = true)) {
            val gallons = liters * GALLON_PER_LITER
            context.getString(
                R.string.aquarium_volume_gallon_format,
                if (rounded) {
                    LocaleFormatters.formatInteger(gallons.roundToInt().toLong(), locale)
                } else {
                    LocaleFormatters.formatNumber(gallons, locale)
                }
            )
        } else {
            context.getString(
                R.string.aquarium_volume_liter_format,
                if (rounded) {
                    LocaleFormatters.formatInteger(liters.roundToInt().toLong(), locale)
                } else {
                    LocaleFormatters.formatNumber(liters, locale)
                }
            )
        }
    }

    private fun Context.currentLocale(): Locale {
        val locales = resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }
}

package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumVolumeCalculator
import com.aqua.aqualight.i18n.LocaleFormatter
import kotlin.math.roundToInt

object AquariumDimensionFormatter {
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
        val separator = context.getString(separatorRes)
        return if (sizeUnit.equals("in", ignoreCase = true)) {
            val widthIn = widthCm / 2.54
            val lengthIn = lengthCm / 2.54
            val heightIn = heightCm / 2.54

            context.getString(
                R.string.aquarium_dimension_format,
                LocaleFormatter.formatDecimal(context, widthIn),
                separator,
                LocaleFormatter.formatDecimal(context, lengthIn),
                LocaleFormatter.formatDecimal(context, heightIn)
            )
        } else {
            context.getString(
                R.string.aquarium_dimension_format,
                LocaleFormatter.formatInteger(context, widthCm),
                separator,
                LocaleFormatter.formatInteger(context, lengthCm),
                LocaleFormatter.formatInteger(context, heightCm)
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
        val liters = AquariumVolumeCalculator.grossLiters(
            widthCm = widthCm,
            lengthCm = lengthCm,
            heightCm = heightCm
        )

        return if (volumeUnit.equals("gal", ignoreCase = true)) {
            val gallons = AquariumVolumeCalculator.litersToGallons(liters)
            context.getString(
                R.string.aquarium_volume_gallon_format,
                if (rounded) {
                    LocaleFormatter.formatInteger(context, gallons.roundToInt())
                } else {
                    LocaleFormatter.formatDecimal(context, gallons)
                }
            )
        } else {
            context.getString(
                R.string.aquarium_volume_liter_format,
                if (rounded) {
                    LocaleFormatter.formatInteger(context, liters.roundToInt())
                } else {
                    LocaleFormatter.formatDecimal(context, liters)
                }
            )
        }
    }
}

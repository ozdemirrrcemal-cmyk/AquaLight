package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.localization.LocaleFormatters
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
        val separator = context.getString(separatorRes)
        return if (sizeUnit.equals("in", ignoreCase = true)) {
            val widthIn = widthCm / 2.54
            val lengthIn = lengthCm / 2.54
            val heightIn = heightCm / 2.54

            context.getString(
                R.string.aquarium_dimension_format,
                LocaleFormatters.formatNumber(context, widthIn),
                separator,
                LocaleFormatters.formatNumber(context, lengthIn),
                LocaleFormatters.formatNumber(context, heightIn)
            )
        } else {
            context.getString(
                R.string.aquarium_dimension_format,
                LocaleFormatters.formatInteger(context, widthCm.toLong()),
                separator,
                LocaleFormatters.formatInteger(context, lengthCm.toLong()),
                LocaleFormatters.formatInteger(context, heightCm.toLong())
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
        val liters = (widthCm * lengthCm * heightCm) / 1000.0

        return if (volumeUnit.equals("gal", ignoreCase = true)) {
            val gallons = liters * GALLON_PER_LITER
            context.getString(
                R.string.aquarium_volume_gallon_format,
                if (rounded) {
                    LocaleFormatters.formatInteger(context, gallons.roundToInt().toLong())
                } else {
                    LocaleFormatters.formatNumber(context, gallons)
                }
            )
        } else {
            context.getString(
                R.string.aquarium_volume_liter_format,
                if (rounded) {
                    LocaleFormatters.formatInteger(context, liters.roundToInt().toLong())
                } else {
                    LocaleFormatters.formatNumber(context, liters)
                }
            )
        }
    }
}

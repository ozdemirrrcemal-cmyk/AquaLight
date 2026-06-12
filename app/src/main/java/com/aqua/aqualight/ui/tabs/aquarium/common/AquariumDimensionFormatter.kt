package com.aqua.aqualight.ui.tabs.aquarium.common

import android.content.Context
import com.aqua.aqualight.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

object AquariumDimensionFormatter {
    private const val GALLON_PER_LITER = 0.264172

    private val preciseFormatter = DecimalFormat(
        "#0.##",
        DecimalFormatSymbols(Locale.US)
    )

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
        widthCm: Int,
        lengthCm: Int,
        heightCm: Int,
        sizeUnit: String,
        separator: String = " × "
    ): String {
        return if (sizeUnit.equals("in", ignoreCase = true)) {
            val widthIn = widthCm / 2.54
            val lengthIn = lengthCm / 2.54
            val heightIn = heightCm / 2.54

            "${preciseFormatter.format(widthIn)} W${separator}${preciseFormatter.format(lengthIn)} L${separator}${preciseFormatter.format(heightIn)} H"
        } else {
            "$widthCm W${separator}$lengthCm L${separator}$heightCm H"
        }
    }

    fun volumeText(
        widthCm: Int,
        lengthCm: Int,
        heightCm: Int,
        volumeUnit: String,
        rounded: Boolean = false
    ): String {
        val liters = (widthCm * lengthCm * heightCm) / 1000.0

        return if (volumeUnit.equals("gal", ignoreCase = true)) {
            val gallons = liters * GALLON_PER_LITER
            if (rounded) {
                "${gallons.roundToInt()} gal"
            } else {
                "${preciseFormatter.format(gallons)} gal"
            }
        } else {
            if (rounded) {
                "${liters.roundToInt()} L"
            } else {
                "${preciseFormatter.format(liters)} L"
            }
        }
    }
}

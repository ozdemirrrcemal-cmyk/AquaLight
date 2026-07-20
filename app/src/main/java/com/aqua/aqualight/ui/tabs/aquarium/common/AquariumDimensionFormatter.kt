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
        val values = dimensionValues(
            context = context,
            widthCm = widthCm,
            lengthCm = lengthCm,
            heightCm = heightCm,
            sizeUnit = sizeUnit
        )
        return context.getString(
            R.string.aquarium_dimension_format,
            values.width,
            context.getString(separatorRes),
            values.length,
            values.height
        )
    }

    fun labeledSizeText(
        context: Context,
        widthCm: Int,
        lengthCm: Int,
        heightCm: Int,
        sizeUnit: String,
        @StringRes formatRes: Int
    ): String {
        val values = dimensionValues(
            context = context,
            widthCm = widthCm,
            lengthCm = lengthCm,
            heightCm = heightCm,
            sizeUnit = sizeUnit
        )
        return context.getString(
            formatRes,
            values.width,
            values.length,
            values.height,
            values.unit
        )
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

    private fun dimensionValues(
        context: Context,
        widthCm: Int,
        lengthCm: Int,
        heightCm: Int,
        sizeUnit: String
    ): DimensionValues {
        val canonicalUnit = if (sizeUnit.equals("in", ignoreCase = true)) "in" else "cm"
        return DimensionValues(
            width = AquariumDimensionInputPolicy.format(
                context = context,
                centimeters = widthCm,
                unit = canonicalUnit
            ),
            length = AquariumDimensionInputPolicy.format(
                context = context,
                centimeters = lengthCm,
                unit = canonicalUnit
            ),
            height = AquariumDimensionInputPolicy.format(
                context = context,
                centimeters = heightCm,
                unit = canonicalUnit
            ),
            unit = canonicalUnit
        )
    }

    private data class DimensionValues(
        val width: String,
        val length: String,
        val height: String,
        val unit: String
    )
}

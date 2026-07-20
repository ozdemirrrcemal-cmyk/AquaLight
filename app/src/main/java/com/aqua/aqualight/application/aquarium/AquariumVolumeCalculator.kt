package com.aqua.aqualight.application.aquarium

/** Single overflow-safe source for tank volume calculations. */
object AquariumVolumeCalculator {
    private const val GALLONS_PER_LITER = 0.264172

    fun grossLiters(widthCm: Int, lengthCm: Int, heightCm: Int): Double {
        if (widthCm <= 0 || lengthCm <= 0 || heightCm <= 0) return 0.0

        return widthCm.toDouble() *
            lengthCm.toDouble() *
            heightCm.toDouble() / 1000.0
    }

    fun litersToGallons(liters: Double): Double {
        return if (liters.isFinite() && liters > 0.0) {
            liters * GALLONS_PER_LITER
        } else {
            0.0
        }
    }
}

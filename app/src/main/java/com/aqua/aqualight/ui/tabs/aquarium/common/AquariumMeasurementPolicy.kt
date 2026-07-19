package com.aqua.aqualight.ui.tabs.aquarium.common

object AquariumMeasurementPolicy {
    const val MIN_DIMENSION_CM = 1
    const val MAX_DIMENSION_CM = 5000

    fun isValidDimensionCm(value: Int): Boolean {
        return value in MIN_DIMENSION_CM..MAX_DIMENSION_CM
    }

    fun isValidDimensionCm(value: Double): Boolean {
        return value.isFinite() &&
            value >= MIN_DIMENSION_CM.toDouble() &&
            value <= MAX_DIMENSION_CM.toDouble()
    }

    fun areValidDimensions(
        widthCm: Int,
        lengthCm: Int,
        heightCm: Int
    ): Boolean {
        return isValidDimensionCm(widthCm) &&
            isValidDimensionCm(lengthCm) &&
            isValidDimensionCm(heightCm)
    }
}

package com.aqua.aqualight.ui.tabs.aquarium.common

object AquariumMeasurementPolicy {
    private const val MIN_DIMENSION_CM = 1
    private const val MAX_DIMENSION_CM = 5000

    fun isValidDimensionCm(value: Int): Boolean {
        return value in MIN_DIMENSION_CM..MAX_DIMENSION_CM
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

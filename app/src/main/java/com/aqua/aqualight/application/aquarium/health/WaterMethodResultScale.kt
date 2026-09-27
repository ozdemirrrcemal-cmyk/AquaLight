package com.aqua.aqualight.application.aquarium.health

import java.math.BigDecimal

enum class WaterResultQualifier { EXACT, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL }

data class WaterMethodNumericRange(val minimum: BigDecimal, val maximum: BigDecimal) {
    init {
        require(minimum.signum() >= 0 && maximum > minimum) { "Invalid source measurement range." }
    }

    operator fun contains(value: BigDecimal): Boolean = value >= minimum && value <= maximum
}

sealed interface WaterMethodPrecision {
    data class DecimalPlaces(val count: Int) : WaterMethodPrecision {
        init {
            require(count >= 0) { "Source decimal places cannot be negative." }
        }
    }

    data class Increment(val value: BigDecimal) : WaterMethodPrecision {
        init {
            require(value.signum() > 0) { "Source increment must be positive." }
        }
    }

    data class ComparatorScale(val values: List<BigDecimal>) : WaterMethodPrecision {
        init {
            require(values.size > 1 && values.all { it.signum() >= 0 }) { "Invalid comparator scale." }
            require(values.zipWithNext().all { (left, right) -> left < right }) {
                "Comparator scale points must be strictly increasing."
            }
        }
    }
}

data class WaterMethodDetectionLimits(
    val detection: BigDecimal?,
    val quantification: BigDecimal?
) {
    init {
        require(detection == null || detection.signum() > 0) { "Detection limit must be positive." }
        require(quantification == null || quantification.signum() > 0) {
            "Quantification limit must be positive."
        }
        require(detection == null || quantification == null || quantification >= detection) {
            "Quantification limit cannot be below detection limit."
        }
    }
}

/** Limits and precision describe the method's reporting capability, never an aquarium target. */
data class WaterMethodResultScale(
    val range: WaterMethodNumericRange,
    val precision: WaterMethodPrecision,
    val limits: WaterMethodDetectionLimits,
    val qualifiers: Set<WaterResultQualifier>
) {
    init {
        require(qualifiers.isNotEmpty()) { "Supported result notation must be explicit." }
        require(listOfNotNull(limits.detection, limits.quantification).all { it <= range.maximum }) {
            "Detection limits cannot exceed the source range maximum."
        }
        if (precision is WaterMethodPrecision.ComparatorScale) {
            require(precision.values.all { it in range }) { "Comparator values exceed the source range." }
        }
    }
}
